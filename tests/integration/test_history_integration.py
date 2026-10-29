"""Packaged runtime witnesses for historical card state and a paid morbid spell."""

import json
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
    "Requires reviewed historical-state native engine",
)
class HistoryIntegrationTests(unittest.TestCase):
    """History must retain old characteristics and influence real spell resolution."""

    def test_departed_objects_and_paid_morbid_resolution(self):
        """Observe real zone moves and a mana-paid Tragic Slip in a copied game."""
        settings = read_settings()
        expected = settings.verify()
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/history_worker.py",
            "history.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "history-state.json")
        self.assertEqual(receipt["engine"], expected)
        expected_rows = [
            ("counters", "Grizzly Bears", 4, 4, 0, "Battlefield"),
            ("name", "Eldrazi Grizzly Bears", 2, 2, 1, "Battlefield"),
            ("transformed", "Reflection of Kiki-Jiki", 2, 2, 0, "Battlefield"),
            ("graveyard_pt", "Grizzly Bears", 1, 4, 1, "Graveyard"),
        ]
        self.assertEqual(len(receipt["cases"]), len(expected_rows))
        for row, (case, name, power, toughness, stickers, zone) in zip(
            receipt["cases"], expected_rows
        ):
            with self.subTest(case=case):
                self.assertEqual(row["case"], case)
                self.assertEqual(
                    row["original"],
                    {
                        "name": name,
                        "power": power,
                        "toughness": toughness,
                        "stickers": stickers,
                        "zone": zone,
                        "creature": True,
                        "sheet_in_same_game": True,
                    },
                )
                self.assertEqual(row["copied"], row["original"])
                self.assertIs(row["mapped_context"], True)
        self.assertEqual(len(receipt["morbid"]), 2)
        for row, creature_died in zip(receipt["morbid"], (True, False)):
            with self.subTest(creature_died=creature_died):
                self.assertEqual(
                    row,
                    {
                        "creature_died": creature_died,
                        "paid": True,
                        "paying_mana": 1,
                        "copied_stack": 1,
                        "target_in_graveyard": creature_died,
                        "power_before_state_actions": -7 if creature_died else 5,
                        "source_target_unchanged": True,
                        "source_stack": 1,
                    },
                )
        self.assertEqual(settings.verify(), expected)
        print(
            json.dumps({"history_run": run.name, "cases": 4, "paid_morbid": "passed"})
        )
