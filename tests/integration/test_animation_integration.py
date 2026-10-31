"""Opt-in paid animation and Licid reversion checks for the pinned runtime."""

import os
import unittest

import pytest

from commander.engine.forge_config import read_settings
from commander.engine.interactions.suite import read_bounded_json
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration

LICID_COSTS = {
    "Calming Licid": [3, 1, 1],
    "Convulsing Licid": [3, 1, 1],
    "Corrupting Licid": [3, 1, 1],
    "Dominating Licid": [3, 3, 1],
    "Enraging Licid": [2, 1, 1],
    "Flanking Licid": [2, 1, 1],
    "Gliding Licid": [3, 1, 1],
    "Leeching Licid": [2, 1, 1],
    "Nurturing Licid": [2, 1, 1],
    "Quickening Licid": [2, 2, 1],
    "Stinging Licid": [2, 2, 1],
    "Tempting Licid": [3, 1, 1],
    "Transmogrifying Licid": [3, 1, 1],
}


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires reviewed animation-snapshot runtime",
)
class AnimationIntegrationTests(unittest.TestCase):
    """Keep animation expiry and paid reversal isolated from the original game."""

    def _assert_licid(self, row):
        self.assertEqual(row["paid"], LICID_COSTS[row["name"]])
        self.assertTrue(row["before"]["aura"])
        self.assertFalse(row["before"]["creature"])
        self.assertEqual(row["source_after"], row["before"])
        self.assertTrue(row["copied_after"]["creature"])
        self.assertFalse(row["copied_after"]["aura"])
        self.assertEqual(row["copied_after"]["name"], row["name"])
        target = row["copied_target"]
        self.assertEqual((target["power"], target["toughness"]), (2, 2))
        self.assertFalse(target["flying"])
        self.assertFalse(target["artifact"])
        if row["name"] == "Gliding Licid":
            self.assertTrue(row["source_target"]["flying"])

    def _assert_timed(self, row):
        self.assertEqual(row["source"], row["before"])
        after = row["after"]
        if row["case"] == "spoiling":
            self.assertEqual(row["paid"], 3)
            self.assertEqual(
                (row["before"]["power"], row["before"]["toughness"]), (0, 2)
            )
            self.assertFalse(row["before"]["flying"])
            self.assertEqual((after["power"], after["toughness"]), (4, 4))
            self.assertTrue(after["flying"])
        elif row["case"] == "permanent_layer":
            self.assertEqual(row["paid"], [5, 1])
            self.assertEqual(row["before"]["power"], 11)
            self.assertEqual((after["power"], after["toughness"]), (9, 9))
            self.assertEqual(after["name"], "Vitu-Ghazi")
            self.assertTrue(after["creature"])
            self.assertTrue(after["land"])
            self.assertTrue(after["haste"])
            self.assertFalse(after["artifact"])
        else:
            factory = row["case"] == "factory"
            self.assertEqual(row["paid"], [1] if factory else 1)
            self.assertEqual(row["before"]["power"], 2 if factory else 3)
            self.assertTrue(row["before"]["creature"])
            self.assertTrue(after["land"])
            self.assertFalse(after["creature"])
            self.assertFalse(after["artifact"])

    def test_paid_animation_expiry_and_all_licid_reversals(self):
        """Resolve real actions, then expire or reverse effects in only the copied world."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("animation_snapshot_state", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/animation_worker.py",
            "animation.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "animation-state.json")
        self.assertEqual(receipt["engine"], expected)
        self.assertEqual(
            [(row["case"], row.get("name")) for row in receipt["cases"]],
            [("licid", name) for name in LICID_COSTS]
            + [
                ("factory", None),
                ("animate_land", None),
                ("spoiling", None),
                ("affinity", None),
                ("permanent_layer", None),
            ],
        )
        for row in receipt["cases"]:
            with self.subTest(case=row["case"], name=row.get("name")):
                self.assertEqual(row["unmapped"], [])
                if row["case"] == "licid":
                    self._assert_licid(row)
                elif row["case"] == "affinity":
                    self.assertEqual(row["paid"], 3)
                    self.assertEqual(row["before"], 4)
                    self.assertEqual(row["source"], 4)
                    self.assertEqual(row["after"], 0)
                    self.assertEqual(row["lands_after"], 4)
                else:
                    self._assert_timed(row)
