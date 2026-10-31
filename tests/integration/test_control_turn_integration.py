"""Opt-in paid turn/combat-control lifecycles and independent agent worlds."""

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
    "Requires reviewed turn-control snapshot runtime",
)
class ControlTurnIntegrationTests(unittest.TestCase):
    """Inspect paid native control, source independence and release deadlines."""

    def _assert_state(self, state, masters, departed=False):
        self.assertEqual(len(state["players"]), 4)
        for index, player in enumerate(state["players"]):
            master = masters[index]
            self.assertEqual(player["seat"], index)
            self.assertTrue(player["in_world"])
            self.assertEqual(player["active"], not (departed and index == 0))
            self.assertEqual(player["master"], master)
            self.assertEqual(player["view_master"], master)
            self.assertEqual(
                player["controlled"], master is not None and master != index
            )
            self.assertEqual(
                player["decision_maker"], index if master is None else master
            )
        for card in state["cards"]:
            self.assertEqual(card["present"], not departed)
            if departed:
                self.assertIsNone(card["zone"])
                self.assertIsNone(card["in_world"])
            else:
                self.assertTrue(card["in_world"])
                zone = {
                    "Mindslaver": "Graveyard",
                    "Emrakul, the Promised End": "Battlefield",
                    "Worst Fears": "Exile",
                    "Secret of Bloodbending": "Exile",
                }[card["name"]]
                self.assertEqual(card["zone"], zone)

    def _assert_case(self, row):
        case = row["case"]
        kind = case.split("_")[0]
        costs = {
            "worst": [8],
            "mindslaver": [6, 4],
            "combat": [4],
            "overlap": [8, 8],
            "self": [8],
            "expired": [8],
            "departed": [8],
            "emrakul": [13],
        }
        self.assertEqual(row["paid"], costs[kind])
        self.assertEqual(row["unmapped"], [])
        self.assertEqual(row["before"], row["copied_before"])
        self.assertEqual(row["before"], row["source"])
        self.assertEqual(row["checkpoints"], row["source_checkpoints"])
        target = 3 if kind == "overlap" else 0 if case == "self" else 1
        master = 2 if kind == "overlap" else 0
        initial = [None] * 4
        if case.endswith("active") or case == "self":
            initial[target] = master
        self._assert_state(row["before"], initial, case == "departed")
        self._assert_state(row["copied_before"], initial, case == "departed")
        self.assertEqual(len(row["checkpoints"]), 2 if case.endswith("pending") else 1)
        for index, state in enumerate(row["checkpoints"]):
            expected = [None] * 4
            if case.endswith("pending") and index == 0:
                expected[target] = master
            self._assert_state(state, expected, case == "departed")
        final = row["checkpoints"][-1]
        if kind == "combat":
            self.assertEqual(final["phase"], "MAIN2")
            self.assertEqual(final["turn"], 1)
        else:
            self.assertEqual(final["phase"], "MAIN1")
        if kind == "emrakul":
            self.assertEqual(final["turn"], 1)
            self.assertTrue(final["extra"])
        else:
            self.assertFalse(final["extra"])
        if case == "self":
            self.assertEqual(row["before"]["turn"], 0)
            self.assertFalse(row["before"]["players"][0]["controlled"])
            self.assertEqual(row["before"]["players"][0]["master"], 0)

    def test_turn_and_combat_control(self):
        """Run thirteen fixed witnesses, including Emrakul's extra-turn continuation."""
        run = create_run()
        settings = read_settings()
        worker = PROJECT_ROOT / "tests/support/control_turn_worker.py"
        process, _ = run_test_worker(settings, run, worker, "control-turn.log")
        self.assertEqual(process["returncode"], 0, process)
        receipt = read_bounded_json(run / "control-turn.json")
        self.assertEqual(receipt["engine"], settings.verify())
        cases = receipt["cases"]
        names = (
            "worst_pending worst_active mindslaver_pending mindslaver_active"
            " combat_pending combat_active overlap_pending overlap_active self"
            " expired departed emrakul_pending emrakul_active"
        ).split()
        self.assertEqual(names, [case["case"] for case in cases])
        for case in cases:
            with self.subTest(witness=case["case"]):
                self._assert_case(case)
