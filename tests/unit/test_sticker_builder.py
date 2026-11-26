"""Trusted source archives still require bounded, traversal-safe extraction."""

import json
import tempfile
import unittest
import zipfile
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

from commander.paths import PROJECT_ROOT
from tools.build_sticker_engine import (
    _copy_regressions,
    _extract,
    _test_evidence,
    _verify_patches,
)
from tools.engine_test_sources import REGRESSION_PACKAGES, REGRESSION_SOURCES

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class StickerBuildBoundaryTests(unittest.TestCase):
    """Reject dangerous archives and missing/skipped native build evidence."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.root = Path(stack.enter_context(tempfile.TemporaryDirectory()))

    def test_current_recipe_patches_match_builder_paths_and_hashes(self):
        recipe = json.loads((PROJECT_ROOT / "engine/sticker_recipe.json").read_bytes())
        _verify_patches(recipe["patches"])

    def test_unregistered_patch_paths_are_rejected_before_reading(self):
        for name in (
            "engine/patches/unreviewed.patch",
            "../outside.patch",
            "engine/patches/commander-payload-graph.patch/..",
        ):
            with (
                self.subTest(path=name),
                patch("tools.build_sticker_engine.file_hash") as digest,
            ):
                with self.assertRaisesRegex(ValueError, "patch registry or content"):
                    _verify_patches([{"path": name, "sha256": "0" * 64}])
                digest.assert_not_called()

    def test_changed_reviewed_patch_content_hash_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "patch registry or content"):
            _verify_patches(
                [
                    {
                        "path": "engine/patches/commander-payload-graph.patch",
                        "sha256": "0" * 64,
                    }
                ]
            )

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

    def test_registered_regressions_keep_declared_packages_and_exact_bytes(self):
        _copy_regressions(self.root)
        test_root = self.root / "forge-gui-desktop/src/test/java"
        self.assertEqual(REGRESSION_PACKAGES.keys(), REGRESSION_SOURCES.keys())
        expected = set()
        for name in REGRESSION_SOURCES:
            with self.subTest(source=name):
                original = PROJECT_ROOT / "engine/tests" / name
                declarations = [
                    line.removeprefix("package ").removesuffix(";")
                    for line in original.read_text(encoding="utf-8").splitlines()
                    if line.startswith("package ")
                ]
                self.assertEqual(len(declarations), 1)
                destination = Path(declarations[0].replace(".", "/")) / name
                expected.add(destination.as_posix())
                self.assertEqual(
                    (test_root / destination).read_bytes(), original.read_bytes()
                )
        self.assertEqual(
            {
                path.relative_to(test_root).as_posix()
                for path in test_root.rglob("*.java")
            },
            expected,
        )

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
