"""Exact reviewed printing links can exclude a meld result but never admit a front."""

import copy
import json
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

from commander.engine.engine_gaps import classify_gap
from commander.rules.card_structure import meld_role
from commander.rules.meld_reviews import (
    REVIEWS_PATH,
    printing_fingerprint,
    reviewed_meld_result,
)

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class ReviewedMeldResultTests(unittest.TestCase):
    """The legacy Ragnarok printing is tied to the same Oracle identity by its source."""

    def setUp(self):
        self.manifest = json.loads(REVIEWS_PATH.read_text(encoding="utf-8"))
        review = self.manifest["reviews"][0]
        self.card = copy.deepcopy(review["related_printing"])
        self.card["id"] = review["sources"][0].rsplit("/", 1)[-1]
        self.assertEqual(printing_fingerprint(self.card), review["card_sha256"])
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.root = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        self.path = self.root / "reviews.json"
        stack.enter_context(
            patch("commander.rules.meld_reviews.REVIEWS_PATH", self.path)
        )
        self._save()

    def _save(self):
        self.path.write_text(json.dumps(self.manifest), encoding="utf-8")

    def test_exact_review_excludes_result_and_preserves_frozen_identity_evidence(self):
        self.assertEqual(meld_role(self.card), "meld_result")
        gap = classify_gap(self.card, {}, "missing_script")
        self.assertEqual(gap["category"], "non_starting_meld_result")
        self.assertEqual(
            gap["role_review"]["card_sha256"], printing_fingerprint(self.card)
        )

    def test_changed_identity_or_relationship_metadata_is_not_approved(self):
        for key, value in (
            ("id", "another-printing"),
            ("oracle_id", "another-card"),
            ("name", "Other Name"),
            ("all_parts", []),
        ):
            card = {**self.card, key: value}
            with self.subTest(field=key):
                self.assertEqual(reviewed_meld_result(card), "unknown")

    def test_review_cannot_approve_starting_parts_or_other_roles(self):
        self.manifest["reviews"][0]["role"] = "meld_part"
        self._save()
        with self.assertRaisesRegex(ValueError, "only exclude"):
            reviewed_meld_result(self.card)

    def test_changed_related_proof_cannot_be_accepted(self):
        review = self.manifest["reviews"][0]
        review["related_printing"]["oracle_id"] = "00000000-0000-0000-0000-000000000001"
        self._save()
        with self.assertRaisesRegex(ValueError, "fingerprint"):
            reviewed_meld_result(self.card)
        review["related_sha256"] = printing_fingerprint(review["related_printing"])
        self._save()
        with self.assertRaisesRegex(ValueError, "identity conflict"):
            reviewed_meld_result(self.card)

    def test_proof_must_reference_itself_as_a_result(self):
        review = self.manifest["reviews"][0]
        related = review["related_printing"]
        for part in related["all_parts"]:
            if part["id"] == related["id"]:
                part["component"] = "meld_part"
        review["related_sha256"] = printing_fingerprint(related)
        self._save()
        with self.assertRaisesRegex(ValueError, "does not identify itself"):
            reviewed_meld_result(self.card)

    def test_duplicate_deep_or_oversized_review_documents_are_rejected(self):
        for raw in (
            b"x" * 65537,
            b"[]",
            b'{"schema_version":1,"schema_version":1}',
            b'{"x":' + b"[" * 17 + b"0" + b"]" * 17 + b"}",
        ):
            self.path.write_bytes(raw)
            with self.subTest(size=len(raw)), self.assertRaises(ValueError):
                reviewed_meld_result(self.card)
        self.manifest["reviews"].append(copy.deepcopy(self.manifest["reviews"][0]))
        self._save()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            reviewed_meld_result(self.card)
