"""Opt-in paid delayed-trigger checks for the pinned packaged runtime."""

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
    "Requires reviewed delayed-trigger runtime",
)
class DelayedIntegrationTests(unittest.TestCase):
    """Future obligations reach the right objects, players and deadlines."""

    def _assert_pact(self, row):
        self.assertEqual(row["countered_zone"], "Graveyard")
        self.assertFalse(row["source_lost"])
        self.assertEqual(row["source_tapped"], 0)
        paid = row["case"] == "pact_paid"
        self.assertEqual(row["owner_lost"], not paid)
        self.assertEqual(row["active_seats"], 4 if paid else 3)
        self.assertEqual(row["tapped_after_debt"], 5 if paid else 0)
        if paid:
            self.assertEqual(row["tapped_next_upkeep"], 0)
            self.assertFalse(row["lost_next_upkeep"])

    def _assert_simple(self, row):
        if row["case"] == "ghostway":
            self.assertEqual(row["paid"], 3)
            self.assertEqual(row["before"], ["Exile", "Exile"])
            self.assertEqual(row["after"], ["Battlefield", "Battlefield"])
            self.assertEqual(row["source"], ["Exile", "Exile"])
            self.assertTrue(all(row["controllers"]))
        elif row["case"] == "infuse":
            self.assertEqual(row["paid"], 3)
            self.assertTrue(row["target_untapped"])
            self.assertEqual(row["before"], 0)
            self.assertEqual(row["first_upkeep"], 1)
            self.assertEqual(row["second_upkeep"], 1)
            self.assertEqual(row["source"], 0)
        elif row["case"] == "rebound":
            self.assertEqual(row["paid"], [2, 1])
            self.assertEqual(row["before"], 2)
            self.assertEqual(row["after"], 3)
            self.assertEqual(row["source"], 2)
            self.assertEqual(row["copied_zone"], "Graveyard")
            self.assertEqual(row["source_zone"], "Exile")
            self.assertEqual(row["lands_tapped"], 0)
        else:
            self.assertEqual(row["after"], ["Hand", "Hand"])
            self.assertEqual(row["source"], ["Exile", "Exile"])
            self.assertTrue(all(row["owners"]))
            self.assertEqual(row["source_hand"], 0)

    def test_paid_delayed_obligations(self):
        """Resolve real paid actions, then only the copied future obligations."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("delayed_trigger_snapshot", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/delayed_worker.py",
            "delayed.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "delayed-state.json")
        self.assertEqual(receipt["engine"], expected)
        self.assertEqual(
            [row["case"] for row in receipt["cases"]],
            [
                "kiki",
                "kiki_removed",
                "ghostway",
                "pact_unpaid",
                "pact_paid",
                "infuse",
                "rebound",
                "suppress_deferred",
                "suppress_registered",
            ],
        )
        for row in receipt["cases"]:
            with self.subTest(case=row["case"]):
                self.assertEqual(row["unmapped"], [])
                if row["case"].startswith("kiki"):
                    removed = row["case"] == "kiki_removed"
                    self.assertEqual(row["paid"], [5, int(removed)])
                    self.assertFalse(row["token_present"])
                    self.assertTrue(row["source_token_present"])
                    self.assertEqual(
                        row["source_kiki_zone"],
                        "Graveyard" if removed else "Battlefield",
                    )
                elif row["case"].startswith("pact"):
                    self._assert_pact(row)
                else:
                    self._assert_simple(row)
