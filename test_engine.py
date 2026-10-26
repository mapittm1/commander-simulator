"""Engine boundary regressions: failures must never become simulated wins."""

import argparse
import json
import os
import sys
import tempfile
import unittest
import uuid
import zipfile
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

from engine_commands import parse_seat
from engine_process import MAX_LOG_BYTES, ProcessLimits, run_bounded
from forge_config import EngineSettings, file_hash, read_settings
from forge_decks import card_index, serialize_deck
from forge_engine import _engine_profile, _environment, prepare_seats, run_game
from forge_results import parse_game_result

# Behavior is expressed in test names.
# pylint: disable=missing-function-docstring

WIN = (
    "Game Outcome: Ai(2)-seat-2 has won because all opponents have lost\n"
    "Game Result: Game 1 ended in 123 ms. Ai(2)-seat-2 has won!\n"
)
FINISHED = {"status": "finished", "returncode": 0}


class EngineResultTests(unittest.TestCase):
    """Corroborate results and distinguish failure from genuine outcomes."""

    def test_corroborated_win_and_draw(self):
        self.assertEqual(parse_game_result(WIN, FINISHED)["winner_seat"], 2)
        draw = "Game Outcome: Draw\nGame Result: Game 1 ended in a Draw! Took 7 ms."
        self.assertEqual(parse_game_result(draw, FINISHED)["outcome"], "draw")
        self.assertEqual(
            parse_game_result("Stopping slow match as draw\n" + draw, FINISHED)[
                "status"
            ],
            "engine_timeout",
        )

    def test_failures_never_count_as_wins(self):
        cases = [
            (WIN, {"status": "timeout", "returncode": -1}, "timeout"),
            (WIN, {"status": "log_limit", "returncode": 0}, "log_limit"),
            (WIN, {"status": "finished", "returncode": 1}, "engine_error"),
            (WIN + "\tat forge.Game.run(Game.java:45)\n", FINISHED, "engine_error"),
            (WIN + WIN, FINISHED, "unrecognized_result"),
            (WIN.replace("Game Outcome:", "Ignored:"), FINISHED, "unrecognized_result"),
            (
                WIN.replace("Outcome: Ai(2)", "Outcome: Ai(3)"),
                FINISHED,
                "unrecognized_result",
            ),
            ("", FINISHED, "unrecognized_result"),
        ]
        for log, process, status in cases:
            with self.subTest(status=status, log=log):
                result = parse_game_result(log, process)
                self.assertEqual(result["status"], status)
                self.assertIsNone(result["winner_seat"])


