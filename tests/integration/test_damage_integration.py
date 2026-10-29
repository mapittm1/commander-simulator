"""Opt-in packaged-runtime damage provenance and real paid cast witnesses."""

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
    "Requires reviewed damage-history runtime and native bridge",
)
class DamageIntegrationTests(unittest.TestCase):
    """Require historical source/target facts and native cast admission to agree."""

    def test_damage_provenance_freerunning_and_bloodthirst(self):
        """Exercise seven freerunning controls, filtered packets and paid bloodthirst."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("damage_history_state", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings, run, PROJECT_ROOT / "tests/support/damage_worker.py", "damage.log"
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "damage-state.json")
        self.assertEqual(receipt["engine"], expected)
        cases = (
            ("assassin", True),
            ("commander", True),
            ("ordinary", False),
            ("noncombat", False),
            ("creatureTarget", False),
            ("stolenAfter", True),
            ("stolenBefore", False),
        )
        self.assertEqual(len(receipt["freerunning"]), len(cases))
        for row, (scenario, eligible) in zip(receipt["freerunning"], cases):
            with self.subTest(scenario=scenario):
                self.assertEqual(
                    row,
                    {
                        "scenario": scenario,
                        "original_window": eligible,
                        "copied_window": eligible,
                        "allowed": eligible,
                        "mana_paid": 2 if eligible else 0,
                        "copied_hand": 3 if eligible else 1,
                        "spell_in_graveyard": eligible,
                        "source_hand": 1,
                        "source_library": 3,
                    },
                )
        with self.subTest(case="damage_queries"):
            self.assertEqual(
                receipt["queries"],
                {
                    "opponent": 3,
                    "self": 3,
                    "invalid_source": 0,
                    "invalid_target": 0,
                    "received_from_source": 3,
                    "maximum": 3,
                    "distinct_targets": 2,
                    "mapped_records": True,
                    "after_copy_damage": 5,
                    "source_unchanged": 3,
                    "after_source_damage": 7,
                    "copy_unchanged": 5,
                },
            )
        with self.subTest(case="previous_turn"):
            self.assertEqual(
                receipt["previous_turn"],
                {
                    "previous": 3,
                    "current": 0,
                    "maximum": 0,
                    "expired": 0,
                    "source_previous": 3,
                },
            )
        self.assertEqual(len(receipt["bloodthirst"]), 2)
        for row, eligible in zip(receipt["bloodthirst"], (True, False)):
            with self.subTest(bloodthirst=eligible):
                self.assertEqual(
                    row,
                    {
                        "opponent_damaged": eligible,
                        "bolt_mana": 1,
                        "creature_mana": 4,
                        "power": 5 if eligible else 3,
                        "counters": 2 if eligible else 0,
                        "source_stack": 1,
                    },
                )
        self.assertEqual(settings.verify(), expected)
        print(
            json.dumps(
                {
                    "damage_run": run.name,
                    "freerunning": 7,
                    "bloodthirst": 2,
                    "query_groups": 2,
                }
            )
        )
