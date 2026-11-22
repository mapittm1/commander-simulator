"""Opt-in fixed free-casting cases against the operator-selected reviewed native JAR."""

import json
import os
import unittest

import pytest

from commander.engine.forge_config import read_settings
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.free_casting_worker import NATIVE_CLASSES, reviewed_inputs
from tests.support.native_receipts import read_test_receipts
from tests.support.native_worker import run_test_worker
from tests.support.packaged_rules import SUPPORT_SOURCES, compile_rules

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires the reviewed free-casting runtime, local TestNG and JDK 17+",
)
class FreeCastingIntegrationTests(unittest.TestCase):
    """Require all fixed methods, native class origins and integrity checks to pass."""

    def test_packaged_free_casting_rules(self):
        """Run the separate 26-case family within the unchanged 90-second worker budget."""
        settings = read_settings()
        run = create_run()
        compile_rules(settings, run, *reviewed_inputs())
        worker = PROJECT_ROOT / "tests/support/free_casting_worker.py"
        process, _ = run_test_worker(
            settings, run, worker, "free-casting-worker.log", SUPPORT_SOURCES
        )
        self.assertEqual(process["returncode"], 0, process)
        rows = read_test_receipts(
            settings,
            run,
            "free-casting.json",
            {
                "free-casting-summary.json": 1,
                "free-casting-methods.json": 26,
            },
        )
        summary, methods = rows[0], rows[1:]
        self.assertEqual(len({row["method"] for row in methods}), 26)
        for row in methods:
            with self.subTest(method=row["method"]):
                self.assertEqual(row["status"], "PASS")
        self.assertEqual(
            summary["counts"], {"total": 26, "passed": 26, "failed": 0, "skipped": 0}
        )
        for key in (
            "testng_status",
            "configuration_failures",
            "configuration_skips",
            "partial_successes",
        ):
            self.assertEqual(summary[key], 0)
        self.assertEqual(
            set(summary["production_classes_from_packaged_jar"]), set(NATIVE_CLASSES)
        )
        print(json.dumps({"free_casting_run": run.name}), flush=True)
