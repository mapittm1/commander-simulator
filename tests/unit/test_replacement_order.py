"""Expected damage is insufficient without the correct native choice evidence."""

import copy
import unittest

from commander.engine.interactions.ordering import ORDER_CASES
from commander.engine.interactions.suite import evaluate_receipt, load_suite
from tests.support.interaction_fixture import synthetic_receipt

# Test names describe behavior.
# pylint: disable=missing-function-docstring

FINISHED = {"status": "finished", "returncode": 0}


class ReplacementOrderTests(unittest.TestCase):
    """Verify arithmetic, triggers, candidates, deciding player, and selected order."""

    def setUp(self):
        self.suite = load_suite()
        self.receipt = synthetic_receipt(self.suite)

    def _case(self, receipt, identity="replacement_double_then_prevent"):
        return next(case for case in receipt["cases"] if case["id"] == identity)

    def test_wrong_damage_and_trigger_outcomes_fail_for_each_order(self):
        for identity in ORDER_CASES:
            for stage, field, value in (
                ("after_damage", "life", 37),
                ("after_damage", "life", 34),
                ("after_damage", "choice_count", 0),
                (
                    "after_second",
                    "tokens",
                    1 if identity.startswith("noncombat_") else 0,
                ),
            ):
                with self.subTest(case=identity, field=field, value=value):
                    receipt = copy.deepcopy(self.receipt)
                    self._case(receipt, identity)["states"][stage][field] = value
                    self.assertEqual(
                        evaluate_receipt(self.suite, receipt, FINISHED)["status"],
                        "failed",
                    )

    def test_forged_choices_fail_even_when_life_and_summary_count_match(self):
        for field, value in (("chosen_id", 999), ("decider_id", 0)):
            receipt = copy.deepcopy(self.receipt)
            state = self._case(receipt)["states"]["after_damage"]
            self.assertEqual(state["life"], 35)
            self.assertEqual(state["choice_count"], 2)
            state["ordering"]["choices"][0][field] = value
            self.assertEqual(
                evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
            )
        receipt = copy.deepcopy(self.receipt)
        self._case(receipt)["states"]["after_damage"]["ordering"]["choices"].reverse()
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
        )

    def test_candidate_raw_identities_and_remaining_inventory_are_verified(self):
        for field, value in (
            ("card_id", 999),
            ("timestamp", 999),
            ("effect_id", 999),
            ("controller_id", 999),
            ("name", "Unreviewed effect"),
        ):
            with self.subTest(field=field):
                receipt = copy.deepcopy(self.receipt)
                candidate = self._case(receipt)["states"]["after_damage"]["ordering"][
                    "choices"
                ][0]["candidates"][0]
                candidate[field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
                )
        for index in (0, 1):
            receipt = copy.deepcopy(self.receipt)
            candidates = self._case(receipt)["states"]["after_damage"]["ordering"][
                "choices"
            ][index]["candidates"]
            candidates.append(copy.deepcopy(candidates[0]))
            self.assertEqual(
                evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
            )

    def test_missing_or_malformed_choice_evidence_is_inconclusive(self):
        for field, value in (
            ("choices", []),
            ("choices", None),
            ("affected_player_id", True),
            ("effects", None),
        ):
            with self.subTest(field=field):
                receipt = copy.deepcopy(self.receipt)
                self._case(receipt)["states"]["after_damage"]["ordering"][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"],
                    "inconclusive",
                )
        receipt = copy.deepcopy(self.receipt)
        del self._case(receipt)["states"]["after_damage"]["ordering"]
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "inconclusive"
        )

    def test_wrong_initial_controller_is_not_a_rules_conclusion(self):
        receipt = copy.deepcopy(self.receipt)
        self._case(receipt)["states"]["before"]["ordering"]["effects"][
            "damage_reducer"
        ]["controller_id"] = 0
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "inconclusive"
        )
