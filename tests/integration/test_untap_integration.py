"""Opt-in fixed untap cases against the operator-selected reviewed native JAR."""

import json
import os
import unittest

import pytest

from commander.engine.forge_config import read_settings
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.native_receipts import assert_rule_summary, read_test_receipts
from tests.support.native_worker import run_test_worker
from tests.support.packaged_rules import SUPPORT_SOURCES, compile_rules
from tests.support.untap_worker import NATIVE_CLASSES, reviewed_inputs

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires the reviewed untap runtime, local TestNG and JDK 17+",
)
class UntapIntegrationTests(unittest.TestCase):
    """Require all fixed methods, native class origins and integrity checks to pass."""

    def test_packaged_untap_rules(self):
        """Run the separate 98-case family within the unchanged 90-second worker budget."""
        settings = read_settings()
        run = create_run()
        compile_rules(settings, run, *reviewed_inputs())
        worker = PROJECT_ROOT / "tests/support/untap_worker.py"
        process, _ = run_test_worker(
            settings, run, worker, "untap-worker.log", SUPPORT_SOURCES
        )
        self.assertEqual(process["returncode"], 0, process)
        rows = read_test_receipts(
            settings,
            run,
            "untap.json",
            {
                "untap-summary.json": 1,
                "untap-methods.json": 98,
            },
        )
        summary, methods = rows[0], rows[1:]
        self.assertEqual(len({row["method"] for row in methods}), 98)
        for row in methods:
            with self.subTest(method=row["method"]):
                self.assertEqual(row["status"], "PASS")
        assert_rule_summary(self, summary, 98, NATIVE_CLASSES)
        print(json.dumps({"untap_run": run.name}), flush=True)
