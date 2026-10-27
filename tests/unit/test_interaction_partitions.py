"""Reject incomplete families, unregistered paths, and changed multi-file evidence."""

import copy
import hashlib
import json
import shutil
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

from commander.engine.interactions.suite import (
    CASE_IDS,
    MAX_RECEIPT_BYTES,
    PARTITIONS,
    definition_fingerprint,
    definition_paths,
    definitions_unchanged,
    evaluate_receipt,
    load_suite,
    read_hashed_json,
)

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class DefinitionPartitionTests(unittest.TestCase):
    """Exercise real bounded files in an isolated copy of the fixed definition tree."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.root = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        for name, source in definition_paths().items():
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
        for field, value in (
            ("CONFIG_ROOT", self.root),
            ("SUITE_PATH", self.root / "interaction_suite.json"),
        ):
            mocked = patch("commander.engine.interactions.suite." + field, value)
            mocked.start()
            self.addCleanup(mocked.stop)

    def _write(self, relative, value):
        (self.root / relative).write_text(json.dumps(value), encoding="utf-8")

    def test_registry_covers_each_case_once_with_bounded_definition_files(self):
        suite = load_suite()
        self.assertEqual([len(ids) for ids in PARTITIONS.values()], [8, 6, 2, 4, 4])
        self.assertEqual(tuple(case["id"] for case in suite["cases"]), CASE_IDS)
        self.assertEqual(len(suite["definition_hashes"]), 6)
        self.assertTrue(definitions_unchanged(suite["definition_hashes"]))
        for path in definition_paths().values():
            self.assertLessEqual(path.stat().st_size, MAX_RECEIPT_BYTES)

    def test_manifest_cannot_select_unregistered_paths_or_partial_families(self):
        manifest = read_hashed_json(self.root / "interaction_suite.json")[0]
        for names in (
            ["../outside"],
            list(PARTITIONS)[:-1],
            list(reversed(PARTITIONS)),
            ["equipment"] * len(PARTITIONS),
            [{"path": "../outside.json"}],
        ):
            with self.subTest(partitions=names):
                self._write("interaction_suite.json", {**manifest, "partitions": names})
                with self.assertRaisesRegex(ValueError, "manifest"):
                    load_suite()
        self._write(
            "interaction_suite.json", {**manifest, "filename": "../outside.json"}
        )
        with self.assertRaises(ValueError):
            load_suite()

    def test_partition_rejects_wrong_identity_duplicate_missing_and_reordered_cases(
        self,
    ):
        original = read_hashed_json(self.root / "interactions/equipment.json")[0]
        mutations = [copy.deepcopy(original) for _ in range(5)]
        mutations[0]["partition_id"] = "damage"
        mutations[1]["cases"] = mutations[1]["cases"][:-1]
        mutations[2]["cases"].reverse()
        mutations[3]["cases"][1] = mutations[3]["cases"][0]
        mutations[4]["filename"] = "../outside.json"
        for document in mutations:
            with self.subTest(document=document.get("partition_id")):
                self._write("interactions/equipment.json", document)
                with self.assertRaisesRegex(ValueError, "partition"):
                    load_suite()

    def test_each_definition_retains_its_byte_limit_and_missing_files_fail(self):
        for relative, path in definition_paths().items():
            with self.subTest(path=relative):
                original = path.read_bytes()
                path.write_bytes(b" " * (MAX_RECEIPT_BYTES + 1))
                with self.assertRaisesRegex(ValueError, "size limit"):
                    load_suite()
                path.write_bytes(original)
        (self.root / "interactions/protection.json").unlink()
        with self.assertRaises(OSError):
            load_suite()

    def test_every_file_is_bound_by_exact_bytes_even_for_whitespace_only_edits(self):
        initial = load_suite()
        digest = definition_fingerprint(initial["definition_hashes"])
        for path in definition_paths().values():
            original = path.read_bytes()
            path.write_bytes(original + b"\n")
            current = load_suite()
            self.assertEqual(current["cases"], initial["cases"])
            self.assertFalse(definitions_unchanged(initial["definition_hashes"]))
            self.assertNotEqual(
                definition_fingerprint(current["definition_hashes"]), digest
            )
            path.write_bytes(original)
        self.assertTrue(definitions_unchanged(initial["definition_hashes"]))

    def test_hash_is_of_the_actual_bytes_parsed(self):
        raw = b'\xef\xbb\xbf{ "value": 3 }\n'
        path = self.root / "parsed.json"
        path.write_bytes(raw)
        value, digest = read_hashed_json(path)
        path.write_text('{"value":4}', encoding="utf-8")
        self.assertEqual(value, {"value": 3})
        self.assertEqual(digest, hashlib.sha256(raw).hexdigest())

    def test_verifier_rejects_unknown_empty_and_incomplete_expected_partitions(self):
        suite = load_suite()
        process = {"status": "finished", "returncode": 0}
        for ids in ((), ("arbitrary",), PARTITIONS["equipment"][:-1]):
            self.assertEqual(
                evaluate_receipt(suite, {"cases": []}, process, case_ids=ids)["status"],
                "inconclusive",
            )
        receipt = {"cases": [{"id": identity} for identity in PARTITIONS["equipment"]]}
        suite["cases"] = suite["cases"][1:]
        self.assertEqual(
            evaluate_receipt(suite, receipt, process, case_ids=PARTITIONS["equipment"])[
                "status"
            ],
            "inconclusive",
        )
