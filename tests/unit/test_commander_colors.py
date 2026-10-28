"""Pregame color choices must be legal, immutable and safe to export."""

import hashlib
import json
import unittest
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from commander.catalog.database import connect, get_card_details, store_card
from commander.decks.deck_store import get_revision, save_revision
from commander.decks.decks import prepare_deck, save_deck
from commander.engine.forge_decks import serialize_deck
from commander.engine.forge_engine import run_game
from tests.unit.test_legality import card

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class CommanderColorTests(unittest.TestCase):
    """Choices affect deck identity without modifying authoritative Oracle records."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        with self.db:
            for value in (
                card(
                    "The Prismatic Piper",
                    text="If The Prismatic Piper is your commander, choose a color before the game begins. The Prismatic Piper is the chosen color.\nPartner",
                ),
                card("Leader", colors=["G"]),
                card(
                    "Faceless One",
                    "Legendary Enchantment Creature — Background",
                    text="If Faceless One is your commander, choose a color before the game begins. Faceless One is the chosen color.\nChoose a Background",
                ),
                card(
                    "Noble Heritage", "Legendary Enchantment — Background", colors=["W"]
                ),
                card("Rograkh, Son of Rohgahh", text="Partner", colors=["R"]),
                *(
                    card(name, "Basic Land — " + name, colors=[color])
                    for color, name in zip(
                        "WUBRG", ("Plains", "Island", "Swamp", "Mountain", "Forest")
                    )
                ),
                card("Wastes", "Basic Land"),
            ):
                store_card(self.db, value)
            self.db.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,11)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )

    def _deck(self, color="G", basic="Forest"):
        choice = "" if color is None else " [color=" + color + "]"
        return f"Commander\n1 The Prismatic Piper{choice}\nMainboard\n99 {basic}\n"

    def test_each_explicit_color_allows_only_the_matching_identity(self):
        for color, name in zip(
            "WUBRG", ("Plains", "Island", "Swamp", "Mountain", "Forest")
        ):
            with self.subTest(color=color):
                result = prepare_deck(self.db, self._deck(color.lower(), name))
                self.assertEqual(result["legality"]["status"], "legal", result)
                selected = next(
                    entry for entry in result["cards"] if entry["zone"] == "commander"
                )
                self.assertEqual(selected["commander_color"], color)
        invalid = prepare_deck(self.db, self._deck("G", "Island"))
        self.assertEqual(invalid["legality"]["status"], "illegal")
        self.assertTrue(any("color identity" in error for error in invalid["issues"]))

    def test_partner_and_background_identities_include_each_selected_color(self):
        for text in (
            "Commander\n1 The Prismatic Piper [color=G]\n1 Rograkh, Son of Rohgahh\nMainboard\n98 Forest\n",
            "Commander\n1 Faceless One [color=G]\n1 Noble Heritage\nMainboard\n98 Forest\n",
        ):
            with self.subTest(text=text):
                self.assertEqual(
                    prepare_deck(self.db, text)["legality"]["status"], "legal"
                )
        mainboard = "Commander\n1 Leader\nMainboard\n98 Forest\n1 The Prismatic Piper\n"
        self.assertEqual(
            prepare_deck(self.db, mainboard)["legality"]["status"], "legal"
        )

    def test_missing_color_blocks_approval_without_pretending_the_deck_is_colorless(
        self,
    ):
        result = prepare_deck(self.db, self._deck(None))
        self.assertEqual(result["legality"]["status"], "needs_review", result)
        self.assertTrue(
            any("Select a commander color" in blocker for blocker in result["warnings"])
        )
        with self.assertRaises(ValueError):
            save_deck(self.db, "Missing choice", 3, result)

    def test_invalid_noncommander_and_unauthorized_color_annotations_fail(self):
        for text in (
            self._deck("C"),
            self._deck("WG"),
            self._deck("G}|#{other=true}"),
            "Commander\n1 Leader [color=U]\nMainboard\n99 Island\n",
            "Commander\n1 Leader\nMainboard\n98 Forest\n1 The Prismatic Piper [color=G]\n",
        ):
            with self.subTest(text=text):
                try:
                    result = prepare_deck(self.db, text)
                except ValueError:
                    continue
                self.assertEqual(result["legality"]["status"], "illegal")
                with self.assertRaises(ValueError):
                    save_deck(self.db, "Invalid color", 3, result)

    def test_choice_changes_revision_hash_and_preserves_catalog_and_prior_revision(
        self,
    ):
        original = prepare_deck(self.db, self._deck("G", "Wastes"))
        deck_id = save_deck(self.db, "Color choices", 3, original)
        first = get_revision(self.db, deck_id, 1)
        save_revision(
            self.db, deck_id, prepare_deck(self.db, self._deck("U", "Wastes"))
        )
        second = get_revision(self.db, deck_id, 2)
        self.assertNotEqual(first["deck_hash"], second["deck_hash"])
        for version, expected in ((1, "G"), (2, "U")):
            chosen = next(
                entry
                for entry in get_revision(self.db, deck_id, version)["cards"]
                if entry["zone"] == "commander"
            )
            self.assertEqual(chosen["commander_color"], expected)
            self.assertEqual(chosen["oracle"]["color_identity"], [])
        self.assertEqual(
            self.db.execute("SELECT color FROM deck_commander_colors").fetchone()[0],
            "U",
        )
        self.assertEqual(
            get_card_details(self.db, "The Prismatic Piper")["color_identity"], []
        )

    def test_flat_export_and_existing_nonchoice_hash_format_remain_supported(self):
        prepared = prepare_deck(
            self.db,
            "1 The Prismatic Piper\n99 Forest",
            ["The Prismatic Piper [color=G]"],
        )
        self.assertEqual(prepared["legality"]["status"], "legal")
        plain = prepare_deck(self.db, "99 Forest", ["Leader"])
        snapshot = sorted(
            (entry["card"]["card_id"], entry["zone"], entry["quantity"])
            for entry in plain["cards"]
        )
        self.assertEqual(
            plain["legality"]["deck_hash"],
            hashlib.sha256(json.dumps(snapshot).encode()).hexdigest(),
        )

    def test_export_generates_only_the_validated_native_color_flag(self):
        deck_id = save_deck(self.db, "Export", 3, prepare_deck(self.db, self._deck()))
        revision = get_revision(self.db, deck_id, 1)
        index = {
            entry["card"]["name"]: entry["card"]["name"] for entry in revision["cards"]
        }
        exported = serialize_deck(revision, index, 1)
        self.assertIn("1 The Prismatic Piper|#{markedColors=G}", exported)
        self.assertNotIn("[color=G]", exported)
        selected = next(
            entry for entry in revision["cards"] if entry["zone"] == "commander"
        )
        selected["commander_color"] = "G}|#{other=true}"
        with self.assertRaises(ValueError):
            serialize_deck(revision, index, 1)

    def test_valid_native_color_flag_still_requires_the_matching_oracle_permission(
        self,
    ):
        deck_id = save_deck(
            self.db, "Permission", 3, prepare_deck(self.db, self._deck())
        )
        revision = get_revision(self.db, deck_id, 1)
        index = {
            entry["card"]["name"]: entry["card"]["name"] for entry in revision["cards"]
        }
        chosen = next(
            entry for entry in revision["cards"] if entry["zone"] == "commander"
        )
        chosen["oracle"]["oracle_text"] = ""
        with self.assertRaisesRegex(ValueError, "Oracle permission"):
            serialize_deck(revision, index, 1)

    def test_engine_without_required_pregame_capability_is_blocked_before_run(self):
        for entry, message in (
            ({"commander_color": "G"}, "explicit commander colors"),
            ({"zone": "companion"}, "companion pregame"),
        ):
            prepared = [{"revision": {"cards": [entry]}}]
            settings = SimpleNamespace(home=Path("."), verify=lambda: {})
            with (
                self.subTest(entry=entry),
                patch(
                    "commander.engine.forge_engine.prepare_seats", return_value=prepared
                ),
                patch("commander.engine.forge_engine.card_index", return_value={}),
                patch("commander.engine.forge_engine._new_run") as create,
            ):
                with self.assertRaisesRegex(ValueError, message):
                    run_game(self.db, settings, [(1, 1)] * 4)
                create.assert_not_called()
