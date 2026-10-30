"""Opt-in paid conditional-control and effect-cleanup checks."""

import os
import unittest

import pytest

from commander.engine.forge_config import read_settings
from commander.engine.interactions.suite import read_bounded_json
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires reviewed conditional-cleanup runtime",
)
class ConditionalIntegrationTests(unittest.TestCase):
    """Evaluate real rule conditions and deadlines in an independent copied game."""

    def _assert_old_man(self, row):
        untap = row["case"] == "old_man_untap"
        self.assertEqual(row["paid"], [3, 0 if untap else 1])
        self.assertEqual(row["copied_watchers"], 1)
        self.assertEqual(row["after_controller"], 1)
        self.assertEqual(row["zone_player"], 1)
        self.assertEqual(row["target_power"], 5 if row["case"] == "old_man_grow" else 2)
        self.assertEqual(
            row["source_power"], 0 if row["case"] == "old_man_shrink" else 2
        )
        self.assertEqual(row["source_controller"], 0)
        self.assertTrue(row["source_tapped"])

    def _assert_matriarch(self, row):
        cloudshift = row["case"] == "matriarch_cloudshift"
        disenchant = row["case"] == "matriarch_disenchant"
        self.assertEqual(row["paid"], [4, 1, 2 if disenchant else 1])
        # Cloudshift creates new control for the caster; Ephemerate returns to the owner.
        self.assertEqual(row["after_controller"], 0 if cloudshift else 1)
        self.assertFalse(row["after_enchanted"])
        self.assertEqual(row["source_controller"], 0)
        self.assertTrue(row["source_enchanted"])

    def _assert_effect(self, row):
        if row["case"] == "silence":
            self.assertEqual(row["paid"], [1, 1])
            self.assertTrue(row["before_restricted"])
            self.assertTrue(row["before_denied"])
            self.assertTrue(row["after_allowed"])
            self.assertEqual(row["copied_life"], 37)
            self.assertEqual(row["source_life"], 40)
            self.assertTrue(row["source_restricted"])
        else:
            self.assertEqual(row["paid"], 2)
            self.assertFalse(row["can_target_before"])
            self.assertFalse(row["can_target_middle"])
            self.assertTrue(row["can_target_after"])
            self.assertFalse(row["source_can_target"])

    def test_paid_conditions_and_effect_deadlines(self):
        """Resolve paid actions, then change only the copied conditions or deadlines."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("conditional_cleanup_snapshot", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/conditional_worker.py",
            "conditional.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "conditional-state.json")
        self.assertEqual(receipt["engine"], expected)
        self.assertEqual(
            [row["case"] for row in receipt["cases"]],
            [
                "old_man_grow",
                "old_man_shrink",
                "old_man_untap",
                "matriarch_disenchant",
                "matriarch_ephemerate",
                "matriarch_cloudshift",
                "peace_deferred",
                "peace_registered",
                "silence",
            ],
        )
        for row in receipt["cases"]:
            with self.subTest(case=row["case"]):
                self.assertEqual(row["unmapped"], [])
                if row["case"].startswith("old_man_"):
                    self._assert_old_man(row)
                elif row["case"].startswith("matriarch_"):
                    self._assert_matriarch(row)
                else:
                    self._assert_effect(row)
