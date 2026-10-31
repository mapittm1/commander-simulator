"""Opt-in forced blocking and prevention-shield snapshot checks."""

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
    "Requires reviewed blocking/prevention snapshot runtime",
)
class BlockingPreventionIntegrationTests(unittest.TestCase):
    """Observe copied-world requirements and real damage, without replaying casts."""

    def _assert_blocking(self, row):
        self.assertEqual(
            row["paid"],
            {"hunt": [1], "step": [5], "sled": [6, 1], "tusker": [5]}[row["case"]],
        )
        self.assertGreater(row["attacker"], 0)
        active = {"attackers": [row["attacker"]], "mapped_world": True}
        expired = {"attackers": [], "mapped_world": True}
        self.assertEqual(row["before"], active)
        self.assertEqual(row["copied_before"], active)
        self.assertEqual(row["source"], active)
        self.assertEqual(row["after"], expired)
        self.assertEqual(row["source_after"], expired)

    def _assert_shield(self, row):
        case = row["case"]
        creature = case == "creature"
        partial = case == "partial"
        amount = {"player": 4, "creature": 4, "partial": 1, "stacked": 8}[case]
        self.assertEqual(row["paid"], [1, 1] if case in {"partial", "stacked"} else [1])
        self.assertEqual(row["damage_costs"], {"copy_bolt": 1, "source_bolts": [1, 1]})
        active = {
            "shield": amount,
            "life": None if creature else 40,
            "damage": 0 if creature else None,
        }
        self.assertEqual(row["before"], active)
        self.assertEqual(row["copied_before"], active)
        self.assertEqual(row["source"], active)
        self.assertEqual(row["after"], {**active, "shield": 0})
        self.assertEqual(
            row["copy_damage"],
            {
                "shield": 0,
                "life": None if creature else 37,
                "damage": 3 if creature else None,
            },
        )
        source_life = None if creature else 38 if partial else 40
        self.assertEqual(
            row["source_damage"],
            {
                "shield": max(0, amount - 3),
                "life": source_life,
                "damage": 0 if creature else None,
            },
        )
        self.assertEqual(
            row["source_expired"],
            {
                "shield": 0,
                "life": source_life,
                "damage": 0 if creature else None,
            },
        )
        self.assertEqual(
            row["source_final"],
            {
                "shield": 0,
                "life": None if creature else 35 if partial else 37,
                "damage": 3 if creature else None,
            },
        )

    def test_paid_blocking_and_prevention_deadlines(self):
        """Use mapped active requirements, native deadlines and paid damage spells."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("blocking_prevention_snapshot", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/blocking_prevention_worker.py",
            "blocking-prevention.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "blocking-prevention.json")
        self.assertEqual(receipt["engine"], expected)
        self.assertEqual(
            [row["case"] for row in receipt["cases"]],
            "hunt step sled tusker player creature partial stacked".split(),
        )
        for row in receipt["cases"]:
            with self.subTest(case=row["case"]):
                if row["case"] in {"hunt", "step", "sled", "tusker"}:
                    self._assert_blocking(row)
                else:
                    self._assert_shield(row)
                self.assertEqual(row["unmapped"], [])
