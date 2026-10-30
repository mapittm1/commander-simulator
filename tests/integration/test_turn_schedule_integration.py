"""Opt-in paid turn/phase scheduling checks for the pinned native runtime."""

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
    "Requires reviewed turn-schedule runtime",
)
class TurnScheduleIntegrationTests(unittest.TestCase):
    """Keep queued recipients, ordering, phase hooks and attached obligations."""

    def _assert_turns(self, row):
        case = row["case"]
        expected = {
            "warp": [(2, True, 0), (1, False, 0)],
            "stretch": [(0, True, 1), (0, True, 0), (1, False, 0)],
            "ordered": [(2, True, 0), (1, True, 0), (1, False, 0)],
        }
        self.assertEqual(
            [(turn["seat"], turn["extra"], turn["remaining"]) for turn in row["turns"]],
            expected[case],
        )
        self.assertEqual(
            row["paid"], {"warp": [5], "stretch": [10], "ordered": [5, 5]}[case]
        )
        self.assertEqual(row["before_remaining"], 2 if case == "stretch" else 0)
        self.assertEqual(row["source_remaining"], 2 if case == "stretch" else 0)
        self.assertEqual(row["source_next"], 0 if case == "stretch" else 2)
        self.assertEqual(row["source_turn"], 1)

    def _assert_special_turn(self, row):
        case = row["case"]
        if case == "mid_extra_turn":
            self.assertEqual(row["paid"], 3)
            self.assertEqual(row["source"], {"seat": 0, "extra": True, "remaining": 0})
            self.assertEqual(row["copied"], row["source"])
            self.assertEqual(row["source_upkeeps"], 1)
            self.assertEqual(row["copied_upkeeps"], 1)
            self.assertEqual(row["source_previous"], 0)
            self.assertEqual(row["copied_previous"], 0)
            return
        self.assertEqual(row["first"], {"seat": 0, "extra": True, "remaining": 0})
        if case == "savor":
            self.assertEqual(row["paid"], 3)
            self.assertEqual(row["tapped_extra"], 3)
            self.assertEqual(row["later"], {"seat": 0, "extra": False, "remaining": 0})
            self.assertEqual(row["tapped_later"], 0)
            self.assertEqual(row["source_tapped"], 3)
        else:
            self.assertEqual(row["paid"], 2)
            self.assertTrue(row["owner_lost"])
            self.assertEqual(row["active_seats"], 3)
            self.assertFalse(row["source_lost"])

    def _assert_combat(self, row):
        if row["case"] == "moraug":
            self.assertEqual(row["paid"], 6)
            self.assertTrue(row["untapped"])
            self.assertTrue(row["source_tapped"])
            self.assertEqual(row["combats"], 2)
            return
        multiple = row["case"] == "multiple_assaults"
        self.assertEqual(row["paid"], [4, 4] if multiple else [4])
        self.assertTrue(row["scheduled"])
        self.assertEqual(row["combats"], 3 if multiple else 2)
        self.assertEqual(row["mains"], 3 if multiple else 2)
        self.assertEqual(row["source_combats"], 0)
        self.assertTrue(row["source_scheduled"])

    def test_paid_turn_and_phase_schedules(self):
        """Resolve paid actions before copying, then execute only the copied schedule."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("turn_schedule_snapshot", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/turn_schedule_worker.py",
            "turn-schedule.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "turn-schedule.json")
        self.assertEqual(receipt["engine"], expected)
        self.assertEqual(
            [row["case"] for row in receipt["cases"]],
            [
                "warp",
                "stretch",
                "ordered",
                "savor",
                "fortune",
                "assault",
                "multiple_assaults",
                "mid_extra_turn",
                "moraug",
            ],
        )
        for row in receipt["cases"]:
            with self.subTest(case=row["case"]):
                self.assertEqual(row["unmapped"], [])
                if row["case"] in {"warp", "stretch", "ordered"}:
                    self._assert_turns(row)
                elif row["case"] in {"savor", "fortune", "mid_extra_turn"}:
                    self._assert_special_turn(row)
                else:
                    self._assert_combat(row)
