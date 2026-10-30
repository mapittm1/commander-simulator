"""Opt-in paid usage-history checks against the pinned packaged runtime."""

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
    "Requires reviewed usage-history runtime",
)
class UsageIntegrationTests(unittest.TestCase):
    """Limits and mode histories constrain paid actions after snapshots."""

    def _assert_activation(self, row):
        self.assertEqual(row["cast_paid"], 3)
        self.assertEqual(row["activation_paid"], 6 if row["case"] == "only_once" else 0)
        self.assertEqual(row["before"], row["after"])
        self.assertEqual(row["source"], row["before"])
        self.assertTrue(row["actor_world"])
        self.assertTrue(row["host_world"])
        self.assertFalse(row["after"]["playable"])
        self.assertEqual(row["after"]["turn"], 1)
        self.assertEqual(row["after"]["game"], 1)
        pending = row["case"] == "pending"
        self.assertEqual(row["after"]["resolved"], 0 if pending else 1)
        self.assertEqual(row["resolved_after"]["resolved"], 1)
        self.assertEqual(row["entries"], int(pending))
        self.assertEqual(row["source_entries"], int(pending))

    def _assert_modes(self, row):
        self.assertEqual(row["paid"], [5, 2, 2, 2])
        self.assertEqual(len(row["first"]), 1)
        self.assertEqual(row["retained"], row["first"])
        self.assertEqual(len(row["second"]), 2)
        self.assertEqual(len(set(row["second"])), 2)
        self.assertEqual(len(row["third"]), 3)
        self.assertEqual(len(set(row["third"])), 3)
        self.assertTrue(set(row["first"]) <= set(row["second"]) <= set(row["third"]))
        self.assertEqual(row["source"], row["first"])
        self.assertEqual(row["middle_source"], row["second"])

    def test_paid_usage_histories(self):
        """Resolve paid actions and retain independent native usage evidence."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("usage_snapshot_state", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings, run, PROJECT_ROOT / "tests/support/usage_worker.py", "usage.log"
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "usage-state.json")
        self.assertEqual(receipt["engine"], expected)
        self.assertEqual(
            [row["case"] for row in receipt["cases"]],
            ["once_per_turn", "pending", "only_once", "vampire", "modes", "saddle"],
        )
        for row in receipt["cases"]:
            with self.subTest(case=row["case"]):
                if "activation_paid" in row:
                    self._assert_activation(row)
                elif row["case"] == "modes":
                    self._assert_modes(row)
                elif row["case"] == "vampire":
                    self.assertEqual(row["paid"], [2, 2])
                    self.assertEqual(row["before"], 1)
                    self.assertEqual(row["after"], 1)
                    self.assertEqual(row["source"], 1)
                    self.assertTrue(row["host_world"])
                else:
                    self.assertEqual(row["cast_paid"], 3)
                    self.assertEqual(row["before"], 2)
                    self.assertEqual(row["after"], 2)
                    self.assertEqual(row["source"], 2)
                    self.assertGreater(row["members"], 0)
                    self.assertEqual(row["members"], row["source_members"])
                    self.assertTrue(row["member_worlds"])
