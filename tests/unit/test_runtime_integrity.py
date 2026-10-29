"""Reviewed runtime bytes and recipe identity cannot change behind a pinned build."""

import hashlib
import json
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

from commander.engine.forge_config import EngineSettings, file_hash, read_engine_lock
from commander.engine.printing_resources import edition_fingerprint
from tools.build_sticker_engine import _verify_unchanged_inputs

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class RuntimeIntegrityTests(unittest.TestCase):
    """Optional runtime pins are enforced when present; legacy resource pins remain valid."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.root = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        self.java = self.root / "jre/bin/java.exe"
        self.jvm = self.root / "jre/bin/server/jvm.dll"
        self.jvm.parent.mkdir(parents=True)
        self.java.write_bytes(b"trusted-java-executable")
        self.jvm.write_bytes(b"trusted-jvm-library")
        jar = self.root / "native.jar"
        cards = self.root / "res/cardsfolder/cardsfolder.zip"
        cards.parent.mkdir(parents=True)
        jar.write_bytes(b"trusted-native-jar")
        cards.write_bytes(b"trusted-card-archive")
        self.lock = self.root / "lock.json"
        self.metadata = {
            "jar_name": jar.name,
            "jar_sha256": file_hash(jar),
            "cards_sha256": file_hash(cards),
            "runtime_executable_sha256": file_hash(self.java),
            "runtime_jvm_sha256": file_hash(self.jvm),
        }
        self.lock.write_text(json.dumps(self.metadata), encoding="utf-8")
        self.settings = EngineSettings(self.root, self.java, self.lock)

    def test_changed_java_executable_or_jvm_library_is_rejected(self):
        self.assertEqual(self.settings.verify(), self.metadata)
        for path in (self.java, self.jvm):
            original = path.read_bytes()
            path.write_bytes(b"changed-runtime")
            with (
                self.subTest(path=path.name),
                self.assertRaisesRegex(ValueError, "integrity"),
            ):
                self.settings.verify()
            path.write_bytes(original)

    def test_malformed_optional_runtime_hashes_are_rejected(self):
        for field in ("runtime_executable_sha256", "runtime_jvm_sha256"):
            self.lock.write_text(
                json.dumps({**self.metadata, field: "not-a-sha256"}), encoding="utf-8"
            )
            with self.subTest(field=field), self.assertRaises(ValueError):
                read_engine_lock(self.lock)

    def test_changed_recipe_cannot_be_stamped_as_the_original_build(self):
        recipe = self.root / "engine/sticker_recipe.json"
        recipe.parent.mkdir()
        original = b'{"schema_version":1}\n'
        recipe.write_bytes(original)
        digest = hashlib.sha256(original).hexdigest()
        with (
            patch("tools.build_sticker_engine.PROJECT", self.root),
            patch("tools.build_sticker_engine._verify_inputs") as verify,
        ):
            _verify_unchanged_inputs(None, {}, digest)
            verify.assert_called_once()
            recipe.write_bytes(b'{"schema_version":2}\n')
            with self.assertRaisesRegex(ValueError, "recipe changed"):
                _verify_unchanged_inputs(None, {}, digest)
            verify.assert_called_once()

    def test_changed_or_added_edition_resources_cannot_pass_a_pinned_engine(self):
        folder = self.root / "res/editions"
        folder.mkdir()
        edition = folder / "unfinity.txt"
        edition.write_text("reviewed edition metadata", encoding="utf-8")
        self.metadata["edition_resources"] = edition_fingerprint(self.root)
        self.lock.write_text(json.dumps(self.metadata), encoding="utf-8")
        self.settings.verify()
        edition.write_text("changed metadata", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "edition resource integrity"):
            self.settings.verify()
        edition.write_text("reviewed edition metadata", encoding="utf-8")
        (folder / "unexpected.txt").write_text("extra edition", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "edition resource integrity"):
            self.settings.verify()

    def test_malformed_edition_inventory_pins_fail_closed(self):
        for value in (
            {},
            {"files": True, "sha256": "a" * 64},
            {"files": 4097, "sha256": "a" * 64},
            {"files": 1, "sha256": "wrong"},
        ):
            self.lock.write_text(
                json.dumps({**self.metadata, "edition_resources": value}),
                encoding="utf-8",
            )
            with self.subTest(pin=value), self.assertRaises(ValueError):
                read_engine_lock(self.lock)
