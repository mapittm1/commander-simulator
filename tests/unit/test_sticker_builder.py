"""Trusted source archives still require bounded, traversal-safe extraction."""

import tempfile
import unittest
import zipfile
from contextlib import ExitStack
from pathlib import Path

from tools.build_sticker_engine import _extract, _test_evidence

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class StickerBuildBoundaryTests(unittest.TestCase):
    """Reject dangerous archives and missing/skipped native build evidence."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.root = Path(stack.enter_context(tempfile.TemporaryDirectory()))

    def test_traversal_absolute_drive_and_symlink_entries_are_rejected(self):
        for number, name in enumerate(
            (
                "forge/../escape.txt",
                "/forge/escape.txt",
                "forge/C:escape.txt",
                "wrong/file.txt",
                "forge/link",
            )
        ):
            archive_path = self.root / f"bad-{number}.zip"
            entry = zipfile.ZipInfo(name)
            if name.endswith("link"):
                entry.create_system = 3
                entry.external_attr = 0o120777 << 16
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr(entry, b"untrusted")
            with self.subTest(name=name), self.assertRaises(ValueError):
                _extract(archive_path, self.root / f"out-{number}", "forge")
        self.assertFalse((self.root / "escape.txt").exists())

    def test_native_test_header_requires_all_tests_and_no_skips(self):
        report = (
            self.root / "forge-gui-desktop/target/surefire-reports/testng-results.xml"
        )
        report.parent.mkdir(parents=True)
        for header in (
            '<testng-results total="33" passed="32" failed="0" skipped="1">',
            '<testng-results total="32" passed="32" failed="0" skipped="0">',
            '<testng-results total="33" passed="32" failed="1" skipped="0">',
            "<testng-results>",
        ):
            report.write_text(header, encoding="utf-8")
            with self.subTest(header=header), self.assertRaises(ValueError):
                _test_evidence(self.root)
        report.write_text(
            '<testng-results total="33" passed="33" failed="0" skipped="0">',
            encoding="utf-8",
        )
        self.assertEqual(_test_evidence(self.root)["passed"], 33)
