"""Detect early resolution, broken pass cycles and forged native payment evidence."""

import copy
import unittest

from commander.engine.interactions.priority import PRIORITY_CASES
from commander.engine.interactions.suite import PARTITIONS, evaluate_receipt, load_suite
from tests.support.interaction_fixture import synthetic_receipt

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class PriorityEvidenceTests(unittest.TestCase):
    """The observer's summaries cannot replace raw identities and native callbacks."""

    def setUp(self):
        self.suite = load_suite()
        receipt = synthetic_receipt(self.suite)
        self.cases = [case for case in receipt["cases"] if case["id"] in PRIORITY_CASES]

    def _evaluate(self, cases):
        return evaluate_receipt(
            self.suite,
            {"cases": cases},
            {"status": "finished", "returncode": 0},
            case_ids=PARTITIONS["priority"],
        )

    def test_native_sequence_examples_satisfy_the_independent_verifier(self):
        self.assertEqual(self._evaluate(self.cases)["status"], "passed")

    def test_missing_callbacks_steps_and_payment_evidence_cannot_pass(self):
        for field in ("callbacks", "steps", "spells", "lands"):
            cases = copy.deepcopy(self.cases)
            del cases[1]["states"]["after_sequence"][field]
            with self.subTest(field=field):
                self.assertEqual(self._evaluate(cases)["status"], "inconclusive")

    def test_counterexamples_fail_even_when_final_summary_is_unchanged(self):
        def missing_last_pass(state):
            state["callbacks"].pop()

        def wrong_seat(state):
            state["callbacks"][3]["player_id"] = 2

        def wrong_target(state):
            state["callbacks"][2]["targets"] = [21]

        def early_resolution(state):
            state["steps"][2]["stack"] = []

        def forged_payment(state):
            state["steps"][1]["spells"][1]["payment"][0]["source_id"] = 30

        def wrong_color(state):
            state["steps"][1]["spells"][1]["payment"][0]["symbol"] = "G"

        def unpaid_response(state):
            state["steps"][1]["lands"][1][0]["tapped"] = False

        def unexpected_phase(state):
            state["steps"][1]["phase"] = "MAIN2"

        def wrong_zone(state):
            state["steps"][-1]["spells"][0]["zone"] = "Battlefield"

        for mutate in (
            missing_last_pass,
            wrong_seat,
            wrong_target,
            early_resolution,
            forged_payment,
            wrong_color,
            unpaid_response,
            unexpected_phase,
            wrong_zone,
        ):
            cases = copy.deepcopy(self.cases)
            mutate(cases[1]["states"]["after_sequence"])
            with self.subTest(mutation=mutate.__name__):
                self.assertEqual(self._evaluate(cases)["status"], "failed")

    def test_invalid_starting_resources_are_inconclusive(self):
        self.cases[1]["states"]["before"]["lands"][1][0]["controller_id"] = 0
        self.assertEqual(self._evaluate(self.cases)["status"], "inconclusive")

    def test_unaffordable_response_cannot_tap_resources_or_claim_a_cast(self):
        state = self.cases[2]["states"]["after_sequence"]
        state["callbacks"][2]["cast"] = True
        self.assertEqual(self._evaluate(self.cases)["status"], "failed")

    def test_malformed_native_ids_booleans_and_missing_targets_are_inconclusive(self):
        for field, value in (
            ("proposal_id", True),
            ("targets", [True]),
            ("stack", "20"),
            ("can_pay", 1),
        ):
            cases = copy.deepcopy(self.cases)
            cases[1]["states"]["after_sequence"]["callbacks"][2][field] = value
            with self.subTest(field=field):
                self.assertEqual(self._evaluate(cases)["status"], "inconclusive")

    def test_affordable_optional_response_must_remain_unpaid_when_passing(self):
        self.cases[0]["states"]["after_sequence"]["steps"][1]["lands"][1][0][
            "tapped"
        ] = True
        self.assertEqual(self._evaluate(self.cases)["status"], "failed")

    def test_unaffordable_response_cannot_report_payment_or_a_changed_hand_zone(self):
        self.cases[2]["states"]["after_sequence"]["steps"][1]["spells"][1][
            "zone"
        ] = "Graveyard"
        self.assertEqual(self._evaluate(self.cases)["status"], "failed")
