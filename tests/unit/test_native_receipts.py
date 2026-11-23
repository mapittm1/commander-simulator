"""Large native results stay bounded and retain exact names, hashes and outcomes."""

import json
import unittest
from contextlib import contextmanager
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import Mock, patch

from commander.engine.forge_config import file_hash
from commander.engine.interactions.suite import MAX_RECEIPT_BYTES, read_hashed_json
from tests.support.native_receipts import read_test_receipts, write_test_receipts


@contextmanager
def temporary_workspace():
    """Keep temporary evidence alive through unittest cleanup."""
    with TemporaryDirectory() as directory:
        yield Path(directory)


class NativeReceiptTests(unittest.TestCase):
    """Exercise real receipt files without starting the native engine."""

    def setUp(self):
        self.directory = self.enterContext(temporary_workspace())
        profile = self.directory / "res/ai/Default.ai"
        profile.parent.mkdir(parents=True)
        profile.write_bytes(b"fixed profile")
        self.engine = {"identity": "fixed test engine"}
        self.settings = Mock(home=self.directory)
        self.settings.verify.return_value = self.engine
        self.enterContext(
            patch(
                "tests.support.native_receipts.read_settings",
                return_value=self.settings,
            )
        )
        self.name = "methods.json"
        self.manifest = "manifest.json"

    def write(self, count):
        """Use long, distinct method names that reproduce the original size failure."""
        rows = [
            {
                "method": "forge.ai." + "LongMethodName" * 9 + str(index),
                "status": "PASS",
            }
            for index in range(count)
        ]
        write_test_receipts(
            self.directory, self.engine, self.manifest, {self.name: rows}
        )
        return rows

    def read(self, count):
        """Read only the fixed caller-selected logical receipt."""
        return read_test_receipts(
            self.settings, self.directory, self.manifest, {self.name: count}
        )

    def alter_index(self, change):
        """Reseal an altered index so tests exercise names/counts independently of hashing."""
        path = self.directory / self.name
        document = json.loads(path.read_bytes())
        change(document)
        path.write_text(json.dumps(document), encoding="utf-8")
        manifest_path = self.directory / self.manifest
        manifest = json.loads(manifest_path.read_bytes())
        manifest["parts"][0]["sha256"] = file_hash(path)
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    def test_large_results_round_trip_in_bounded_parts(self):
        """Reproduce an oversized original document without increasing the limit."""
        rows = self.write(1176)
        self.assertGreater(len(json.dumps({"cases": rows}).encode()), MAX_RECEIPT_BYTES)
        self.assertEqual(self.read(1176), rows)
        document = json.loads((self.directory / self.name).read_bytes())
        self.assertEqual(
            [part["cases"] for part in document["chunks"]], [500, 500, 176]
        )
        self.assertTrue(
            all(
                path.stat().st_size <= MAX_RECEIPT_BYTES
                for path in self.directory.glob("*.json")
            )
        )

    def test_legacy_single_part_and_empty_results_remain_readable(self):
        """Existing evidence and zero-case controls retain their wire format."""
        rows = self.write(2)
        self.assertEqual(self.read(2), rows)
        self.assertNotIn(
            "chunks", json.loads((self.directory / self.name).read_bytes())
        )
        write_test_receipts(
            self.directory, self.engine, "empty-manifest.json", {"empty.json": []}
        )
        self.assertEqual(
            read_test_receipts(
                self.settings, self.directory, "empty-manifest.json", {"empty.json": 0}
            ),
            [],
        )

    def test_changed_chunk_bytes_are_rejected(self):
        """A valid JSON edit must still fail the bound content hash."""
        self.write(501)
        path = self.directory / "methods-0000.json"
        document = json.loads(path.read_bytes())
        document["cases"][0]["status"] = "FAIL"
        path.write_text(json.dumps(document), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "digest or case count"):
            self.read(501)

    def test_chunk_path_cannot_be_selected_by_the_manifest(self):
        """Reject escaped paths before attempting any physical case read."""
        self.write(501)
        self.alter_index(
            lambda document: document["chunks"][0].update(name="../outside.json")
        )
        with patch(
            "tests.support.native_receipts.read_hashed_json", wraps=read_hashed_json
        ) as reader:
            with self.assertRaisesRegex(ValueError, "chunk names or counts"):
                self.read(501)
            self.assertEqual(
                [call.args[0] for call in reader.call_args_list],
                [self.directory / self.name],
            )

    def test_missing_chunk_is_rejected_before_reading_case_files(self):
        """The index must account for every caller-expected case."""
        self.write(501)
        self.alter_index(lambda document: document["chunks"].pop())
        with self.assertRaisesRegex(ValueError, "chunk names or counts"):
            self.read(501)

    def test_incorrect_chunk_size_is_rejected(self):
        """Reject compensating or incomplete per-part counts."""
        self.write(501)
        self.alter_index(lambda document: document["chunks"][0].update(cases=499))
        with self.assertRaisesRegex(ValueError, "chunk names or counts"):
            self.read(501)

    def test_changed_index_is_rejected(self):
        """Whitespace changes still invalidate the canonical index digest."""
        self.write(501)
        path = self.directory / self.name
        path.write_bytes(path.read_bytes() + b" ")
        with self.assertRaisesRegex(ValueError, "digest or case count"):
            self.read(501)

    def test_caller_count_must_match_logical_total(self):
        """A caller-selected expected total cannot be supplied by the manifest."""
        self.write(501)
        with self.assertRaisesRegex(ValueError, "digest or case count"):
            self.read(502)

    def test_one_oversized_case_does_not_increase_the_document_limit(self):
        """Chunking cannot bypass a per-document size boundary."""
        with self.assertRaisesRegex(ValueError, "exceeds its size limit"):
            write_test_receipts(
                self.directory,
                self.engine,
                self.manifest,
                {self.name: [{"method": "x" * MAX_RECEIPT_BYTES}]},
            )
        self.assertFalse((self.directory / self.manifest).exists())
        self.assertFalse((self.directory / self.name).exists())
