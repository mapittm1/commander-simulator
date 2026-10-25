"""Deck parsing, resolution, and persistence checks."""

import unittest

from database import connect, store_card
from decks import MAX_DECK_BYTES, list_decks, parse_decklist, prepare_deck, save_deck


class DeckTests(unittest.TestCase):
    def setUp(self):
        self.db = connect(":memory:")
        with self.db:
            for name, types, colors in (("Test Commander", "Legendary Creature", ["G"]),
                                         ("Forest", "Basic Land — Forest", ["G"]),
                                         ("Mountain", "Basic Land — Mountain", ["R"]),
                                         ("Test Spell", "Sorcery", ["G"])):
                store_card(self.db, {"id": name, "oracle_id": name, "name": name,
                                     "type_line": types, "color_identity": colors,
                                     "legalities": {"commander": "legal"}})

    def tearDown(self):
        self.db.close()

    def test_parse_sections_and_ignore_sideboard(self):
        main, commanders = parse_decklist("Commander\n1 Test Commander\nDeck\n99 Forest\nSideboard\n1 Mountain")
        self.assertEqual(main, {"Forest": 99})
        self.assertEqual(commanders, {"Test Commander": 1})

    def test_flat_export_with_explicit_commander(self):
        prepared = prepare_deck(self.db, "1 Test Commander\n99 Forest", ["Test Commander"])
        self.assertEqual(prepared["total"], 100)
        self.assertEqual(prepared["issues"], [])

    def test_persistence_and_bracket(self):
        prepared = prepare_deck(self.db, "99 Forest", ["Test Commander"])
        deck_id = save_deck(self.db, "Test deck", 3, prepared)
        deck = list_decks(self.db)[0]
        self.assertEqual(deck["id"], deck_id)
        self.assertEqual(deck["declared_bracket"], 3)
        self.assertIn("preliminary", deck["validation_json"])

    def test_color_and_singleton_issues(self):
        prepared = prepare_deck(self.db, "96 Forest\n1 Mountain\n2 Test Spell", ["Test Commander"])
        self.assertEqual(len(prepared["issues"]), 2)
        with self.assertRaises(ValueError):
            save_deck(self.db, "Invalid", 3, prepared)

    def test_aliases_are_counted_together(self):
        prepared = prepare_deck(self.db, "97 Forest\n1 Test Spell\n1 test spell", ["Test Commander"])
        self.assertTrue(any("Singleton" in issue for issue in prepared["issues"]))

    def test_unknown_card_and_bad_lines(self):
        with self.assertRaises(ValueError):
            prepare_deck(self.db, "99 Not A Card", ["Test Commander"])
        for text in ("Forest", "0 Forest", "-1 Forest"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                parse_decklist(text)

    def test_excessive_input_is_rejected(self):
        for text in ("9" * 100 + " Forest", "x" * (MAX_DECK_BYTES + 1),
                     "1 " + "x" * 301, "1 Forest\n" * 2001):
            with self.subTest(length=len(text)), self.assertRaises(ValueError):
                parse_decklist(text)

    def test_excessive_commanders_are_rejected(self):
        with self.assertRaises(ValueError):
            prepare_deck(self.db, "Commander\n100 Test Commander\nDeck\n1 Forest")

    def test_sql_text_in_names_is_treated_as_data(self):
        prepared = prepare_deck(self.db, "99 Forest", ["Test Commander"])
        title = "Robert'); DROP TABLE cards;--"
        save_deck(self.db, title, 3, prepared)
        self.assertEqual(list_decks(self.db)[0]["name"], title)
        self.assertEqual(self.db.execute("SELECT COUNT(*) FROM cards").fetchone()[0], 4)

    def test_invalid_deck_name_is_rejected(self):
        prepared = prepare_deck(self.db, "99 Forest", ["Test Commander"])
        with self.assertRaises(ValueError):
            save_deck(self.db, "   ", 3, prepared)

    def test_arena_export_metadata(self):
        main, _ = parse_decklist("99 Forest (SET) 123")
        self.assertEqual(main, {"Forest": 99})


if __name__ == "__main__":
    unittest.main()
