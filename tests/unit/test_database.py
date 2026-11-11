"""Offline checks for catalog imports and queries."""

import gzip
import io
import json
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path

from commander.catalog.database import catalog_stats, connect, find_cards
from commander.catalog.scryfall import (
    ScryfallRedirectHandler,
    import_bulk,
    iter_cards,
    validate_scryfall_url,
)

# Test names describe behavior; duplicating them in docstrings adds noise.
# pylint: disable=missing-function-docstring


class CatalogTests(unittest.TestCase):
    """Exercise catalog identity, parsing, and transactional import behavior."""

    def setUp(self):
        resources = ExitStack()
        self.addCleanup(resources.close)
        directory = resources.enter_context(
            tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parent)
        )
        self.root = Path(directory)
        self.connection = connect(self.root / "cards.sqlite3")
        self.card = {
            "id": "printing-1",
            "oracle_id": "card-1",
            "name": "Test Front // Test Back",
            "cmc": 3,
            "color_identity": ["G"],
            "legalities": {"commander": "legal"},
            "card_faces": [
                {"name": "Test Front", "mana_cost": "{2}{G}"},
                {"name": "Test Back", "power": "*", "toughness": "4"},
            ],
        }

    def tearDown(self):
        self.connection.close()

    def run_import(self, cards, source="local"):
        path = self.root / "cards.json"
        path.write_text(json.dumps(cards), encoding="utf-8")
        return import_bulk(self.connection, path, source)

    def test_faces_and_reprints_share_identity(self):
        reprint = dict(self.card, id="printing-2")
        self.run_import([self.card, reprint])
        self.assertEqual(
            catalog_stats(self.connection),
            {"cards": 1, "printings": 2, "card_faces": 4, "imports": 1},
        )
        self.assertEqual(
            find_cards(self.connection, "test back")[0]["card_id"], "card-1"
        )

    def test_updates_do_not_duplicate(self):
        self.run_import([self.card])
        self.card["oracle_text"] = "Updated text"
        self.run_import([self.card])
        self.assertEqual(catalog_stats(self.connection)["printings"], 1)
        self.assertEqual(
            find_cards(self.connection, self.card["name"])[0]["oracle_text"],
            "Updated text",
        )

    def test_scryfall_bulk_can_reassign_one_token_printing(self):
        old = {
            "id": "beast-printing",
            "oracle_id": "trample-beast",
            "name": "Beast",
            "set": "ttrc",
            "collector_number": "4",
            "lang": "en",
            "layout": "token",
            "legalities": {"commander": "not_legal"},
            "oracle_text": "Trample",
        }
        second_printing = dict(old, id="other-beast", collector_number="8")
        self.run_import([old, second_printing])
        corrected = dict(old, oracle_id="vanilla-beast", oracle_text="")

        with self.assertRaisesRegex(ValueError, "cannot change Oracle identity"):
            self.run_import([corrected])
        self.assertEqual(catalog_stats(self.connection)["cards"], 1)
        self.assertEqual(self.run_import([corrected], "oracle_cards"), 1)
        rows = self.connection.execute(
            "SELECT scryfall_id, card_id FROM printings ORDER BY scryfall_id"
        )
        self.assertEqual(
            [tuple(row) for row in rows],
            [("beast-printing", "vanilla-beast"), ("other-beast", "trample-beast")],
        )
        self.assertEqual(
            self.connection.execute(
                "SELECT oracle_text FROM cards WHERE card_id = 'trample-beast'"
            ).fetchone()[0],
            "Trample",
        )

    def test_scryfall_bulk_rejects_playable_card_or_printing_mismatch(self):
        token = {
            **self.card,
            "set": "test",
            "collector_number": "1",
            "lang": "en",
            "layout": "token",
            "legalities": {"commander": "not_legal"},
        }
        self.run_import([token])
        for changes in (
            {"layout": "normal"},
            {"legalities": {"commander": "legal"}},
            {"collector_number": "2"},
        ):
            with self.subTest(changes=changes):
                with self.assertRaisesRegex(
                    ValueError, "cannot change Oracle identity"
                ):
                    self.run_import(
                        [dict(token, oracle_id="card-2", **changes)], "oracle_cards"
                    )
        self.assertEqual(catalog_stats(self.connection)["cards"], 1)
        self.assertEqual(catalog_stats(self.connection)["imports"], 1)

    def test_bad_record_rolls_back_whole_import(self):
        with self.assertRaises(KeyError):
            self.run_import([self.card, {"id": "broken"}])
        self.assertEqual(catalog_stats(self.connection)["cards"], 0)
        self.assertEqual(catalog_stats(self.connection)["imports"], 0)

    def test_reversible_record_keeps_face_ids(self):
        del self.card["oracle_id"]
        self.card["card_faces"][0]["oracle_id"] = "front-id"
        self.run_import([self.card])
        self.assertEqual(
            find_cards(self.connection, "Test Front")[0]["card_id"], "printing-1"
        )
        self.assertEqual(
            self.connection.execute(
                "SELECT oracle_id FROM card_faces WHERE face_index = 0"
            ).fetchone()[0],
            "front-id",
        )

    def test_streaming_handles_large_records_and_unicode(self):
        self.card["oracle_text"] = "é" * 150000
        self.assertEqual(
            list(iter_cards(io.StringIO(json.dumps([self.card])))), [self.card]
        )

    def test_malformed_json_is_rejected(self):
        for text in ("[{},]", "[{} {}]", "[{}", "[{}] garbage", "{}"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                list(iter_cards(io.StringIO(text)))

    def test_compressed_json_lines(self):
        path = self.root / "cards.bulk"
        with gzip.open(path, "wt", encoding="utf-8") as stream:
            stream.write(json.dumps(self.card) + "\n")
        self.assertEqual(import_bulk(self.connection, path), 1)
        self.assertEqual(
            find_cards(self.connection, "Test Back")[0]["card_id"], "card-1"
        )

    def test_download_url_allowlist(self):
        for url in (
            "file:///etc/passwd",
            "http://api.scryfall.com/bulk-data",
            "https://evil.example/cards",
            "https://data.scryfall.io:444/cards",
            "https://user:secret@data.scryfall.io/cards",
        ):
            with self.subTest(url=url), self.assertRaises(ValueError):
                validate_scryfall_url(url)
        validate_scryfall_url("https://data.scryfall.io/cards.json")

    def test_redirect_cannot_leave_allowlist(self):
        handler = ScryfallRedirectHandler()
        with self.assertRaises(ValueError):
            handler.redirect_request(
                None, None, 302, "Found", {}, "https://evil.example"
            )

    def test_empty_import_is_rejected(self):
        with self.assertRaises(ValueError):
            self.run_import([])


if __name__ == "__main__":
    unittest.main()
