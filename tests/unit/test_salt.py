"""Salt ratings are attributed opinions with partial coverage, not aggression probabilities."""

import copy
import json
import sqlite3
import tempfile
import unittest
from contextlib import ExitStack
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
from unittest.mock import patch

from commander.catalog.database import connect, store_card
from commander.decks.deck_store import save_deck
from commander.decks.decks import prepare_deck
from commander.salt.salt_analysis import deck_salt
from commander.salt.salt_data import (
    SALT_SOURCE,
    read_salt_manifest,
    score_hundredths,
    validate_salt_manifest,
)
from commander.salt.salt_store import save_salt_snapshot, show_salt_snapshot

# Test names express expected behavior.
# pylint: disable=missing-function-docstring


def sample() -> dict:
    """Create synthetic, explicitly non-production source facts for offline tests."""
    now = datetime.now(timezone.utc)
    return {
        "schema_version": 1,
        "source_url": SALT_SOURCE,
        "voting_year": now.year,
        "observed_at": now.isoformat(),
        "cards": [
            {"name": "Salt Card", "rank": 1, "score": 3.05},
            {"name": "Unknown Source Card", "rank": 2, "score": 2.5},
        ],
    }


class SaltInputTests(unittest.TestCase):
    """Strict values, schema, source, cardinality, and bounded local file parsing."""

    def test_published_scale_and_exact_hundredths(self):
        self.assertEqual(score_hundredths(Decimal("3.05")), 305)
        self.assertEqual(score_hundredths(0), 0)
        self.assertEqual(score_hundredths(4), 400)
        for value in (
            True,
            "3.05",
            -1,
            4.01,
            0.001,
            float("nan"),
            float("inf"),
            Decimal("1e-999999"),
        ):
            with self.subTest(value=str(value)), self.assertRaises(ValueError):
                score_hundredths(value)

    def test_invalid_schema_sources_years_and_dates_are_rejected(self):
        cases = [
            ("schema_version", True),
            ("schema_version", 1.0),
            ("voting_year", True),
            ("voting_year", datetime.now(timezone.utc).year + 1),
            ("source_url", "https://edhrec.com/top/salt?override=1"),
            ("source_url", "http://127.0.0.1/private"),
            ("observed_at", "2026-01-01"),
            (
                "observed_at",
                (datetime.now(timezone.utc) + timedelta(days=1)).isoformat(),
            ),
        ]
        for key, value in cases:
            data = sample()
            data[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                validate_salt_manifest(data)
        data = sample()
        data["extra"] = "not accepted"
        with self.assertRaises(ValueError):
            validate_salt_manifest(data)

    def test_duplicate_names_ranks_and_inconsistent_order_are_rejected(self):
        for changed in (
            {"rank": 1},
            {"name": "salt CARD"},
            {"score": 4},
            {"rank": True},
            {"name": "Bad\nName"},
        ):
            data = sample()
            data["cards"][1].update(changed)
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                validate_salt_manifest(data)
        for cards in ([], sample()["cards"] * 51):
            data = sample()
            data["cards"] = cards
            with self.assertRaises(ValueError):
                validate_salt_manifest(data)

    def test_local_json_limits_duplicate_keys_and_nonfinite_values(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "salt.json"
            for raw in (
                '{"cards":1,"cards":2}',
                '{"score":NaN}',
                '{"rank":' + "9" * 1000 + "}",
                '{"score":1e' + "9" * 100 + "}",
                "[" * 2000 + "0" + "]" * 2000,
                '"' + "x" * (1024 * 1024) + '"',
            ):
                path.write_text(raw, encoding="utf-8")
                with self.assertRaises(ValueError):
                    read_salt_manifest(path)
            path.write_text(json.dumps(sample()), encoding="utf-8")
            data = validate_salt_manifest(read_salt_manifest(path))
            self.assertEqual(data["cards"][0]["score_hundredths"], 305)


class SaltAnalysisTests(unittest.TestCase):
    """Real legality/catalog matching, immutable history, and explicitly unknown ratings."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.path = (
            Path(stack.enter_context(tempfile.TemporaryDirectory())) / "salt.sqlite3"
        )
        self.db = connect(self.path)
        self.addCleanup(self.db.close)
        fixtures = (
            ("Leader", "Legendary Creature — Human"),
            ("Forest", "Basic Land — Forest"),
            ("Salt Card", "Artifact"),
            ("Front // Back", "Creature — Human"),
        )
        with self.db:
            for name, types in fixtures:
                record = {
                    "name": name,
                    "id": name,
                    "oracle_id": name,
                    "type_line": types,
                    "color_identity": ["G"],
                    "legalities": {"commander": "legal"},
                }
                if name == "Front // Back":
                    record["card_faces"] = [
                        {"name": "Front", "type_line": types},
                        {"name": "Back", "type_line": types},
                    ]
                store_card(self.db, record)
            self.db.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,4)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )
        self.deck_id = save_deck(
            self.db,
            "Salt test fixture",
            2,
            prepare_deck(self.db, "98 Forest\n1 Salt Card", ["Leader"]),
        )

    def _import(self, data=None):
        return save_salt_snapshot(self.db, sample() if data is None else data)

    def test_known_and_unknown_ratings_remain_distinct(self):
        stored = self._import()
        self.assertEqual(stored["snapshot"]["unresolved_cards"], 1)
        result = deck_salt(self.db, self.deck_id, 1, stored["snapshot_id"])
        self.assertEqual(result["status"], "partial")
        self.assertEqual(
            result["coverage"],
            {
                "rated_quantity": 1,
                "unrated_quantity": 99,
                "total_quantity": 100,
                "rated_fraction": 0.01,
            },
        )
        self.assertEqual(result["known_rating_subtotal"], 3.05)
        self.assertEqual(result["mean_rated_card_rating"], 3.05)
        self.assertIsNone(result["aggression_probability"])
        self.assertEqual(result["cards"][0]["rating_status"], "rated_in_sample")
        self.assertTrue(all(card["rating"] is None for card in result["cards"][1:]))

    def test_zero_is_a_real_rating_while_an_absent_rating_remains_unknown(self):
        data = sample()
        data["cards"] = [{"name": "Salt Card", "rank": 1, "score": 0}]
        stored = self._import(data)
        result = deck_salt(self.db, self.deck_id, 1, stored["snapshot_id"])
        self.assertEqual(result["coverage"]["rated_quantity"], 1)
        self.assertEqual(result["mean_rated_card_rating"], 0)

    def test_face_names_match_catalog_identity_and_duplicate_identity_is_rejected(self):
        data = sample()
        data["cards"] = [{"name": "Front", "rank": 1, "score": 3}]
        stored = self._import(data)
        self.assertEqual(
            stored["snapshot"]["cards"][0]["catalog_name"], "Front // Back"
        )
        data["cards"].append({"name": "Front // Back", "rank": 2, "score": 2})
        with self.assertRaisesRegex(ValueError, "same catalog identity"):
            self._import(data)
        self.assertEqual(
            self.db.execute("SELECT count(*) FROM salt_snapshots").fetchone()[0], 1
        )

    def test_no_rated_cards_yields_unknown_mean_and_full_unrated_count(self):
        stored = self._import()
        deck_id = save_deck(
            self.db,
            "No known ratings",
            2,
            prepare_deck(self.db, "99 Forest", ["Leader"]),
        )
        result = deck_salt(self.db, deck_id, 1, stored["snapshot_id"])
        self.assertIsNone(result["mean_rated_card_rating"])
        self.assertEqual(result["coverage"]["unrated_quantity"], 100)

    def test_illegal_saved_decks_and_stale_catalogs_block_analysis(self):
        stored = self._import()
        record = json.loads(
            self.db.execute(
                "SELECT raw_json FROM card_details WHERE card_id=?", ("Salt Card",)
            ).fetchone()[0]
        )
        record["legalities"]["commander"] = "banned"
        with self.db:
            store_card(self.db, record)
        with self.assertRaisesRegex(ValueError, "legality gate"):
            deck_salt(self.db, self.deck_id, 1, stored["snapshot_id"])
        with self.db:
            self.db.execute(
                "UPDATE imports SET source_updated_at=?",
                ((datetime.now(timezone.utc) - timedelta(days=8)).isoformat(),),
            )
        with self.assertRaisesRegex(ValueError, "current"):
            self._import()

    def test_source_snapshot_is_frozen_and_old_observations_are_labeled(self):
        original = self._import()
        changed = sample()
        changed["cards"][0]["score"] = 3
        changed["observed_at"] = (
            datetime.now(timezone.utc) - timedelta(days=40)
        ).isoformat()
        changed["voting_year"] = datetime.fromisoformat(changed["observed_at"]).year
        later = self._import(changed)
        self.assertEqual(show_salt_snapshot(self.db, original["snapshot_id"]), original)
        self.assertEqual(
            deck_salt(self.db, self.deck_id, 1, original["snapshot_id"])[
                "known_rating_subtotal"
            ],
            3.05,
        )
        self.assertEqual(
            deck_salt(self.db, self.deck_id, 1, later["snapshot_id"])["freshness"],
            "stale",
        )
        for statement in (
            "UPDATE salt_snapshots SET snapshot_json='{}' WHERE id=?",
            "DELETE FROM salt_snapshots WHERE id=?",
        ):
            with self.assertRaises(sqlite3.IntegrityError), self.db:
                self.db.execute(statement, (original["snapshot_id"],))

    def test_ambiguous_matches_stay_unresolved_and_no_network_is_used(self):
        with patch(
            "commander.salt.salt_store.find_deck_candidates",
            return_value=[{"card_id": "a"}, {"card_id": "b"}],
        ):
            stored = self._import()
        self.assertTrue(
            all(
                card["resolution"] == "ambiguous"
                for card in stored["snapshot"]["cards"]
            )
        )
        self.assertEqual(stored["snapshot"]["resolved_cards"], 0)

    def test_invalid_identifiers_and_existing_transactions_are_rejected(self):
        stored = self._import()
        for version in (True, None, -1, 2**63):
            with self.assertRaises(ValueError):
                deck_salt(self.db, self.deck_id, version, stored["snapshot_id"])
        self.db.execute("BEGIN")
        with self.assertRaisesRegex(ValueError, "transaction"):
            show_salt_snapshot(self.db, stored["snapshot_id"])
        self.assertTrue(self.db.in_transaction)
        self.db.rollback()

    def test_input_is_not_modified_by_validation_or_import(self):
        data = sample()
        before = copy.deepcopy(data)
        self._import(data)
        self.assertEqual(data, before)

    def test_art_only_name_collisions_use_the_same_candidate_rules_as_deck_import(self):
        with self.db:
            store_card(
                self.db,
                {
                    "id": "art",
                    "oracle_id": "art",
                    "name": "Salt Card // Salt Card",
                    "layout": "art_series",
                    "legalities": {"commander": "not_legal"},
                    "card_faces": [{"name": "Salt Card"}, {"name": "Salt Card"}],
                },
            )
        stored = self._import()
        first = stored["snapshot"]["cards"][0]
        self.assertEqual(
            (first["card_id"], first["resolution"]), ("Salt Card", "resolved")
        )
        self.assertEqual(
            prepare_deck(self.db, "98 Forest\n1 Salt Card", ["Leader"])["legality"][
                "status"
            ],
            "legal",
        )
