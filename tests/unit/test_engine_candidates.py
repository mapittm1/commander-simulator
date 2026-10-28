"""Alternate engine locks remain bounded, explicit and tied to exact resource bytes."""

import json
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path

from commander.engine.forge_config import (
    EngineSettings,
    file_hash,
    read_engine_lock,
    read_settings,
)

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class CandidateEngineTests(unittest.TestCase):
    """A separately configured candidate cannot weaken either executable/resource pin."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.root = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        self.java = self.root / "java.exe"
        self.java.write_bytes(b"trusted runtime fixture")
        self.jar = self.root / "fixture.jar"
        self.jar.write_bytes(b"candidate engine fixture")
        self.cards = self.root / "res/cardsfolder/cardsfolder.zip"
        self.cards.parent.mkdir(parents=True)
        self.cards.write_bytes(b"candidate card fixture")
        self.lock = self.root / "candidate-lock.json"
        self.metadata = {
            "jar_name": self.jar.name,
            "jar_sha256": file_hash(self.jar),
            "cards_sha256": file_hash(self.cards),
        }
        self._write(self.metadata)

    def _write(self, value):
        self.lock.write_text(json.dumps(value), encoding="utf-8")

    def test_candidate_lock_is_selected_explicitly_by_relative_admin_config(self):
        config = self.root / "candidate.json"
        config.write_text(
            json.dumps({"home": ".", "java": "java.exe", "lock": self.lock.name}),
            encoding="utf-8",
        )
        settings = read_settings(config)
        self.assertEqual(settings.lock_path, self.lock)
        self.assertEqual(settings.verify(), self.metadata)

    def test_candidate_resource_tampering_is_rejected(self):
        settings = EngineSettings(self.root, self.java, self.lock)
        for path in (self.jar, self.cards):
            before = path.read_bytes()
            path.write_bytes(b"tampered")
            with (
                self.subTest(path=path.name),
                self.assertRaisesRegex(ValueError, "integrity"),
            ):
                settings.verify()
            path.write_bytes(before)

    def test_lock_cannot_choose_paths_outside_the_engine_home(self):
        for value in (
            "../outside.jar",
            "folder/tool.jar",
            r"folder\tool.jar",
            "C:/outside.jar",
            "fixture.jar:stream",
            "fixture.exe",
            "x" * 201 + ".jar",
        ):
            self._write({**self.metadata, "jar_name": value})
            with (
                self.subTest(name=value),
                self.assertRaisesRegex(ValueError, "filename"),
            ):
                read_engine_lock(self.lock)

    def test_lock_size_depth_duplicates_and_hash_types_are_bounded(self):
        for raw in (
            b"x" * 65537,
            b"[]",
            b'{"jar_name":"a.jar","jar_name":"b.jar"}',
            b'{"nested":' + b"[" * 17 + b"0" + b"]" * 17 + b"}",
        ):
            self.lock.write_bytes(raw)
            with self.subTest(size=len(raw)), self.assertRaises(ValueError):
                read_engine_lock(self.lock)
        for value in (True, None, "a" * 63, "G" * 64, "A" * 64):
            for key in ("jar_sha256", "cards_sha256"):
                self._write({**self.metadata, key: value})
                with (
                    self.subTest(field=key, value=value),
                    self.assertRaises(ValueError),
                ):
                    read_engine_lock(self.lock)

    def test_unknown_config_fields_remain_rejected(self):
        config = self.root / "candidate.json"
        config.write_text(
            json.dumps(
                {
                    "home": ".",
                    "java": "java.exe",
                    "lock": self.lock.name,
                    "command": "untrusted",
                }
            ),
            encoding="utf-8",
        )
        with self.assertRaises(ValueError):
            read_settings(config)

    def test_unpinned_loose_card_scripts_cannot_override_the_archive(self):
        (self.cards.parent / "override.txt").write_text(
            "Name:Injected", encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "loose scripts"):
            EngineSettings(self.root, self.java, self.lock).verify()
