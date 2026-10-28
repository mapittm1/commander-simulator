"""Meld-front proofs require exact Oracle identity and a complete pinned printing graph."""

import copy
import json
import tempfile
import unittest
from contextlib import ExitStack
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from commander.catalog.database import connect, store_card
from commander.decks.decks import prepare_deck
from commander.rules.card_structure import meld_role
from commander.rules.meld_part_reviews import REVIEWS_PATH, meld_part_review
from commander.rules.meld_reviews import printing_fingerprint
from tests.unit.test_legality import card

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class MeldFrontProofTests(unittest.TestCase):
    """A reviewed role unblocks normal construction checks without bypassing them."""

    def setUp(self):
        self.manifest = json.loads(REVIEWS_PATH.read_text(encoding="utf-8"))
        self.current = copy.deepcopy(self.manifest["reviews"][0]["current_printing"])
        resources = ExitStack()
        self.addCleanup(resources.close)
        directory = resources.enter_context(tempfile.TemporaryDirectory())
        self.path = Path(directory) / "fronts.json"
        resources.enter_context(
            patch("commander.rules.meld_part_reviews.REVIEWS_PATH", self.path)
        )
        self._save()

    def _save(self):
        self.path.write_text(json.dumps(self.manifest), encoding="utf-8")

    def test_both_exact_source_links_admit_only_the_front_role(self):
        for review in self.manifest["reviews"]:
            current = review["current_printing"]
            with self.subTest(card=current["name"]):
                self.assertEqual(meld_role(current), "meld_part")
                self.assertEqual(meld_part_review(current), review)

    def test_identity_or_relationship_changes_do_not_inherit_approval(self):
        for field, value in (
            ("id", "different"),
            ("oracle_id", "different"),
            ("name", "Other"),
            ("all_parts", []),
        ):
            with self.subTest(field=field):
                self.assertIsNone(meld_part_review({**self.current, field: value}))

    def test_related_printing_cannot_be_a_result_or_different_identity(self):
        for field, value in (
            ("oracle_id", "00000000-0000-0000-0000-000000000001"),
            ("name", "Other"),
        ):
            manifest = copy.deepcopy(self.manifest)
            manifest["reviews"][0]["related_printing"][field] = value
            self.path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.subTest(field=field), self.assertRaises(ValueError):
                meld_part_review(self.current)
        self._save()
        review = self.manifest["reviews"][0]
        for printing in (review["current_printing"], review["related_printing"]):
            for part in printing["all_parts"]:
                if part["id"] == review["related_printing"]["id"]:
                    part["component"] = "meld_result"
        review["card_sha256"] = printing_fingerprint(review["current_printing"])
        self._save()
        with self.assertRaises(ValueError):
            meld_part_review(self.current)

    def test_source_identity_and_fingerprint_are_mandatory(self):
        for field, value in (
            ("card_sha256", "0" * 64),
            ("sources", ["https://example.com/"]),
        ):
            manifest = copy.deepcopy(self.manifest)
            manifest["reviews"][0][field] = value
            self.path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.subTest(field=field), self.assertRaises(ValueError):
                meld_part_review(self.current)

    def test_duplicate_oversized_deep_and_malformed_manifests_fail(self):
        invalid_documents = {
            "oversized": b"x" * 65537,
            "wrong-root": b"[]",
            "duplicate-key": b'{"schema_version":1,"schema_version":1}',
            "too-deep": b'{"x":' + b"[" * 17 + b"0" + b"]" * 17 + b"}",
        }
        for reason, raw in invalid_documents.items():
            self.path.write_bytes(raw)
            with self.subTest(reason=reason), self.assertRaises(ValueError):
                meld_part_review(self.current)
        self.manifest["reviews"].append(copy.deepcopy(self.manifest["reviews"][0]))
        self._save()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            meld_part_review(self.current)

    def test_normal_legality_and_frozen_report_retain_used_proof(self):
        db = connect(":memory:")
        self.addCleanup(db.close)
        with db:
            for review, color, basic in zip(
                self.manifest["reviews"], ("B", "G"), ("Swamp", "Forest")
            ):
                source = review["current_printing"]
                selected = {**card(source["name"], colors=[color]), **source}
                store_card(db, selected)
                store_card(db, card(basic, "Basic Land — " + basic, colors=[color]))
            db.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,4)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )
        for review, basic in zip(self.manifest["reviews"], ("Swamp", "Forest")):
            source = review["current_printing"]
            with self.subTest(commander=source["name"]):
                prepared = prepare_deck(
                    db, f"Commander\n1 {source['name']}\nMainboard\n99 {basic}"
                )
                self.assertEqual(
                    prepared["legality"]["status"], "legal", prepared["legality"]
                )
                self.assertEqual(
                    prepared["legality"]["meld_part_reviews"][source["oracle_id"]],
                    review,
                )
                invalid = prepare_deck(
                    db, f"Commander\n1 {source['name']}\nMainboard\n98 {basic}"
                )
                self.assertEqual(invalid["legality"]["status"], "illegal")

    def test_malformed_relationship_fields_are_rejected_cleanly(self):
        for field, value in (("id", {}), ("name", []), ("component", None)):
            manifest = copy.deepcopy(self.manifest)
            manifest["reviews"][0]["related_printing"]["all_parts"][0][field] = value
            self.path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.subTest(field=field), self.assertRaises(ValueError):
                meld_part_review(self.current)
