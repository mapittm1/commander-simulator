"""Compile only fixed source and reject failed, changed, or excessive native artifacts."""

import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import Mock, patch

from commander.engine.interactions.native_bridge import (
    CLASS_RELATIVE,
    SOURCE,
    bridge_unchanged,
    build_native_bridge,
)

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class NativeBridgeTests(unittest.TestCase):
    """Exercise compilation admission without requiring a JDK in offline tests."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.root = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        self.compiler = self.root / "javac.exe"
        self.compiler.write_bytes(b"trusted local compiler fixture")
        self.settings = Mock(home=self.root)
        self.settings.verify.return_value = {"jar_name": "pinned.jar"}
        stack.enter_context(
            patch.dict("os.environ", {"COMMANDER_JAVAC": str(self.compiler)})
        )

    def _execute(self, _command, run, log, _limits):
        path = run / CLASS_RELATIVE
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"compiled test fixture")
        log.write_text("javac 19.0.1\n", encoding="utf-8")
        return {"status": "finished", "returncode": 0}

    def test_fixed_source_java17_no_processors_bounded_argv_and_recorded_hashes(self):
        with patch(
            "commander.engine.interactions.native_bridge.run_bounded",
            side_effect=self._execute,
        ) as execute:
            evidence = build_native_bridge(self.settings, self.root, {})
        command, run, _log, limits = execute.call_args.args
        self.assertEqual(command[0], str(self.compiler))
        self.assertEqual(command[-1], str(SOURCE))
        self.assertIn("-proc:none", command)
        self.assertEqual(command[command.index("--release") + 1], "17")
        self.assertEqual(limits.timeout, 30)
        self.assertEqual(limits.log_bytes, 65536)
        self.assertEqual(run, self.root)
        self.assertTrue(bridge_unchanged(self.root, evidence))
        (self.root / CLASS_RELATIVE).write_bytes(b"changed class")
        self.assertFalse(bridge_unchanged(self.root, evidence))

    def test_missing_compiler_is_actionable(self):
        with (
            patch.dict("os.environ", {}, clear=True),
            patch(
                "commander.engine.interactions.native_bridge.shutil.which",
                return_value=None,
            ),
            self.assertRaisesRegex(ValueError, "JDK 17"),
        ):
            build_native_bridge(self.settings, self.root, {})

    def test_failed_compilation_never_produces_accepted_evidence(self):
        def execute(*args):
            self._execute(*args)
            return {"status": "timeout", "returncode": 0}

        with (
            patch(
                "commander.engine.interactions.native_bridge.run_bounded",
                side_effect=execute,
            ),
            self.assertRaisesRegex(ValueError, "compilation failed"),
        ):
            build_native_bridge(self.settings, self.root, {})

    def test_changed_compiler_is_rejected(self):
        def execute(*args):
            result = self._execute(*args)
            self.compiler.write_bytes(b"changed compiler")
            return result

        with (
            patch(
                "commander.engine.interactions.native_bridge.run_bounded",
                side_effect=execute,
            ),
            self.assertRaisesRegex(ValueError, "compiler changed"),
        ):
            build_native_bridge(self.settings, self.root, {})

    def test_old_compiler_version_is_rejected(self):
        def execute(command, run, log, limits):
            result = self._execute(command, run, log, limits)
            log.write_text("javac 16.0.2\n", encoding="utf-8")
            return result

        with (
            patch(
                "commander.engine.interactions.native_bridge.run_bounded",
                side_effect=execute,
            ),
            self.assertRaisesRegex(ValueError, "compilation failed"),
        ):
            build_native_bridge(self.settings, self.root, {})

    def test_oversized_class_is_rejected(self):
        def execute(command, run, log, limits):
            result = self._execute(command, run, log, limits)
            (run / CLASS_RELATIVE).write_bytes(b"x" * 65537)
            return result

        with (
            patch(
                "commander.engine.interactions.native_bridge.run_bounded",
                side_effect=execute,
            ),
            self.assertRaisesRegex(ValueError, "size limit"),
        ):
            build_native_bridge(self.settings, self.root, {})
