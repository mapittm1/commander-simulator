"""Functional printing choices must survive import and never weaken deck gates."""

import copy
import unittest
from datetime import datetime, timezone
from uuid import NAMESPACE_URL, uuid5

from commander.catalog.database import (
    connect,
    get_card_details,
    store_card,
    store_printing,
)
from commander.decks.deck_store import get_revision, save_revision
from commander.decks.decks import parse_deck_sections, prepare_deck, save_deck
from commander.engine.forge_decks import serialize_deck
from commander.engine.forge_engine import check_game_evidence
from commander.rules.legality import validate_entries
from tests.unit.test_legality import card

# Behavior is described by test names.
# pylint: disable=missing-function-docstring


class AttractionPrintingTests(unittest.TestCase):
    """An explicit printing is a functional choice, not a new card or native script."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        self.sources = []
        with self.db:
            store_card(self.db, card("Leader", colors=["G"]))
            store_card(self.db, card("Forest", "Basic Land — Forest", colors=["G"]))
            for number in range(10):
                source = dict(
                    card(f"Attraction {number}", "Artifact — Attraction"),
                    id=str(uuid5(NAMESPACE_URL, f"printing-{number}-a")),
                    oracle_id=str(uuid5(NAMESPACE_URL, f"oracle-{number}")),
                    set="unf",
                    collector_number=f"{200 + number}a",
                    lang="en",
                    attraction_lights=[2, 6],
                )
                self.sources.append(source)
                store_card(self.db, source)
            store_printing(
                self.db,
                dict(
                    self.sources[0],
                    id=str(uuid5(NAMESPACE_URL, "printing-0-b")),
                    collector_number="200b",
                    attraction_lights=[3, 6],
                ),
            )
            self.db.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,12)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )

    def _text(self, selected="200a"):
        first = "Attraction 0" + (f" (UNF) {selected}" if selected else "")
        return (
            "Commander\n1 Leader\nMainboard\n99 Forest\nAttractions\n1 "
            + first
            + "\n"
            + "".join(f"1 Attraction {number}\n" for number in range(1, 10))
        )

    def test_arena_printing_syntax_is_preserved_only_for_attractions(self):
        sections = parse_deck_sections(self._text("200B *F*"))
        self.assertIn("Attraction 0 (UNF) 200B *F*", sections["attractions"])
        prepared = prepare_deck(self.db, self._text("200B *F*"))
        self.assertEqual(prepared["legality"]["status"], "legal", prepared)
        selected = next(entry for entry in prepared["cards"] if "printing" in entry)
        self.assertEqual(selected["printing"]["collector_number"], "200b")
        self.assertEqual(selected["printing"]["attraction_lights"], [3, 6])
        self.assertEqual(prepared["total"], 100)
        main = parse_deck_sections("1 Forest (M21) 274")
        self.assertIn("Forest", main["mainboard"])

    def test_revision_hashes_history_projection_and_native_selector_preserve_choice(
        self,
    ):
        prepared = prepare_deck(self.db, self._text())
        deck_id = save_deck(self.db, "Selected print", 3, prepared)
        first = get_revision(self.db, deck_id, 1)
        save_revision(self.db, deck_id, prepare_deck(self.db, self._text("200b")))
        second = get_revision(self.db, deck_id, 2)
        self.assertNotEqual(first["deck_hash"], second["deck_hash"])
        self.assertEqual(first, get_revision(self.db, deck_id, 1))
        selected = next(entry for entry in second["cards"] if "printing" in entry)
        self.assertEqual(
            self.db.execute("SELECT scryfall_id FROM deck_printing_choices").fetchone()[
                0
            ],
            selected["printing"]["id"],
        )
        index = {
            entry["card"]["name"]: entry["card"]["name"] for entry in second["cards"]
        }
        self.assertIn("1 Attraction 0|UNF|[200b]", serialize_deck(second, index, 1))
        self.assertEqual(
            self.db.execute("SELECT SUM(quantity) FROM deck_cards").fetchone()[0], 100
        )
        self.assertEqual(
            get_card_details(self.db, self.sources[0]["oracle_id"])[
                "attraction_lights"
            ],
            [2, 6],
        )

    def test_conflicting_printings_cannot_hide_a_duplicate_card(self):
        with self.assertRaisesRegex(ValueError, "Conflicting printings"):
            prepare_deck(self.db, self._text() + "1 Attraction 0 (UNF) 200b\n")
        report = prepare_deck(self.db, self._text() + "1 Attraction 0\n")["legality"]
        self.assertEqual(report["status"], "illegal")

    def test_missing_or_malicious_selectors_are_never_substituted(self):
        for suffix in ("999z", "200a]|Main|1", "200a\u2028bad"):
            with self.subTest(selector=suffix), self.assertRaises(ValueError):
                prepare_deck(self.db, self._text(suffix))

    def test_tampered_printing_identity_lights_and_native_text_are_rejected(self):
        prepared = prepare_deck(self.db, self._text())
        for field, value in (
            ("id", str(uuid5(NAMESPACE_URL, "different"))),
            ("oracle_id", str(uuid5(NAMESPACE_URL, "different"))),
            ("attraction_lights", [3, 6]),
            ("set", "unf|Main"),
            ("collector_number", "200a]|Main"),
            ("lang", "fr"),
            ("attraction_lights", [True, 6]),
        ):
            modified = copy.deepcopy(prepared["cards"])
            selected = next(entry for entry in modified if "printing" in entry)
            selected["printing"][field] = value
            with self.subTest(field=field, value=value):
                self.assertEqual(
                    validate_entries(self.db, modified)["status"], "illegal"
                )

    def test_native_export_rejects_injected_selectors_and_non_attraction_usage(self):
        prepared = prepare_deck(self.db, self._text())
        deck_id = save_deck(self.db, "Guarded export", 3, prepared)
        revision = get_revision(self.db, deck_id, 1)
        index = {
            entry["card"]["name"]: entry["card"]["name"] for entry in revision["cards"]
        }
        selected = next(entry for entry in revision["cards"] if "printing" in entry)
        selected["printing"]["collector_number"] = "200a]|Main"
        with self.assertRaises(ValueError):
            serialize_deck(revision, index, 1)
        selected["printing"]["collector_number"] = "200a"
        selected["zone"] = "mainboard"
        with self.assertRaises(ValueError):
            serialize_deck(revision, index, 1)

    def test_printing_updates_invalidate_approval_without_rewriting_history(self):
        prepared = prepare_deck(self.db, self._text())
        deck_id = save_deck(self.db, "Original print", 3, prepared)
        original = get_revision(self.db, deck_id, 1)
        with self.db:
            store_printing(self.db, dict(self.sources[0], attraction_lights=[4, 6]))
        self.assertEqual(
            validate_entries(self.db, original["cards"])["status"], "illegal"
        )
        self.assertEqual(get_revision(self.db, deck_id, 1), original)

    def test_name_only_selections_remain_supported_without_printing_metadata(self):
        prepared = prepare_deck(self.db, self._text(None))
        self.assertEqual(prepared["legality"]["status"], "legal")
        self.assertTrue(all("printing" not in entry for entry in prepared["cards"]))

    def test_printing_changes_after_legality_are_rechecked_at_game_admission(self):
        prepared = prepare_deck(self.db, self._text())
        deck_id = save_deck(self.db, "Admission recheck", 3, prepared)
        revision = get_revision(self.db, deck_id, 1)
        with self.db:
            store_printing(self.db, dict(self.sources[0], attraction_lights=[4, 6]))
        with self.assertRaisesRegex(ValueError, "printing facts changed"):
            check_game_evidence(self.db, [{"revision": revision}])

    def test_selected_source_printing_must_itself_be_legal_and_an_attraction(self):
        for changes in (
            {"legalities": {"commander": "not_legal"}},
            {"type_line": "Artifact"},
        ):
            with self.db:
                store_printing(self.db, {**self.sources[0], **changes})
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                prepare_deck(self.db, self._text())
