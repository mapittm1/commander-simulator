"""Reviewed exports require exact identities and reproducible script evidence."""

import copy
import hashlib
import json
import tempfile
import unittest
import zipfile
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

from commander.engine.engine_aliases import apply_alias_reviews
from commander.engine.forge_config import file_hash
from commander.engine.forge_decks import coverage, script_match, serialize_deck

# Test names express behavior.
# pylint: disable=missing-function-docstring


class ReviewedAliasTests(unittest.TestCase):
    """No punctuation normalization, name-only match, or changed evidence is sufficient."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.root = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        self.archive = self.root / "cards.zip"
        script = b"Name:Engine:Name\n"
        with zipfile.ZipFile(self.archive, "w") as archive:
            archive.writestr("e/engine.txt", script)
        self.review = {
            "review_id": "fixture-only",
            "name": "Engine꞉Name",
            "oracle_id": "a37f8381-83a4-4b6b-8176-c90f6a61da74",
            "engine_name": "Engine:Name",
            "archive_sha256": file_hash(self.archive),
            "script_sha256": hashlib.sha256(script).hexdigest(),
            "script_path": "e/engine.txt",
            "sources": ["https://example.test/review"],
            "scope": "Synthetic unit-test identity only.",
        }
        self.manifest = {"schema_version": 1, "reviews": [self.review]}
        self.path = self.root / "reviews.json"
        registry = patch("commander.engine.engine_aliases.REVIEWS_PATH", self.path)
        registry.start()
        self.addCleanup(registry.stop)

    def _index(self, entries=None):
        self.path.write_text(json.dumps(self.manifest), encoding="utf-8")
        return apply_alias_reviews(
            self.archive, {"Engine:Name": "Engine:Name"} if entries is None else entries
        )

    def _revision(self, identity=None):
        return {
            "id": 1,
            "cards": [
                {
                    "zone": "commander",
                    "quantity": 1,
                    "card": {
                        "name": self.review["name"],
                        "card_id": identity or self.review["oracle_id"],
                    },
                }
            ],
        }

    def test_exact_identity_exports_and_retains_review_evidence(self):
        index = self._index()
        result = script_match(self.review["name"], index, self.review["oracle_id"])
        self.assertEqual(result["engine_name"], "Engine:Name")
        self.assertEqual(result["alias_review"], self.review)
        self.assertEqual(
            coverage(self._revision(), index)["alias_reviews"], [self.review]
        )
        self.assertIn("1 Engine:Name", serialize_deck(self._revision(), index, 1))

    def test_name_without_the_reviewed_identity_cannot_export(self):
        index = self._index()
        for identity in (None, "another-identity"):
            self.assertEqual(
                script_match(self.review["name"], index, identity)["script_status"],
                "missing_script",
            )
        with self.assertRaises(ValueError):
            serialize_deck(self._revision("another-identity"), index, 1)

    def test_other_similar_names_are_not_mapped(self):
        index = self._index()
        self.assertEqual(
            script_match("Engine’Name", index, self.review["oracle_id"])[
                "script_status"
            ],
            "missing_script",
        )

    def test_changed_archive_does_not_inherit_an_approval(self):
        self.review["archive_sha256"] = "0" * 64
        self.assertNotIn(self.review["name"], self._index())

    def test_wrong_script_evidence_is_rejected(self):
        self.review["script_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "hash changed"):
            self._index()

    def test_missing_or_noncanonical_targets_and_conflicts_are_rejected(self):
        for entries in (
            {},
            {"Engine:Name": "Other"},
            {"Engine:Name": "Engine:Name", "Engine꞉Name": "Engine:Name"},
        ):
            with self.assertRaises(ValueError):
                self._index(entries)

    def test_duplicate_names_and_identities_are_rejected(self):
        duplicate = copy.deepcopy(self.review)
        self.manifest["reviews"].append(duplicate)
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self._index()
        duplicate["name"] = "Another"
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self._index()

    def test_invalid_or_unsafe_review_fields_are_rejected(self):
        for field, value in (
            ("engine_name", "Engine|SET"),
            ("oracle_id", "invalid"),
            ("script_sha256", "short"),
            ("sources", ["http://example.test"]),
            ("name", "bad\nname"),
        ):
            with self.subTest(field=field):
                original = self.review[field]
                self.review[field] = value
                with self.assertRaises(ValueError):
                    self._index()
                self.review[field] = original

    def test_registry_size_schema_count_and_duplicate_fields_are_bounded(self):
        for raw in (
            b"x" * 65537,
            b"[]",
            b'{"schema_version":1,"schema_version":1}',
            json.dumps({"schema_version": 1, "reviews": [{}] * 65}).encode(),
        ):
            self.path.write_bytes(raw)
            with self.assertRaises(ValueError):
                apply_alias_reviews(self.archive, {})
