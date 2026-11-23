"""Opt-in fixed Battle combat cases against the operator-selected reviewed native JAR."""

import json
import os
import unittest

import pytest

from commander.engine.forge_config import read_settings
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.battle_combat_worker import (
    NATIVE_CLASSES,
    REVIEW_SOURCES,
    reviewed_inputs,
)
from tests.support.native_receipts import assert_rule_summary, read_test_receipts
from tests.support.native_worker import run_test_worker
from tests.support.packaged_rules import SUPPORT_SOURCES, compile_rules

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires the reviewed Battle combat runtime, local TestNG and JDK 17+",
)
class BattleCombatIntegrationTests(unittest.TestCase):
    """Require all fixed methods, native class origins and integrity checks to pass."""

    def test_packaged_battle_combat_rules(self):
        """Run the separate 110-case family within the unchanged 90-second worker budget."""
        settings = read_settings()
        run = create_run()
        compile_rules(settings, run, *reviewed_inputs())
        worker = PROJECT_ROOT / "tests/support/battle_combat_worker.py"
        process, _ = run_test_worker(
            settings,
            run,
            worker,
            "battle-combat-worker.log",
            SUPPORT_SOURCES + REVIEW_SOURCES,
        )
        self.assertEqual(process["returncode"], 0, process)
        rows = read_test_receipts(
            settings,
            run,
            "battle-combat.json",
            {
                "battle-combat-summary.json": 1,
                "battle-combat-methods.json": 110,
            },
        )
        summary, methods = rows[0], rows[1:]
        self.assertEqual(len({row["method"] for row in methods}), 110)
        for row in methods:
            with self.subTest(method=row["method"]):
                self.assertEqual(row["status"], "PASS")
        assert_rule_summary(self, summary, 110, NATIVE_CLASSES)
        print(json.dumps({"battle_combat_run": run.name}), flush=True)
