"""Opt-in primary-control choices with native costs, timing and survival."""

import json
import os
import unittest

import pytest

from commander.engine.forge_config import file_hash, read_settings
from commander.engine.interactions.suite import read_bounded_json
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires reviewed primary-control runtime",
)
class PrimaryControlIntegrationTests(unittest.TestCase):
    """Require actual selections, payments and outcomes, not dispatch availability."""

    def _assert_independence(self, row):
        self.assertTrue(row["world_unchanged"])
        self.assertEqual(row["before"], row["after_choice"])
        for state in (row["before"], row["after_choice"]):
            self.assertEqual(state["stack"], [])
            self.assertTrue(all(player["in_world"] for player in state["players"]))
            self.assertTrue(all(card["in_world"] for card in state["cards"]))

    def _assert_positive(self, row):
        self._assert_independence(row)
        self.assertTrue(row["rng_restored"])
        selected = row["selected"]
        self.assertIsNotNone(selected)
        self.assertEqual(selected["api"], "ControlPlayer")
        self.assertEqual(selected["actor"], 0)
        self.assertEqual(selected["players"], [1])
        self.assertEqual(selected["host_id"], row["host_id"])
        self.assertTrue(selected["in_world"])
        key = row["key"]
        self.assertEqual(
            row["setup_paid"],
            {"mind": [6], "sorin": [6], "bracelet": [2, 1]}.get(key, []),
        )
        for prefix in row["prefixes"]:
            self.assertEqual(key, "bracelet")
            self.assertEqual(row["mode"], "full")
            self.assertEqual(prefix["api"], "Attach")
            self.assertEqual(prefix["actor"], 0)
            self.assertEqual(prefix["paid"], 1)
            self.assertTrue(prefix["in_world"])
        self.assertLessEqual(len(row["prefixes"]), 3)
        if key == "secret":
            self.assertIn(row["paid"], (4, 14))
            if row["normal"]:
                self.assertEqual(row["paid"], 4)
        else:
            self.assertEqual(
                row["paid"], {"worst": 8, "mind": 4, "sorin": 0, "bracelet": 14}[key]
            )
        for seat, (before, paid) in enumerate(
            zip(row["before"]["players"], row["after_payment"]["players"])
        ):
            self.assertEqual(before["life"], paid["life"])
            self.assertEqual(
                paid["tapped_lands"] - before["tapped_lands"],
                row["paid"] + len(row["prefixes"]) if seat == 0 else 0,
            )
        combat_only = key == "secret" and row["paid"] == 4
        self.assertEqual(
            row["acquired"]["players"][1]["master"], None if combat_only else 0
        )
        self.assertEqual(
            row["after"]["players"][1]["master"], None if combat_only else 0
        )
        self.assertTrue(row["after"]["players"][0]["active"])
        self.assertEqual(row["after"]["players"][0]["life"], 2)
        self.assertEqual(row["after"]["phase"], "MAIN2")
        cards = row["after"]["cards"]
        if key == "bracelet":
            self.assertEqual([card["zone"] for card in cards], ["Exile", "Battlefield"])
            self.assertEqual(row["after"]["host_power"], 0)
        elif key == "sorin":
            self.assertEqual(cards[0]["zone"], "Battlefield")
            self.assertEqual(row["after"]["loyalty"], 1)
        else:
            self.assertEqual(
                cards[0]["zone"], "Graveyard" if key == "mind" else "Exile"
            )

    def _assert_negative(self, row):
        self._assert_independence(row)
        self.assertTrue(row["can_pay"])
        self.assertNotEqual(row["decision"], "CantPlayAi")
        self.assertFalse(row["willing"])

    def test_primary_control_costs_timing_and_survival(self):
        """Exercise eighteen real choices and five affordable no-benefit controls."""
        settings = read_settings()
        run = create_run()
        worker = PROJECT_ROOT / "tests/support/primary_control_worker.py"
        process, _ = run_test_worker(settings, run, worker, "primary-control.log")
        self.assertEqual(process["returncode"], 0, process)
        manifest = read_bounded_json(run / "primary-control.json")
        self.assertEqual(manifest["engine"], settings.verify())
        self.assertEqual(
            manifest["default_profile_sha256"],
            file_hash(settings.home / "res/ai/Default.ai"),
        )
        self.assertEqual(len(manifest["parts"]), 4)
        for mode, part in zip(
            ("heuristic", "hybrid", "full", "decline"), manifest["parts"]
        ):
            path = run / ("primary-control-" + mode + ".json")
            self.assertEqual(part["name"], path.name)
            self.assertEqual(file_hash(path), part["sha256"])
            receipt = read_bounded_json(path)
            keys = ["worst", "mind", "secret", "sorin", "bracelet"]
            expected = (
                ["decline_" + key for key in keys]
                if mode == "decline"
                else [key + "_" + mode for key in (*keys, "normal_secret")]
            )
            self.assertEqual([row["case"] for row in receipt["cases"]], expected)
            self.assertEqual(part["cases"], len(expected))
            for row in receipt["cases"]:
                with self.subTest(scenario=row["case"]):
                    if mode == "decline":
                        self._assert_negative(row)
                    else:
                        self._assert_positive(row)
        print(json.dumps({"primary_control_run": run.name, "cases": 23}))
