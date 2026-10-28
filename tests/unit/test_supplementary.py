"""Supplementary choices survive import, legality, snapshots and engine export."""

import unittest
from datetime import datetime, timezone

from commander.catalog.database import connect, store_card
from commander.decks.deck_store import get_revision, save_revision
from commander.decks.decks import parse_decklist, prepare_deck, save_deck
from commander.engine.forge_decks import serialize_deck
from tests.unit.test_legality import card

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class SupplementaryDeckTests(unittest.TestCase):
    """No supplementary object can replace a starting card or bypass validation."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        self.base = "Commander\n1 Leader\nMainboard\n99 Forest\n"
        with self.db:
            for value in (
                card("Leader", colors=["G"]),
                card("Forest", "Basic Land — Forest", colors=["G"]),
                *(card(f"Sheet {i}", "Stickers") for i in range(11)),
                *(card(f"Attraction {i}", "Artifact — Attraction") for i in range(11)),
            ):
                store_card(self.db, value)
            self.db.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,24)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )

    def _selection(self, zone="stickers", count=10):
        name = "Sheet" if zone == "stickers" else "Attraction"
        return zone + "\n" + "".join(f"1 {name} {i}\n" for i in range(count))

    def test_supplementary_selection_is_outside_100_and_not_silently_discarded(self):
        text = self.base + self._selection() + self._selection("attractions")
        result = prepare_deck(self.db, text)
        self.assertEqual(result["legality"]["status"], "legal", result)
        self.assertEqual((result["total"], result["supplementary_total"]), (100, 20))
        with self.assertRaisesRegex(ValueError, "supplementary"):
            parse_decklist(text)
        invalid = prepare_deck(self.db, text.replace("99 Forest", "98 Forest"))
        self.assertTrue(any("found 99" in issue for issue in invalid["issues"]))

    def test_small_duplicate_wrong_type_and_banned_selections_are_illegal(self):
        for zone in ("stickers", "attractions"):
            good = self.base + self._selection(zone)
            for text in (
                self.base + self._selection(zone, 9),
                good + ("1 Sheet 0\n" if zone == "stickers" else "1 Attraction 0\n"),
                good + "1 Forest\n",
            ):
                with self.subTest(zone=zone, text=text):
                    self.assertEqual(
                        prepare_deck(self.db, text)["legality"]["status"], "illegal"
                    )
                    with self.assertRaises(ValueError):
                        save_deck(self.db, "Invalid", 3, prepare_deck(self.db, text))
        value = card("Attraction 0", "Artifact — Attraction")
        value["legalities"]["commander"] = "banned"
        with self.db:
            store_card(self.db, value)
        self.assertEqual(
            prepare_deck(self.db, self.base + self._selection("attractions"))[
                "legality"
            ]["status"],
            "illegal",
        )

    def test_attraction_cannot_fill_mainboard_or_commander_slot(self):
        for text in (
            self.base.replace("99 Forest", "98 Forest\n1 Attraction 0"),
            self.base.replace("Leader", "Attraction 0"),
        ):
            report = prepare_deck(self.db, text)["legality"]
            self.assertEqual(report["status"], "illegal")
            self.assertTrue(
                any("Attraction cannot" in issue for issue in report["errors"])
            )

    def test_more_than_ten_is_allowed_and_selection_changes_revision_hash(self):
        prepared = prepare_deck(self.db, self.base + self._selection(count=11))
        self.assertEqual(prepared["legality"]["status"], "legal")
        deck_id = save_deck(self.db, "Selected sheets", 3, prepared)
        original = get_revision(self.db, deck_id, 1)
        self.assertEqual(
            self.db.execute("SELECT COUNT(*) FROM deck_supplementary_cards").fetchone()[
                0
            ],
            11,
        )
        self.assertEqual(
            self.db.execute("SELECT SUM(quantity) FROM deck_cards").fetchone()[0], 100
        )
        save_revision(self.db, deck_id, prepare_deck(self.db, self.base))
        latest = get_revision(self.db, deck_id, 2)
        self.assertNotEqual(latest["deck_hash"], original["deck_hash"])
        self.assertEqual(len(get_revision(self.db, deck_id, 1)["cards"]), 13)
        self.assertEqual(
            self.db.execute("SELECT COUNT(*) FROM deck_supplementary_cards").fetchone()[
                0
            ],
            0,
        )

    def test_engine_export_retains_both_selections_in_separate_sections(self):
        prepared = prepare_deck(
            self.db, self.base + self._selection() + self._selection("attractions")
        )
        deck_id = save_deck(self.db, "Export", 3, prepared)
        revision = get_revision(self.db, deck_id, 1)
        index = {
            entry["card"]["name"]: entry["card"]["name"] for entry in revision["cards"]
        }
        exported = serialize_deck(revision, index, 1)
        main, auxiliary = exported.split("[Stickers]")
        self.assertNotIn("1 Sheet", main)
        self.assertIn("1 Sheet 0", auxiliary)
        self.assertIn("[Attractions]\n1 Attraction 0", auxiliary)
