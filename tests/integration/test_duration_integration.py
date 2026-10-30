"""Opt-in paid duration and temporal-state checks against the pinned native runtime."""

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
    "Requires reviewed scheduled-effect runtime",
)
class DurationIntegrationTests(unittest.TestCase):
    """Resolved effects retain state and end at their actual copied boundaries."""

    def _assert_pump(self, row):
        self.assertEqual(row["before"], row["copied"])
        self.assertEqual(row["before"], row["source"])
        mass = row["case"] == "overrun"
        self.assertEqual(row["paid"], 5 if mass else 3)
        self.assertEqual(row["before"]["power"], 5 if mass else 3)
        self.assertEqual(row["after"]["power"], 2 if mass else 1)
        self.assertEqual(row["after"]["toughness"], 2 if mass else 1)
        self.assertFalse(row["after"]["trample"])

    def _assert_theft(self, row):
        self.assertEqual(row["paid"], 3)
        self.assertEqual(row["before"], row["copied"])
        self.assertEqual(row["before"], row["source"])
        self.assertTrue(row["before"]["haste"])
        self.assertNotEqual(row["before"]["controller"], row["original_controller"])
        self.assertEqual(row["after"]["controller"], row["original_controller"])
        self.assertEqual(row["after"]["zone_player"], row["original_controller"])
        self.assertFalse(row["after"]["haste"])

    def _assert_sower(self, row):
        self.assertEqual(row["paid"], [4, 1])
        self.assertEqual(row["before"], row["source"])
        self.assertNotEqual(row["before"]["controller"], row["original_controller"])
        self.assertEqual(row["after"]["controller"], row["original_controller"])
        self.assertEqual(row["after"]["zone_player"], row["original_controller"])
        self.assertEqual(row["copied_sower_zone"], "Graveyard")
        self.assertEqual(row["source_sower_zone"], "Battlefield")

    def _assert_round(self, row):
        self.assertEqual(row["paid"], 3)
        self.assertEqual(
            row["before_reuse"],
            {"turn": 0, "game": 1, "resolved": 0, "playable": True},
        )
        self.assertEqual(
            row["after_reuse"],
            {"turn": 1, "game": 2, "resolved": 1, "playable": False},
        )
        self.assertEqual(
            row["source"],
            {"turn": 1, "game": 1, "resolved": 1, "playable": False},
        )
        self.assertEqual(row["active_seat"], row["owner"])
        self.assertEqual(row["drawn_hand"], 1)

    def _assert_other(self, row):
        if row["case"] == "shroud":
            self.assertEqual(row["paid"], 2)
            self.assertTrue(row["before"])
            self.assertTrue(row["copied"])
            self.assertFalse(row["after"])
            self.assertTrue(row["source"])
        elif row["case"] == "untap":
            self.assertEqual(row["paid"], [2, 2, 2])
            self.assertEqual(row["before"], 5)
            self.assertEqual(row["after"], 2)
            self.assertEqual(row["source"], 5)
            self.assertFalse(row["copied_tapped"])
            self.assertTrue(row["source_tapped"])
        elif row["case"] == "number":
            self.assertEqual(row["paid"], 3)
            self.assertIsNotNone(row["before"]["number"])
            self.assertGreaterEqual(row["before"]["number"], 0)
            self.assertEqual(row["before"], row["copied"])
            self.assertEqual(row["before"], row["source"])
            self.assertEqual(row["copied"]["visible"], str(row["copied"]["number"]))
        else:
            self._assert_round(row)

    def test_paid_effect_expirations(self):
        """Pay native costs, copy resolved states and run real phase/event boundaries."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("scheduled_snapshot_state", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/duration_worker.py",
            "duration.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "duration-state.json")
        self.assertEqual(receipt["engine"], expected)
        self.assertEqual(
            [row["case"] for row in receipt["cases"]],
            [
                "rootwalla",
                "overrun",
                "theft",
                "sower",
                "shroud",
                "untap",
                "number",
                "round",
            ],
        )
        for row in receipt["cases"]:
            with self.subTest(case=row["case"]):
                self.assertEqual(row["unmapped"], [])
                for key in ("before", "copied", "after", "source"):
                    shape = row.get(key)
                    if isinstance(shape, dict) and "controller_world" in shape:
                        self.assertTrue(shape["controller_world"])
                        self.assertTrue(shape["zone_world"])
                if row["case"] in {"rootwalla", "overrun"}:
                    self._assert_pump(row)
                elif row["case"] == "theft":
                    self._assert_theft(row)
                elif row["case"] == "sower":
                    self._assert_sower(row)
                else:
                    self._assert_other(row)