class EngineBoundaryTests(unittest.TestCase):
    """Exercise real bounded subprocesses and temporary trusted engine fixtures."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.root = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        self.settings = EngineSettings(self.root, self.root / "java.exe")

    def _process(self, program, limits):
        return run_bounded(
            [sys.executable, "-c", program],
            self.root,
            self.root / "process.log",
            limits,
        )

    def test_process_output_and_existing_log_preservation(self):
        result = self._process("print('safe')", ProcessLimits(10))
        self.assertEqual(result["returncode"], 0)
        self.assertEqual((self.root / "process.log").read_text().strip(), "safe")
        with self.assertRaises(FileExistsError):
            self._process("print('overwrite')", ProcessLimits(10))

    def test_log_limit_kills_verbose_process_and_bounds_disk_use(self):
        result = self._process(
            "import sys; sys.stdout.write('x' * 100000)", ProcessLimits(10, 1024)
        )
        self.assertEqual(result["status"], "log_limit")
        self.assertEqual((self.root / "process.log").stat().st_size, 1024)

    def test_wall_timeout_terminates_process(self):
        result = self._process("import time; time.sleep(30)", ProcessLimits(0.1))
        self.assertEqual(result["status"], "timeout")
        self.assertIsNotNone(result["returncode"])

    def test_invalid_limits_rejected_before_process_creation(self):
        for limits in (
            ProcessLimits(True),
            ProcessLimits(float("nan")),
            ProcessLimits(601),
            ProcessLimits(10, MAX_LOG_BYTES + 1),
        ):
            with self.subTest(limits=limits), self.assertRaises(ValueError):
                self._process("raise RuntimeError()", limits)
        self.assertFalse((self.root / "process.log").exists())

    def test_profile_restored_after_failure_and_lock_excludes_second_writer(self):
        profile = self.root / "forge.profile.properties"
        original = b"original profile\r\n"
        profile.write_bytes(original)
        with self.assertRaisesRegex(RuntimeError, "test failure"):
            with _engine_profile(self.settings, self.root):
                self.assertNotEqual(profile.read_bytes(), original)
                with self.assertRaises(FileExistsError):
                    with _engine_profile(self.settings, self.root):
                        self.fail("Second engine writer acquired the lock")
                raise RuntimeError("test failure")
        self.assertEqual(profile.read_bytes(), original)
        self.assertFalse((self.root / ".commander-simulator.lock").exists())

    def test_absent_profile_removed_after_run(self):
        with _engine_profile(self.settings, self.root):
            self.assertTrue((self.root / "forge.profile.properties").exists())
        self.assertFalse((self.root / "forge.profile.properties").exists())

    def test_child_environment_excludes_credentials_and_java_injection(self):
        with patch.dict(
            os.environ,
            {
                "GITHUB_TOKEN": uuid.uuid4().hex,
                "JAVA_TOOL_OPTIONS": "bad",
                "_JAVA_OPTIONS": "bad",
                "JDK_JAVA_OPTIONS": "bad",
            },
        ):
            environment = _environment(self.root)
        self.assertFalse(
            {
                "GITHUB_TOKEN",
                "JAVA_TOOL_OPTIONS",
                "_JAVA_OPTIONS",
                "JDK_JAVA_OPTIONS",
            }.intersection(environment)
        )
        self.assertEqual(environment["TEMP"], str(self.root / "temp"))

    def test_pinned_resources_reject_tampering(self):
        self.settings.java.touch()
        jar = self.root / "fixture.jar"
        jar.write_bytes(b"trusted fixture")
        cards = self.root / "res/cardsfolder/cardsfolder.zip"
        cards.parent.mkdir(parents=True)
        cards.write_bytes(b"trusted cards")
        lock = self.root / "lock.json"
        lock.write_text(
            json.dumps(
                {
                    "jar_name": jar.name,
                    "jar_sha256": file_hash(jar),
                    "cards_sha256": file_hash(cards),
                }
            )
        )
        with patch("forge_config.LOCK_PATH", lock):
            self.settings.verify()
            jar.write_bytes(b"modified")
            with self.assertRaisesRegex(ValueError, "integrity"):
                self.settings.verify()

    def test_config_rejects_unknown_fields_and_oversized_input(self):
        config = self.root / "config.json"
        for text in ('{"home":".","java":"java.exe","command":"bad"}', "x" * 4097):
            config.write_text(text)
            with self.assertRaises(ValueError):
                read_settings(config)

    def test_unsupported_cards_block_before_process_creation(self):
        revision = {"id": 1, "cards": [{"card": {"name": "Missing"}}]}
        with (
            patch("forge_engine.prepare_seats", return_value=[{"revision": revision}]),
            patch.object(EngineSettings, "verify", return_value={}),
            patch("forge_engine.card_index", return_value={}),
            patch("forge_engine.run_bounded") as process,
        ):
            with self.assertRaisesRegex(ValueError, "coverage"):
                run_game(None, self.settings, [(1, 1)] * 4)
            process.assert_not_called()

    def test_explicit_seats_and_numeric_limits(self):
        self.assertEqual(parse_seat("12:3"), (12, 3))
        for text in (
            "1",
            "0:1",
            "1:-1",
            "1:2:3",
            "１:1",
            "9" * 5000 + ":1",
            f"{2**63}:1",
        ):
            with self.assertRaises(argparse.ArgumentTypeError):
                parse_seat(text)
        with self.assertRaises(ValueError):
            prepare_seats(None, [(1, 1)])
        for seats in (None, [None] * 4, [(True, 1)] * 4, [(2**63, 1)] * 4):
            with self.assertRaises(ValueError):
                prepare_seats(None, seats)
        for seed, clock in ((True, 1), (-1, 1), (2**63, 1), (1, 301), (1, True)):
            with self.assertRaises(ValueError):
                run_game(None, self.settings, [], seed, clock)

    def test_script_aliases_and_fail_closed_serialization(self):
        archive_path = self.root / "cards.zip"
        with zipfile.ZipFile(archive_path, "w") as archive:
            archive.writestr(
                "fire.txt", "Name:Fire\nAlternateMode:Split\nALTERNATE\nName:Ice\n"
            )
            archive.writestr(
                "front.txt",
                "Name:Front\nAlternateMode:DoubleFaced\nALTERNATE\nName:Back\n",
            )
        index = card_index(archive_path)
        self.assertEqual(index["Fire // Ice"], "Fire // Ice")
        self.assertEqual(index["Front // Back"], "Front")
        revision = {
            "id": 1,
            "cards": [
                {"zone": "mainboard", "quantity": 1, "card": {"name": "Fire // Ice"}}
            ],
        }
        self.assertIn("1 Fire // Ice", serialize_deck(revision, index, 1))
        with self.assertRaises(ValueError):
            serialize_deck(revision, {}, 1)
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            serialize_deck(revision, {"Fire // Ice": "Fire\n[metadata]"}, 1)

    def test_explicit_universes_within_aliases_preserve_original_script_export(self):
        archive_path = self.root / "variants.zip"
        with zipfile.ZipFile(archive_path, "w") as archive:
            archive.writestr(
                "one.txt", "Name:Original\nVariant:UniversesWithin:FlavorName:Renamed\n"
            )
            archive.writestr(
                "two.txt",
                "Name:Old Front\nVariant:UniversesWithin:FlavorName:New Front\n"
                "AlternateMode:DoubleFaced\nALTERNATE\nName:Old Back\n"
                "Variant:UniversesWithin:FlavorName:New Back\n",
            )
            archive.writestr(
                "cosmetic.txt",
                "Name:Base\nVariant:Cosmetic:FlavorName:Unverified Alias\n",
            )
        index = card_index(archive_path)
        self.assertEqual(index["Renamed"], "Original")
        self.assertEqual(index["New Front // New Back"], "Old Front")
        self.assertNotIn("New Back", index)
        self.assertNotIn("Unverified Alias", index)

    def test_conflicting_variant_aliases_fail_closed(self):
        archive_path = self.root / "conflicting.zip"
        with zipfile.ZipFile(archive_path, "w") as archive:
            archive.writestr(
                "first.txt", "Name:First\nVariant:UniversesWithin:FlavorName:Shared\n"
            )
            archive.writestr(
                "second.txt", "Name:Second\nVariant:UniversesWithin:FlavorName:Shared\n"
            )
        with self.assertRaisesRegex(ValueError, "Ambiguous"):
            card_index(archive_path)
