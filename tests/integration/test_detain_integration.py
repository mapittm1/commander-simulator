"""Opt-in native Detain deadlines, admission and raw control-age regressions."""

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
    "Requires reviewed Detain and control-age runtime",
)
class DetainIntegrationTests(unittest.TestCase):
    """Compare paid native outcomes in independent source and copied worlds."""

    def _assert_state(self, state):
        for card in state["cards"]:
            self.assertTrue(card["in_world"])
            self.assertEqual(card["zone"], "Battlefield")
            self.assertEqual(card["detained"], card["view_detained"])
            self.assertEqual(card["detained"], card["lki_detained"])
            self.assertEqual(
                card["first_turn_controlled"], card["lki_first_turn_controlled"]
            )
            if "can_attack" in card:
                self.assertEqual(card["cant_attack"], card["detained"])
                self.assertEqual(card["cant_block"], card["detained"])
                ready = not card["tapped"] and not card["phased_out"]
                self.assertEqual(
                    card["can_attack"],
                    ready and not card["detained"] and not card["attacker_sick"],
                )
                self.assertEqual(card["can_block"], ready and not card["detained"])

    def _assert_case(self, row):
        kind = row["case"]
        costs = {
            "activation": [2, 2],
            "combat": [2, 2],
            "mass": [2, 1, 2],
            "artifact": [4, 3],
            "same": [2, 2, 2],
            "different": [2, 2, 2],
            "blink": [2, 2, 1],
            "control": [2, 2, 3],
            "extra": [2, 2, 5],
            "expired": [2, 2],
            "haste": [3, 2, 2],
            "haste_removal": [3, 3, 2, 2],
        }
        self.assertEqual(row["paid"], costs[kind])
        self.assertEqual(row["unmapped"], [])
        self.assertEqual(row["copied_before"], row["before"])
        self._assert_state(row["before"])
        self._assert_state(row["copied_before"])
        if kind == "haste_removal":
            self._assert_haste_loss(row)
            return
        self.assertEqual(row["source"], row["before"])
        self.assertEqual(row["source_checkpoints"], row["checkpoints"])
        initially_detained = kind not in {"blink", "expired"}
        self.assertEqual(len(row["before"]["cards"]), 2 if kind == "mass" else 1)
        for card in row["before"]["cards"]:
            self.assertEqual(card["detained"], initially_detained)
            self.assertEqual(card["activation"], not initially_detained)
        count = 1 if kind == "extra" else 4
        self.assertEqual(len(row["checkpoints"]), count if kind != "control" else 2)
        for index, state in enumerate(row["checkpoints"]):
            self._assert_state(state)
            detained = initially_detained and index < len(row["checkpoints"]) - 1
            for card in state["cards"]:
                self.assertEqual(card["detained"], detained)
                self.assertEqual(card["activation"], not detained)
        if kind == "different":
            self.assertTrue(row["checkpoints"][1]["cards"][0]["detained"])
            self.assertEqual(row["checkpoints"][-1]["turn"], 2)
        if kind == "control":
            self.assertEqual(row["before"]["cards"][0]["controller"], 2)
            self.assertEqual(row["checkpoints"][-1]["cards"][0]["controller"], 1)
        if kind == "extra":
            self.assertEqual(row["checkpoints"][0]["turn"], 0)
            self.assertTrue(row["checkpoints"][0]["extra"])

    def _assert_haste_loss(self, row):
        self.assertEqual(row["independent"], row["before"])
        self.assertEqual(row["source_after"], row["after"])
        for name in ("before", "copied_before", "independent"):
            card = row[name]["cards"][0]
            self._assert_state(row[name])
            self.assertTrue(card["first_turn_controlled"])
            self.assertTrue(card["haste"])
            self.assertTrue(card["activation"])
        for name in ("source_after", "after"):
            self._assert_state(row[name])
            card = row[name]["cards"][0]
            self.assertTrue(card["first_turn_controlled"])
            self.assertFalse(card["haste"])
            self.assertTrue(card["sick"])
            self.assertFalse(card["activation"])

    def test_detain_and_haste_loss(self):
        """Exercise twelve fixed legal-card witnesses on the selected runtime."""
        run = create_run()
        settings = read_settings()
        worker = PROJECT_ROOT / "tests/support/detain_worker.py"
        process, _ = run_test_worker(settings, run, worker, "detain.log")
        self.assertEqual(process["returncode"], 0, process)
        receipt = read_bounded_json(run / "detain.json")
        self.assertEqual(receipt["engine"], settings.verify())
        expected = (
            "activation combat mass artifact same different blink control extra"
            " expired haste haste_removal"
        ).split()
        observed = receipt["cases"]
        self.assertEqual(expected, [case["case"] for case in observed])
        for index, row in enumerate(observed):
            with self.subTest(case=expected[index]):
                self._assert_case(row)
