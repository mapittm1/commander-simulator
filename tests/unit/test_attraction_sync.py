"""Printing synchronization must not replace Oracle facts or loosen network boundaries."""

import copy
import io
import json
import unittest
from unittest.mock import patch
from uuid import NAMESPACE_URL, uuid5

from commander.catalog.attraction_sync import SOURCE, sync_attraction_printings
from commander.catalog.database import connect, get_card_details, store_card
from tests.unit.test_printing_admission import _choice

# Test names state the expected behavior.
# pylint: disable=missing-function-docstring


class AttractionSyncTests(unittest.TestCase):
    """Treat API pages as bounded data and commit printing-only changes atomically."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        self.source = {
            **_choice(),
            "object": "card",
            "type_line": "Artifact — Attraction",
            "oracle_text": "Visit — Current Oracle text.",
            "legalities": {"commander": "legal"},
        }
        with self.db:
            store_card(
                self.db,
                {
                    **self.source,
                    "id": str(uuid5(NAMESPACE_URL, "baseline")),
                    "collector_number": "199a",
                },
            )
            self.db.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES ('oracle_cards','2026-10-25T00:00:00+00:00',1)"
            )

    def _page(self, cards=None, **fields):
        return dict(
            {
                "object": "list",
                "has_more": False,
                "total_cards": 1,
                "data": cards or [self.source],
            },
            **fields,
        )

    def _sync_raw(self, raw):
        with patch(
            "commander.catalog.attraction_sync.open_scryfall",
            side_effect=lambda *_args, **_kwargs: io.BytesIO(raw),
        ):
            return sync_attraction_printings(self.db)

    def test_sync_preserves_oracle_rules_and_catalog_freshness(self):
        before = get_card_details(self.db, self.source["oracle_id"])
        imports = list(self.db.execute("SELECT * FROM imports"))
        source = {**self.source, "oracle_text": "Older printed wording"}
        report = self._sync_raw(json.dumps(self._page([source])).encode())
        self.assertEqual(report["records"], 1)
        self.assertIs(report["oracle_catalog_freshness_updated"], False)
        self.assertEqual(get_card_details(self.db, self.source["oracle_id"]), before)
        self.assertEqual(list(self.db.execute("SELECT * FROM imports")), imports)
        self.assertEqual(
            self.db.execute("SELECT COUNT(*) FROM printings").fetchone()[0], 2
        )
        self.assertEqual(
            self.db.execute("SELECT source_sha256 FROM printing_syncs").fetchone()[0],
            report["source_sha256"],
        )

    def test_missing_canonical_identity_rolls_back_all_new_printings_and_receipt(self):
        unknown = {
            **self.source,
            "id": str(uuid5(NAMESPACE_URL, "unknown-print")),
            "oracle_id": str(uuid5(NAMESPACE_URL, "unknown-card")),
        }
        with self.assertRaisesRegex(ValueError, "existing exact catalog identity"):
            self._sync_raw(
                json.dumps(self._page([self.source, unknown], total_cards=2)).encode()
            )
        self.assertEqual(
            self.db.execute("SELECT COUNT(*) FROM printings").fetchone()[0], 1
        )
        self.assertIsNone(
            self.db.execute(
                "SELECT name FROM sqlite_master WHERE name='printing_syncs'"
            ).fetchone()
        )

    def test_pagination_retains_the_same_fixed_query_and_every_printing(self):
        second = {
            **self.source,
            "id": str(uuid5(NAMESPACE_URL, "second-print")),
            "collector_number": "200b",
            "attraction_lights": [3, 6],
        }
        pages = [
            self._page(total_cards=2, has_more=True, next_page=SOURCE + "&page=2"),
            self._page([second], total_cards=2),
        ]
        with (
            patch("commander.catalog.attraction_sync.time.sleep"),
            patch(
                "commander.catalog.attraction_sync.open_scryfall",
                side_effect=[io.BytesIO(json.dumps(page).encode()) for page in pages],
            ) as request,
        ):
            report = sync_attraction_printings(self.db)
        self.assertEqual(report["records"], 2)
        self.assertEqual(
            [call.args[0] for call in request.call_args_list],
            [SOURCE, SOURCE + "&page=2"],
        )

    def test_pagination_cannot_redirect_to_other_hosts_paths_or_queries(self):
        for url in (
            "https://evil.example/",
            "file:///data",
            "http://api.scryfall.com/cards/search",
            "https://api.scryfall.com/bulk-data",
            "https://api.scryfall.com/cards/search?q=other&unique=prints",
        ):
            payload = self._page(total_cards=2, has_more=True, next_page=url)
            with self.subTest(url=url), self.assertRaises(ValueError):
                self._sync_raw(json.dumps(payload).encode())
        self.assertEqual(
            self.db.execute("SELECT COUNT(*) FROM printings").fetchone()[0], 1
        )

    def test_incomplete_duplicate_or_excessive_inventories_are_rejected(self):
        cases = [
            self._page(total_cards=2),
            self._page([self.source, self.source], total_cards=2),
            self._page(total_cards=701),
        ]
        for payload in cases:
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                self._sync_raw(json.dumps(payload).encode())

    def test_large_deep_duplicate_key_and_non_finite_documents_fail_before_writes(self):
        payload = self._page([{**self.source, "cmc": float("nan")}])
        invalid = {
            "oversized": b"x" * (2 * 1024 * 1024 + 1),
            "deep": b"[" * 33 + b"0" + b"]" * 33,
            "duplicate": b'{"object":"list","object":"list"}',
            "non-finite": json.dumps(payload).encode(),
        }
        for reason, raw in invalid.items():
            with self.subTest(reason=reason), self.assertRaises(ValueError):
                self._sync_raw(raw)
        self.assertEqual(
            self.db.execute("SELECT COUNT(*) FROM printings").fetchone()[0], 1
        )

    def test_wrong_type_legality_language_and_light_facts_are_rejected(self):
        for field, value in (
            ("type_line", "Artifact"),
            ("legalities", {"commander": "banned"}),
            ("lang", "fr"),
            ("attraction_lights", [False, 6]),
        ):
            source = copy.deepcopy(self.source)
            source[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self._sync_raw(json.dumps(self._page([source])).encode())
