"""Counter-counterspell evidence must witness separate resolution and pass cycles."""

import copy
import unittest

from commander.engine.interactions.chains import CHAIN_CASES
from commander.engine.interactions.suite import PARTITIONS, evaluate_receipt, load_suite
from tests.support.interaction_fixture import synthetic_receipt

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class CounterChainTests(unittest.TestCase):
    """Mutate intermediate native evidence without replacing final summaries."""

    def setUp(self):
        self.suite = load_suite()
        self.cases = [
            case
            for case in synthetic_receipt(self.suite)["cases"]
            if case["id"] in CHAIN_CASES
        ]

    def _evaluate(self, cases):
        return evaluate_receipt(
            self.suite,
            {"cases": cases},
            {"status": "finished", "returncode": 0},
            case_ids=PARTITIONS["counter_chain"],
        )

    def test_paid_chain_and_unaffordable_control_require_complete_evidence(self):
        self.assertEqual(self._evaluate(self.cases)["status"], "passed")

    def test_invalid_setup_is_inconclusive(self):
        mutations = (
            ("controller_id", 1),
            ("ability_id", 21),
            ("id", 11),
        )
        for key, value in mutations:
            cases = copy.deepcopy(self.cases)
            cases[0]["states"]["before"]["spells"][2][key] = value
            with self.subTest(field=key):
                self.assertEqual(self._evaluate(cases)["status"], "inconclusive")

    def test_missing_or_malformed_targets_and_mana_are_inconclusive(self):
        for field in ("stack_targets", "mana_pool"):
            cases = copy.deepcopy(self.cases)
            del cases[0]["states"]["after_sequence"]["steps"][2][field]
            with self.subTest(field=field):
                self.assertEqual(self._evaluate(cases)["status"], "inconclusive")
        cases = copy.deepcopy(self.cases)
        cases[0]["states"]["after_sequence"]["steps"][2]["stack_targets"][0][
            "targets"
        ] = [True]
        self.assertEqual(self._evaluate(cases)["status"], "inconclusive")

    def test_wrong_targets_early_resolution_priority_and_refunds_fail(self):
        mutations = (
            (
                2,
                "stack_targets",
                [
                    {"ability_id": 22, "targets": [20]},
                    {"ability_id": 21, "targets": [20]},
                    {"ability_id": 20, "targets": []},
                ],
            ),
            (5, "stack", []),
            (5, "priority_id", 2),
            (5, "mana_pool", [0, 3, 0, 0]),
        )
        for offset, key, value in mutations:
            cases = copy.deepcopy(self.cases)
            cases[0]["states"]["after_sequence"]["steps"][offset][key] = value
            with self.subTest(step=offset, field=key):
                self.assertEqual(self._evaluate(cases)["status"], "failed")

    def test_countered_spell_stays_paid_and_root_stays_pending(self):
        for field, value in (("zone", "Battlefield"), ("payment", [])):
            cases = copy.deepcopy(self.cases)
            index = 0 if field == "zone" else 1
            cases[0]["states"]["after_sequence"]["steps"][5]["spells"][index][
                field
            ] = value
            with self.subTest(field=field):
                self.assertEqual(self._evaluate(cases)["status"], "failed")

    def test_second_pass_cycle_cannot_be_skipped(self):
        self.cases[0]["states"]["after_sequence"]["callbacks"].pop(9)
        self.assertEqual(self._evaluate(self.cases)["status"], "failed")

    def test_opponents_mana_cannot_pay_for_the_second_counter(self):
        state = self.cases[0]["states"]["after_sequence"]["steps"][2]
        state["spells"][2]["payment"][0]["source_id"] = 31
        self.assertEqual(self._evaluate(self.cases)["status"], "failed")

    def test_unaffordable_chain_cannot_spend_mana_or_claim_a_cast(self):
        for key, value in (("cast", True), ("can_pay", True), ("targets", [20])):
            cases = copy.deepcopy(self.cases)
            cases[1]["states"]["after_sequence"]["callbacks"][4][key] = value
            with self.subTest(field=key):
                self.assertEqual(self._evaluate(cases)["status"], "failed")
        cases = copy.deepcopy(self.cases)
        cases[1]["states"]["after_sequence"]["steps"][2]["lands"][2][0]["tapped"] = True
        self.assertEqual(self._evaluate(cases)["status"], "failed")
