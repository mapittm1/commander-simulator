"""Opt-in actual AI choices, decision beneficiaries and paid response outcomes."""

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
    "Requires reviewed decision-beneficiary planning runtime",
)
class DecisionIntegrationTests(unittest.TestCase):
    """Observe legal AI choices and native outcomes without requiring arbitrary tie breaks."""

    def _assert_world(self, state):
        self.assertEqual(len(state["players"]), 4)
        for seat, participant in enumerate(state["players"]):
            self.assertEqual(participant["seat"], seat)
            self.assertTrue(participant["in_world"], participant)
            self.assertEqual(participant["view_master"], participant["master"])
            self.assertEqual(
                participant["controlled"],
                participant["master"] is not None and participant["master"] != seat,
            )
        for card in state["cards"]:
            if card["present"]:
                self.assertTrue(card["in_world"], card)
            else:
                self.assertIsNone(card["zone"])
        for entry in state["stack"]:
            self.assertTrue(entry["in_world"], entry)

    def _assert_payment(self, row):
        before = row["before"]["players"]
        after = row["after"]["players"]
        if row["selected"] is None:
            self.assertEqual(row["paid"], 0)
            self.assertIsNone(row["paid_actor"])
            self.assertEqual(row["mana_owners"], [])
        else:
            self.assertTrue(row["selected"]["in_world"])
            self.assertEqual(row["selected"]["actor"], 1)
            self.assertEqual(row["paid_actor"], 1)
            self.assertEqual(row["mana_owners"], [1] * row["paid"])
        self.assertEqual(
            after[1]["tapped_lands"], before[1]["tapped_lands"] + row["paid"]
        )
        for seat in (0, 2, 3):
            if after[seat]["active"]:
                self.assertEqual(
                    after[seat]["tapped_lands"], before[seat]["tapped_lands"]
                )

    def _assert_damage(self, row):
        selected = row["selected"]
        self.assertIsNotNone(selected)
        self.assertEqual(selected["name"], "Shock")
        self.assertEqual(selected["api"], "DealDamage")
        self.assertTrue(selected["is_spell"])
        self.assertEqual(len(selected["players"]), 1)
        self.assertEqual(selected["spells"], [])
        self.assertEqual(row["paid"], 1)
        if row["case"].startswith(("ordinary", "self")):
            self.assertEqual(selected["players"], [0])
        else:
            self.assertNotIn(row["beneficiary"], selected["players"])
        for seat, participant in enumerate(row["after"]["players"]):
            expected = row["before"]["players"][seat]["life"] - (
                2 if seat in selected["players"] else 0
            )
            self.assertEqual(participant["life"], expected)
            self.assertEqual(participant["active"], expected > 0)
        self.assertTrue(row["after"]["players"][row["beneficiary"]]["active"])

    def _assert_response(self, row):
        ability = row["case"].startswith("ability")
        threat = row["before"]["stack"][0]
        self.assertEqual(row["threat_paid"], 1)
        self.assertEqual(threat["actor"], 2)
        self.assertEqual(threat["host_id"], row["threat_id"])
        self.assertEqual(threat["is_spell"], not ability)
        self.assertEqual(row["before"]["stack_cards"], 0 if ability else 1)
        selected = row["selected"]
        self.assertIsNotNone(selected)
        self.assertEqual(selected["name"], "Stifle" if ability else "Counterspell")
        self.assertEqual(selected["api"], "Counter")
        self.assertEqual(selected["spells"], [row["threat_id"]])
        self.assertEqual(selected["players"], [])
        self.assertEqual(row["paid"], 1 if ability else 2)
        self.assertEqual(
            [p["life"] for p in row["after"]["players"]],
            [p["life"] for p in row["before"]["players"]],
        )
        self.assertTrue(all(p["active"] for p in row["after"]["players"]))
        if ability:
            self.assertNotIn(
                row["threat_id"],
                row["threat_sources"],
                "The Ring cannot fund its own tap cost",
            )
            ring = next(
                card
                for card in row["before"]["cards"]
                if card["id"] == row["threat_id"]
            )
            self.assertEqual(ring["zone"], "Graveyard")

    def _assert_uncounterable(self, row):
        self.assertIsNone(row["selected"])
        self.assertEqual(row["threat_paid"], 3)
        self.assertEqual(row["before"]["stack"][0]["name"], "Volcanic Fallout")
        for seat, participant in enumerate(row["after"]["players"]):
            self.assertEqual(
                participant["life"], row["before"]["players"][seat]["life"] - 2
            )
        self.assertFalse(row["after"]["players"][0]["active"])
        self.assertIsNone(row["after"]["players"][1]["master"])

    def _assert_case(self, row):
        case = row["case"]
        self.assertEqual(
            row["before"],
            row["after_choice"],
            "Planning must not play or resolve original actions",
        )
        for label in ("before", "after_choice", "after"):
            self._assert_world(row[label])
        controlled = case.startswith(
            ("controlled", "chain", "response_controlled", "uncounterable", "ability")
        )
        self.assertEqual(row["setup_paid"], [8] if controlled else [])
        self.assertEqual(
            row["before"]["players"][1]["master"],
            0 if controlled else 1 if case.startswith("self") else None,
        )
        if case.startswith("chain"):
            self.assertEqual(row["before"]["players"][0]["master"], 2)
            self.assertEqual(row["beneficiary"], 2)
        self.assertEqual(row["after"]["stack"], [])
        self.assertEqual(row["after"]["stack_cards"], 0)
        self._assert_payment(row)
        if case.startswith("uncounterable"):
            self._assert_uncounterable(row)
        elif case.startswith(("response", "ability")):
            self.assertEqual(len(row["before"]["stack"]), 1)
            self._assert_response(row)
        else:
            self.assertEqual(row["before"]["stack"], [])
            self.assertEqual(row["threat_paid"], 0)
            self._assert_damage(row)

    def test_actual_decision_outcomes(self):
        """Retain sixteen paid choices in the selected packaged runtime."""
        worker = PROJECT_ROOT / "tests/support/decision_worker.py"
        settings = read_settings()
        run = create_run()
        process, _ = run_test_worker(settings, run, worker, "decision.log")
        self.assertEqual(process["returncode"], 0, process)
        receipt = read_bounded_json(run / "decision.json")
        self.assertEqual(receipt["engine"], settings.verify())
        self.assertEqual(
            receipt["cautious_profile_sha256"],
            file_hash(settings.home / "res/ai/Cautious.ai"),
        )
        self.assertEqual(
            receipt["profile_sha256"],
            {
                name: file_hash(settings.home / "res/ai" / (name + ".ai"))
                for name in ("Default", "Cautious")
            },
        )
        expected = (
            "ordinary_heuristic ordinary_full controlled_heuristic controlled_full"
            " chain_heuristic chain_full self_heuristic self_full"
            " response_ordinary_heuristic response_ordinary_full"
            " response_controlled_heuristic response_controlled_full"
            " uncounterable_heuristic uncounterable_full ability_heuristic ability_full"
        ).split()
        observations = receipt["cases"]
        self.assertEqual([item["case"] for item in observations], expected)
        for observed in observations:
            with self.subTest(scenario=observed["case"]):
                self._assert_case(observed)
