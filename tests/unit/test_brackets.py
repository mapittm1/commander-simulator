"""Bracket review must never be confused with legality or automatic power scoring."""

import io
import json
import sqlite3
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from commander.catalog.database import connect, store_card
from commander.decks.deck_store import save_deck, save_revision
from commander.decks.decks import prepare_deck
from commander.rules.brackets import (
    MAX_REVIEW_BYTES,
    check_bracket,
    load_policy,
    read_review,
    require_bracket_review,
    review_history,
    save_bracket_review,
)

# Behavior is expressed in test names.
# pylint: disable=missing-function-docstring


class BracketTests(unittest.TestCase):
    """Exercise Game Changer caps, provenance, human claims, and stale reviews."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        with self.db:
            for name, types, flag in (
                ("Leader", "Legendary Creature — Human", False),
                ("Forest", "Basic Land — Forest", False),
                ("Changer A", "Artifact", True),
                ("Changer B", "Artifact", True),
                ("Changer C", "Artifact", True),
                ("Changer D", "Artifact", True),
            ):
                record = {
                    "name": name,
                    "id": name,
                    "oracle_id": name,
                    "game_changer": flag,
                    "color_identity": ["G"],
                    "legalities": {"commander": "legal"},
                    "type_line": types,
                }
                store_card(self.db, record)
            self.db.execute(
                "INSERT INTO imports(source, source_updated_at, records) VALUES (?, ?, 6)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )
        self.review = {
            "reviewer": "Test reviewer",
            "rationale": "Reviewed the documented bracket expectations.",
            "checks": dict.fromkeys(load_policy()["review_checks"], True),
        }

    def _deck(self, bracket=3, changers=0):
        text = f"{99-changers} Forest\n"
        text += "\n".join(f"1 Changer {letter}" for letter in "ABCD"[:changers])
        prepared = prepare_deck(self.db, text, ["Leader"])
        return save_deck(self.db, "Bracket fixture", bracket, prepared)

    def test_game_changer_caps_are_applied_per_bracket(self):
        for bracket, count, conflict in (
            (1, 1, True),
            (2, 1, True),
            (3, 3, False),
            (3, 4, True),
            (4, 4, False),
            (5, 4, False),
        ):
            deck_id = self._deck(bracket, count)
            result = check_bracket(self.db, deck_id)
            self.assertEqual(result["status"] == "conflict", conflict)
            self.assertEqual(result["game_changer_count"], count)

    def test_automatic_count_never_certifies_intent(self):
        result = check_bracket(self.db, self._deck(2))
        self.assertEqual(result["status"], "needs_review")
        self.assertEqual(result["basis"], "automated_count_only")

    def test_review_is_auditable_and_revision_bound(self):
        deck_id = self._deck()
        review_id = save_bracket_review(self.db, deck_id, self.review)
        report = require_bracket_review(self.db, deck_id)
        self.assertEqual(report["review_id"], review_id)
        self.assertEqual(report["basis"], "automated_count_and_human_attestation")
        self.assertEqual(
            review_history(self.db, deck_id)[0]["report"]["review"], self.review
        )
        prepared = prepare_deck(self.db, "99 Forest", ["Leader"])
        save_revision(self.db, deck_id, prepared)
        with self.assertRaises(ValueError):
            require_bracket_review(self.db, deck_id)
        self.assertEqual(
            require_bracket_review(self.db, deck_id, version=1)["status"], "reviewed"
        )

    def test_attestation_cannot_override_count_conflict(self):
        with self.assertRaises(ValueError):
            save_bracket_review(self.db, self._deck(2, 1), self.review)

    def test_false_or_nonboolean_checks_do_not_pass(self):
        deck_id = self._deck()
        for value in (False, "true", 1):
            self.review["checks"]["intent"] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                save_bracket_review(self.db, deck_id, self.review)

    def test_missing_flags_block_review(self):
        deck_id = self._deck()
        with self.db:
            raw = json.loads(
                self.db.execute(
                    "SELECT raw_json FROM card_details WHERE card_id = 'Leader'"
                ).fetchone()[0]
            )
            del raw["game_changer"]
            store_card(self.db, raw)
        with self.assertRaises(ValueError):
            save_bracket_review(self.db, deck_id, self.review)

    def test_new_catalog_invalidates_prior_review(self):
        deck_id = self._deck()
        save_bracket_review(self.db, deck_id, self.review)
        with self.db:
            self.db.execute(
                "INSERT INTO imports(source, source_updated_at, records) VALUES (?, ?, 6)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )
        with self.assertRaises(ValueError):
            require_bracket_review(self.db, deck_id)

    def test_illegal_deck_blocks_bracket_processing(self):
        deck_id = self._deck()
        with self.db:
            raw = json.loads(
                self.db.execute(
                    "SELECT raw_json FROM card_details WHERE card_id = 'Leader'"
                ).fetchone()[0]
            )
            raw["legalities"]["commander"] = "banned"
            store_card(self.db, raw)
        with self.assertRaises(ValueError):
            check_bracket(self.db, deck_id)

    def test_policy_change_requires_a_new_review(self):
        deck_id = self._deck()
        save_bracket_review(self.db, deck_id, self.review)
        changed = load_policy()
        changed["hash"] = "a-new-policy-fingerprint"
        with patch("commander.rules.brackets.load_policy", return_value=changed):
            with self.assertRaises(ValueError):
                require_bracket_review(self.db, deck_id)

    def test_command_zone_game_changers_are_counted(self):
        deck_id = self._deck(2)
        with self.db:
            raw = json.loads(
                self.db.execute(
                    "SELECT raw_json FROM card_details WHERE card_id = 'Leader'"
                ).fetchone()[0]
            )
            raw["game_changer"] = True
            store_card(self.db, raw)
        result = check_bracket(self.db, deck_id)
        self.assertEqual(result["status"], "conflict")
        self.assertEqual(result["game_changers"][0]["zone"], "commander")

    def test_review_files_are_bounded_json_objects(self):
        for content in (b"x" * (MAX_REVIEW_BYTES + 1), b"[]"):
            with patch("pathlib.Path.open", return_value=io.BytesIO(content)):
                with self.assertRaises(ValueError):
                    read_review(Path("fixture.json"))

    def test_invalid_review_metadata_is_rejected(self):
        deck_id = self._deck()
        for reviewer in ("", "name\nwith controls", "x" * 101):
            invalid = {**self.review, "reviewer": reviewer}
            with self.subTest(reviewer=reviewer), self.assertRaises(ValueError):
                save_bracket_review(self.db, deck_id, invalid)

    def test_review_records_are_immutable(self):
        deck_id = self._deck()
        save_bracket_review(self.db, deck_id, self.review)
        for statement in (
            "DELETE FROM bracket_reviews",
            "UPDATE bracket_reviews SET report_json = '{}'",
        ):
            with self.assertRaises(sqlite3.IntegrityError):
                self.db.execute(statement)
            self.db.rollback()


if __name__ == "__main__":
    unittest.main()
