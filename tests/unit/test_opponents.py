"""Opponent collections preserve source scope and enforce current deck gates."""

import copy
import io
import sqlite3
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

from commander.catalog.database import connect, store_card
from commander.decks.deck_store import save_deck, save_revision
from commander.decks.decks import prepare_deck
from commander.opponents.opponent_manifest import MAX_MANIFEST_BYTES, read_manifest
from commander.opponents.opponents import (
    get_collection,
    require_collection,
    save_collection,
)
from commander.rules.brackets import load_policy, save_bracket_review

# Test names describe behavior without redundant docstrings.
# pylint: disable=missing-function-docstring


class OpponentTests(unittest.TestCase):
    """Test source metadata, immutable membership, and readiness rejection paths."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        self.now = datetime.now(timezone.utc).isoformat()
        with self.db:
            for name, types in [("Forest", "Basic Land — Forest")] + [
                (f"Leader {index}", "Legendary Creature — Human") for index in range(10)
            ]:
                record = {
                    "id": name,
                    "name": name,
                    "type_line": types,
                    "color_identity": ["G"],
                    "game_changer": False,
                    "oracle_id": name,
                    "legalities": {"commander": "legal"},
                }
                store_card(self.db, record)
            self.db.execute(
                "INSERT INTO imports(source, source_updated_at, records) VALUES (?, ?, 11)",
                ("oracle_cards", self.now),
            )
        self.review = {
            "reviewer": "Fixture reviewer",
            "rationale": "Synthetic fixture only.",
            "checks": dict.fromkeys(load_policy()["review_checks"], True),
        }
        self.manifest = {
            "schema_version": 1,
            "bracket_basis": "source_user_declared",
            "name": "Source sample fixture",
            "bracket": 3,
            "source_name": "Fixture dataset",
            "source_url": "https://example.com/sample",
            "collected_at": self.now,
            "time_window": "Fixture observation window",
            "sample_size": 1000,
            "scope": "source_sample",
            "entries": [],
        }

    def _entry(self, index=0, reviewed=True):
        prepared = prepare_deck(self.db, "99 Forest", [f"Leader {index}"])
        deck_id = save_deck(self.db, f"Representative {index}", 3, prepared)
        if reviewed:
            save_bracket_review(self.db, deck_id, self.review)
        return {
            "deck_id": deck_id,
            "version": 1,
            "observed_decks": 10 + index,
            "deck_source_url": f"https://example.com/decks/{index}",
            "author": "Fixture author",
        }

    def test_partial_collection_is_viewable_but_not_ready(self):
        self.manifest["entries"] = [self._entry()]
        collection_id = save_collection(self.db, self.manifest)
        stored = get_collection(self.db, collection_id)
        self.assertEqual(stored["collection"]["coverage"], "partial")
        self.assertEqual(stored["collection"]["provenance_basis"], "operator_supplied")
        with self.assertRaises(ValueError):
            require_collection(self.db, collection_id)

    def test_ten_distinct_representatives_can_pass_current_gate(self):
        self.manifest["entries"] = [self._entry(index) for index in range(10)]
        collection_id = save_collection(self.db, self.manifest)
        result = require_collection(self.db, collection_id)
        self.assertEqual(len(result["collection"]["entries"]), 10)
        self.assertEqual(result["collection"]["entries"][0]["observed_decks"], 19)
        self.assertEqual(result["collection"]["entries"][0]["sample_rank"], 1)

    def test_duplicate_commander_configuration_is_rejected(self):
        self.manifest["entries"] = [self._entry(), self._entry()]
        with self.assertRaises(ValueError):
            save_collection(self.db, self.manifest)

    def test_unreviewed_opponent_is_rejected(self):
        self.manifest["entries"] = [self._entry(reviewed=False)]
        with self.assertRaises(ValueError):
            save_collection(self.db, self.manifest)

    def test_collection_bracket_must_match_its_representatives(self):
        self.manifest["entries"] = [self._entry()]
        self.manifest["bracket"] = 2
        with self.assertRaises(ValueError):
            save_collection(self.db, self.manifest)

    def test_global_ranking_claim_and_impossible_counts_are_rejected(self):
        self.manifest["entries"] = [self._entry()]
        invalid = copy.deepcopy(self.manifest)
        invalid["scope"] = "global_top_ten"
        with self.assertRaises(ValueError):
            save_collection(self.db, invalid)
        invalid = copy.deepcopy(self.manifest)
        invalid["sample_size"] = 1
        with self.assertRaises(ValueError):
            save_collection(self.db, invalid)

    def test_new_deck_revision_does_not_move_collection_membership(self):
        entry = self._entry()
        self.manifest["entries"] = [entry]
        collection_id = save_collection(self.db, self.manifest)
        before = get_collection(self.db, collection_id)
        prepared = prepare_deck(self.db, "99 Forest", ["Leader 0"])
        save_revision(self.db, entry["deck_id"], prepared)
        self.assertEqual(get_collection(self.db, collection_id), before)
        self.assertEqual(before["collection"]["entries"][0]["version"], 1)

    def test_collection_rows_are_immutable(self):
        self.manifest["entries"] = [self._entry()]
        save_collection(self.db, self.manifest)
        for statement in (
            "DELETE FROM opponent_collections",
            "UPDATE opponent_collections SET name = 'Changed'",
        ):
            with self.assertRaises(sqlite3.IntegrityError):
                self.db.execute(statement)
            self.db.rollback()

    def test_bad_source_and_file_size_are_rejected(self):
        self.manifest["entries"] = [self._entry()]
        self.manifest["source_url"] = "javascript:alert(1)"
        with self.assertRaises(ValueError):
            save_collection(self.db, self.manifest)
        with patch(
            "pathlib.Path.open",
            return_value=io.BytesIO(b"x" * (MAX_MANIFEST_BYTES + 1)),
        ):
            with self.assertRaises(ValueError):
                read_manifest(Path("fixture.json"))

    def test_old_source_sample_cannot_be_ready(self):
        self.manifest["entries"] = [self._entry(index) for index in range(10)]
        self.manifest["collected_at"] = (
            datetime.now(timezone.utc) - timedelta(days=31)
        ).isoformat()
        collection_id = save_collection(self.db, self.manifest)
        with self.assertRaises(ValueError):
            require_collection(self.db, collection_id)

    def test_catalog_refresh_invalidates_collection_readiness(self):
        self.manifest["entries"] = [self._entry(index) for index in range(10)]
        collection_id = save_collection(self.db, self.manifest)
        with self.db:
            self.db.execute(
                "INSERT INTO imports(source, source_updated_at, records) VALUES (?, ?, 11)",
                ("oracle_cards", self.now),
            )
        with self.assertRaises(ValueError):
            require_collection(self.db, collection_id)


if __name__ == "__main__":
    unittest.main()
