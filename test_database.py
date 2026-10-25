"""Offline checks for catalog imports and queries."""

import io
import json
import tempfile
import unittest
from pathlib import Path

from database import catalog_stats, connect, find_cards
from scryfall import import_bulk, iter_cards


class CatalogTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parent)
        self.root = Path(self.temp.name)
        self.connection = connect(self.root / "cards.sqlite3")
        self.card = {
            "id": "printing-1", "oracle_id": "card-1", "name": "Test Front // Test Back",
            "cmc": 3, "color_identity": ["G"], "legalities": {"commander": "legal"},
            "card_faces": [{"name": "Test Front", "mana_cost": "{2}{G}"},
                           {"name": "Test Back", "power": "*", "toughness": "4"}],
        }

    def tearDown(self):
        self.connection.close()
        self.temp.cleanup()

    def run_import(self, cards):
        path = self.root / "cards.json"
        path.write_text(json.dumps(cards), encoding="utf-8")
        return import_bulk(self.connection, path)

    def test_faces_and_reprints_share_identity(self):
        reprint = dict(self.card, id="printing-2")
        self.run_import([self.card, reprint])
        self.assertEqual(catalog_stats(self.connection),
                         {"cards": 1, "printings": 2, "card_faces": 4, "imports": 1})
        self.assertEqual(find_cards(self.connection, "test back")[0]["card_id"], "card-1")

    def test_updates_do_not_duplicate(self):
        self.run_import([self.card])
        self.card["oracle_text"] = "Updated text"
        self.run_import([self.card])
        self.assertEqual(catalog_stats(self.connection)["printings"], 1)
        self.assertEqual(find_cards(self.connection, self.card["name"])[0]["oracle_text"],
                         "Updated text")

    def test_bad_record_rolls_back_whole_import(self):
        with self.assertRaises(KeyError):
            self.run_import([self.card, {"id": "broken"}])
        self.assertEqual(catalog_stats(self.connection)["cards"], 0)
        self.assertEqual(catalog_stats(self.connection)["imports"], 0)

    def test_reversible_record_keeps_face_ids(self):
        del self.card["oracle_id"]
        self.card["card_faces"][0]["oracle_id"] = "front-id"
        self.run_import([self.card])
        self.assertEqual(find_cards(self.connection, "Test Front")[0]["card_id"], "printing-1")
        self.assertEqual(self.connection.execute(
            "SELECT oracle_id FROM card_faces WHERE face_index = 0").fetchone()[0], "front-id")

    def test_streaming_handles_large_records_and_unicode(self):
        self.card["oracle_text"] = "é" * 150000
        self.assertEqual(list(iter_cards(io.StringIO(json.dumps([self.card])))), [self.card])

    def test_malformed_json_is_rejected(self):
        for text in ('[{},]', '[{} {}]', '[{}', '[{}] garbage', '{}'):
            with self.subTest(text=text), self.assertRaises(ValueError):
                list(iter_cards(io.StringIO(text)))

    def test_compressed_json_lines(self):
        import gzip
        path = self.root / "cards.bulk"
        with gzip.open(path, "wt", encoding="utf-8") as stream:
            stream.write(json.dumps(self.card) + "\n")
        self.assertEqual(import_bulk(self.connection, path), 1)
        self.assertEqual(find_cards(self.connection, "Test Back")[0]["card_id"], "card-1")

    def test_empty_import_is_rejected(self):
        with self.assertRaises(ValueError):
            self.run_import([])


if __name__ == "__main__":
    unittest.main()
