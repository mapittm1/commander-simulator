"""Paid casting requires native payment and stack evidence, not acceptance flags alone."""

import copy
import unittest

from commander.engine.interactions.casting import CAST_CASES, CAST_STAGES
from commander.engine.interactions.suite import evaluate_receipt, load_suite
from tests.support.interaction_fixture import synthetic_receipt

# Test names describe behavior.
# pylint: disable=missing-function-docstring

FINISHED = {"status": "finished", "returncode": 0}


class CastingVerifierTests(unittest.TestCase):
    """Independently bind paid sources, timing gates, proposed targets, and stack entries."""

    def setUp(self):
        self.suite = load_suite()
        self.receipt = synthetic_receipt(self.suite)

    def _case(self, receipt, identity="cast_instant_paid"):
        return next(case for case in receipt["cases"] if case["id"] == identity)

    def test_native_outcomes_and_admission_controls_must_match_each_case(self):
        mutations = (
            ("cast_instant_paid", "after_attempt", "spell_zone", "Hand"),
            ("cast_instant_paid", "after_attempt", "mana_paid", 0),
            ("cast_instant_paid", "after_resolution", "target_power", 3),
            ("cast_instant_paid", "after_resolution", "target_toughness", 3),
            ("cast_permanent_paid", "after_resolution", "spell_zone", "Graveyard"),
            ("cast_instant_unaffordable", "after_attempt", "cast_succeeded", True),
            ("cast_instant_wrong_color", "after_attempt", "cast_succeeded", True),
            ("cast_instant_unaffordable", "after_attempt", "cast_api_called", False),
            ("cast_permanent_combat_timing", "after_attempt", "cast_api_called", True),
            ("cast_instant_without_priority", "after_attempt", "cast_api_called", True),
            ("cast_permanent_nonempty_stack", "after_attempt", "stack", 0),
            ("cast_instant_combat_paid", "after_attempt", "cast_succeeded", False),
        )
        for identity, stage, field, value in mutations:
            with self.subTest(case=identity, field=field):
                receipt = copy.deepcopy(self.receipt)
                self._case(receipt, identity)["states"][stage][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
                )

    def test_false_payment_receipts_fail_with_matching_summary_flags(self):
        for field, value in (
            ("source_id", 999),
            ("source_timestamp", 999),
            ("color", 1),
            ("symbol", "W"),
        ):
            with self.subTest(field=field):
                receipt = copy.deepcopy(self.receipt)
                state = self._case(receipt)["states"]["after_attempt"]
                self.assertTrue(state["cast_succeeded"])
                self.assertEqual(state["mana_paid"], 1)
                state["payment"][0][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
                )
        receipt = copy.deepcopy(self.receipt)
        self._case(receipt)["states"]["after_attempt"]["sources"][0]["tapped"] = False
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
        )

    def test_raw_cardinalities_cannot_disagree_with_summary_counts(self):
        for field, value in (("stack_card_ids", []), ("payment", []), ("sources", [])):
            receipt = copy.deepcopy(self.receipt)
            self._case(receipt)["states"]["after_attempt"][field] = value
            self.assertEqual(
                evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
            )

    def test_wrong_spell_stack_target_and_priority_identities_fail(self):
        for field, value in (
            ("stack_ability_ids", [999]),
            ("stack_card_ids", [999]),
            ("stack_target_ids", [999]),
            ("spell_id", 999),
            ("target_id", 999),
            ("priority_id", 999),
        ):
            with self.subTest(field=field):
                receipt = copy.deepcopy(self.receipt)
                self._case(receipt)["states"]["after_attempt"][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
                )

    def test_rejected_casts_preserve_raw_resources_card_and_pending_stack(self):
        for identity, field, value in (
            ("cast_instant_wrong_color", "spell_timestamp", 999),
            ("cast_instant_without_priority", "mana_pool", 1),
            ("cast_permanent_nonempty_stack", "stack_ability_ids", [999]),
            ("cast_permanent_nonempty_stack", "stack_card_ids", [999]),
            ("cast_permanent_nonempty_stack", "stack_target_ids", [999]),
        ):
            receipt = copy.deepcopy(self.receipt)
            self._case(receipt, identity)["states"]["after_attempt"][field] = value
            self.assertEqual(
                evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
            )
        receipt = copy.deepcopy(self.receipt)
        self._case(receipt, "cast_instant_wrong_color")["states"]["after_attempt"][
            "sources"
        ][0]["tapped"] = True
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
        )

    def test_missing_or_malformed_payment_evidence_is_inconclusive(self):
        for field, value in (
            ("sources", None),
            ("payment", None),
            ("spell_id", True),
            ("stack_ability_ids", None),
            ("prepared_target_ids", [True]),
        ):
            with self.subTest(field=field):
                receipt = copy.deepcopy(self.receipt)
                self._case(receipt)["states"]["after_attempt"][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"],
                    "inconclusive",
                )
        receipt = copy.deepcopy(self.receipt)
        del self._case(receipt)["states"]["after_attempt"]["payment"]
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "inconclusive"
        )

    def test_incorrect_initial_mana_source_or_priority_is_not_a_rules_conclusion(self):
        for field, value in (
            ("name", "Forest"),
            ("controller_id", 999),
            ("zone", "Graveyard"),
        ):
            receipt = copy.deepcopy(self.receipt)
            self._case(receipt, "cast_instant_wrong_color")["states"]["before"][
                "sources"
            ][0][field] = value
            self.assertEqual(
                evaluate_receipt(self.suite, receipt, FINISHED)["status"],
                "inconclusive",
            )
        receipt = copy.deepcopy(self.receipt)
        self._case(receipt)["states"]["before"]["priority_id"] = 999
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "inconclusive"
        )

    def test_casting_requires_only_its_fixed_checkpoints(self):
        for identity in CAST_CASES:
            receipt = copy.deepcopy(self.receipt)
            case = self._case(receipt, identity)
            self.assertEqual(tuple(case["states"]), CAST_STAGES)
            del case["states"]["after_resolution"]
            self.assertEqual(
                evaluate_receipt(self.suite, receipt, FINISHED)["status"],
                "inconclusive",
            )
