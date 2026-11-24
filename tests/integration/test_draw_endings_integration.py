"""Run fixed draw and mana rules fixtures against the packaged production JAR."""

import json
import os
import unittest

import pytest

from commander.engine.forge_config import read_settings
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.draw_ending_worker import NATIVE_CLASSES, _compile, _inputs
from tests.support.native_receipts import read_test_receipts
from tests.support.native_worker import run_test_worker
from tests.support.packaged_rules import SUPPORT_SOURCES

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires reviewed draw-ending runtime, pinned local TestNG and JDK 17+",
)
class DrawEndingIntegrationTests(unittest.TestCase):
    """Exercise draw triggers, paid casts, copied games and mana-source planning."""

    def test_packaged_draw_ending_rules(self):
        """Require every fixed rules method to pass without skips or configuration failures."""
        settings = read_settings()
        run = create_run()
        _compile(settings, run, *_inputs())
        worker = PROJECT_ROOT / "tests/support/draw_ending_worker.py"
        process, _ = run_test_worker(
            settings, run, worker, "draw-ending-worker.log", SUPPORT_SOURCES
        )
        self.assertEqual(process["returncode"], 0, process)
        rows = read_test_receipts(
            settings,
            run,
            "draw-endings.json",
            {"draw-ending-summary.json": 1, "draw-ending-methods.json": 1477},
        )
        summary, methods = rows[0], rows[1:]
        self.assertEqual(len({row["method"] for row in methods}), 1477)
        for row in methods:
            with self.subTest(method=row["method"]):
                self.assertEqual(row["status"], "PASS")
        self.assertEqual(
            summary["counts"],
            {"total": 1477, "passed": 1477, "failed": 0, "skipped": 0},
        )
        self.assertEqual(summary["testng_status"], 0)
        for key in (
            "configuration_failures",
            "configuration_skips",
            "partial_successes",
        ):
            self.assertEqual(summary[key], 0)
        expected_classes = list(NATIVE_CLASSES)
        self.assertEqual(len(expected_classes), 133)
        self.assertEqual(len(set(expected_classes)), len(expected_classes))
        self.assertEqual(
            summary["production_classes_from_packaged_jar"], expected_classes
        )
        print(json.dumps({"draw_ending_run": run.name}), flush=True)
