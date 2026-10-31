"""Opt-in keyword-restoration and combat-power-expiry checks."""

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
    "Requires reviewed keyword and power-exchange expiration runtime",
)
class KeywordExpirationIntegrationTests(unittest.TestCase):
    """Check paid native actions and independent copied-world deadlines."""

    def _assert_removal(self, row):
        case = row["case"]
        expected_paid = {
            "claws": [1],
            "landwalk": [0],
            "dragon": [6, 2],
            "minotaur": [4, 2],
            "snitch": [2, 0],
            "angel": [2, 1, 0],
            "ward": [5, 2, 1, 0],
        }
        self.assertEqual(row["paid"], expected_paid[case])
        self.assertEqual(
            row["costs"],
            {
                "activator": 1 if case == "minotaur" else 0,
                "tapped": case == "landwalk",
                "sacrificed": case in {"snitch", "angel", "ward"},
            },
        )
        self.assertEqual(
            row["before"],
            {
                "selected": False,
                "flying": case in {"dragon", "angel"},
                "split_red": case in {"angel", "ward"},
                "zone": "Battlefield",
            },
        )
        self.assertEqual(row["copied_before"], row["before"])
        self.assertEqual(row["source"], row["before"])
        self.assertEqual(
            row["after"],
            {
                "selected": True,
                "flying": case in {"claws", "angel"},
                "split_red": False,
                "zone": "Battlefield",
            },
        )
        self.assertEqual(row["source_after"], row["after"])

    def _assert_exchange(self, row):
        self.assertEqual(row["paid"], [2])
        self.assertTrue(row["blocked"])
        self.assertEqual(row["before"], [6, 0])
        self.assertEqual(row["copied_before"], 6)
        self.assertEqual(row["after"], 0)
        self.assertEqual(row["source"], 6)
        self.assertEqual(row["source_after"], 0)

    def test_paid_keyword_and_combat_deadlines(self):
        """Observe expiry through native phase hooks without replaying paid effects."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("keyword_exchange_expiration", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/keyword_expiration_worker.py",
            "keyword-expiration.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "keyword-expiration.json")
        self.assertEqual(receipt["engine"], expected)
        self.assertEqual(
            [row["case"] for row in receipt["cases"]],
            "claws landwalk dragon minotaur snitch angel ward exchange".split(),
        )
        for row in receipt["cases"]:
            with self.subTest(case=row["case"]):
                if row["case"] == "exchange":
                    self._assert_exchange(row)
                else:
                    self._assert_removal(row)
                self.assertEqual(row["unmapped"], [])
