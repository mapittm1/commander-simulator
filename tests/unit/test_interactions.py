"""Counterexamples and missing evidence must never pass controlled interaction checks."""

import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from commander.engine.interactions.suite import (
    evaluate_receipt,
    load_suite,
    oracle_fingerprint,
    read_bounded_json,
)
from commander.engine.interactions.worker import save_observations
from tests.support.interaction_fixture import synthetic_receipt

# Test names describe behavior.
# pylint: disable=missing-function-docstring

FINISHED = {"status": "finished", "returncode": 0}


class StateVerifierTests(unittest.TestCase):
    """Rules-based expectations are checked at each checkpoint, including negative cases."""

    def setUp(self):
        self.suite = load_suite()
        self.receipt = synthetic_receipt(self.suite)

    def test_all_required_checkpoints_are_needed(self):
        self.assertEqual(
            evaluate_receipt(self.suite, self.receipt, FINISHED)["status"], "passed"
        )
        del self.receipt["cases"][0]["states"]["after_first"]
        self.assertEqual(
            evaluate_receipt(self.suite, self.receipt, FINISHED)["status"],
            "inconclusive",
        )

    def test_rules_counterexamples_fail(self):
        mutations = (
            (0, "after_damage", "target_count", 1),
            (0, "after_damage", "tokens", 1),
            (0, "after_first", "equipment_zone", "Battlefield"),
            (0, "after_second", "attached", False),
            (0, "after_second", "token_power", 1),
            (0, "after_second", "token_menace", False),
            (2, "after_second", "equipment_zone", "Battlefield"),
            (2, "after_second", "equipment_owned_by_actor", True),
            (3, "after_second", "equipment_zone", "Battlefield"),
            (4, "after_second", "equipment_zone", "Graveyard"),
            (5, "after_second", "tokens", 0),
            (6, "after_second", "attached", True),
            (7, "after_damage", "stack", 1),
            (7, "after_second", "tokens", 1),
        )
        for index, stage, field, value in mutations:
            with self.subTest(case=index, stage=stage, field=field):
                changed = copy.deepcopy(self.receipt)
                changed["cases"][index]["states"][stage][field] = value
                result = evaluate_receipt(self.suite, changed, FINISHED)
                self.assertEqual(result["status"], "failed")
                self.assertTrue(result["checks"][index]["mismatches"])

    def test_wrong_initial_state_is_not_a_rules_conclusion(self):
        self.receipt["cases"][0]["states"]["before"]["life"] = 20
        self.assertEqual(
            evaluate_receipt(self.suite, self.receipt, FINISHED)["status"],
            "inconclusive",
        )

    def test_prevention_replacement_and_countering_counterexamples_fail(self):
        mutations = (
            ("combat_prevented", "after_damage", "life", 37),
            ("combat_prevented", "after_first", "tokens", 1),
            ("noncombat_prevented", "after_damage", "life", 37),
            ("combat_unaffected_by_noncombat_prevention", "after_damage", "life", 40),
            ("combat_doubled", "after_damage", "life", 37),
            ("combat_doubled", "after_damage", "life", 28),
            ("combat_doubled", "after_first", "tokens", 2),
            ("noncombat_doubled", "after_damage", "life", 37),
            ("noncombat_doubled", "after_damage", "stack", 1),
            ("doubled_damage_prevented", "after_damage", "life", 34),
            ("doubled_damage_prevented", "after_first", "tokens", 1),
            ("initial_trigger_countered", "before_first", "stack", 1),
            ("initial_trigger_countered", "before_first", "spell_target_count", 0),
            (
                "initial_trigger_countered",
                "before_first",
                "counter_targets_pending_trigger",
                False,
            ),
            ("initial_trigger_countered", "after_first", "tokens", 1),
            (
                "initial_trigger_countered",
                "after_second",
                "counter_in_graveyard",
                False,
            ),
            ("reflexive_trigger_countered", "after_response", "stack", 1),
            (
                "reflexive_trigger_countered",
                "after_response",
                "counter_targets_pending_trigger",
                False,
            ),
            (
                "reflexive_trigger_countered",
                "after_second",
                "equipment_zone",
                "Battlefield",
            ),
            ("reflexive_trigger_countered", "after_second", "tokens", 0),
        )
        for case_id, stage, field, value in mutations:
            with self.subTest(case=case_id, stage=stage, field=field):
                changed = copy.deepcopy(self.receipt)
                case = next(case for case in changed["cases"] if case["id"] == case_id)
                case["states"][stage][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, changed, FINISHED)["status"], "failed"
                )

    def test_counter_target_identity_is_checked_independently_of_summary_flags(self):
        for identity, stage, previous in (
            ("initial_trigger_countered", "before_first", "after_damage"),
            ("reflexive_trigger_countered", "after_response", "after_first"),
        ):
            with self.subTest(case=identity):
                changed = copy.deepcopy(self.receipt)
                case = next(case for case in changed["cases"] if case["id"] == identity)
                self.assertTrue(
                    case["states"][stage]["counter_targets_pending_trigger"]
                )
                case["states"][stage]["spell_target_ids"] = [99999]
                self.assertEqual(
                    evaluate_receipt(self.suite, changed, FINISHED)["status"], "failed"
                )
                case["states"][stage]["spell_target_ids"] = case["states"][stage][
                    "stack_ability_ids"
                ][1:]
                case["states"][previous]["stack_ability_ids"] = [99999]
                self.assertEqual(
                    evaluate_receipt(self.suite, changed, FINISHED)["status"], "failed"
                )

    def test_missing_counter_target_ids_are_inconclusive(self):
        case = next(
            case
            for case in self.receipt["cases"]
            if case["id"] == "reflexive_trigger_countered"
        )
        del case["states"]["after_response"]["spell_target_ids"]
        self.assertEqual(
            evaluate_receipt(self.suite, self.receipt, FINISHED)["status"],
            "inconclusive",
        )

    def test_inactive_prevention_fixture_is_not_a_rules_conclusion(self):
        case = next(
            case
            for case in self.receipt["cases"]
            if case["id"] == "noncombat_prevented"
        )
        case["states"]["before"]["noncombat_preventer_loyalty"] = 0
        self.assertEqual(
            evaluate_receipt(self.suite, self.receipt, FINISHED)["status"],
            "inconclusive",
        )

    def test_missing_fields_and_harness_errors_are_not_exercised(self):
        changed = copy.deepcopy(self.receipt)
        changed["cases"][0]["error"] = "Native setup failed"
        self.assertEqual(
            evaluate_receipt(self.suite, changed, FINISHED)["status"], "inconclusive"
        )
        del self.receipt["cases"][0]["states"]["after_second"]["attached"]
        self.assertEqual(
            evaluate_receipt(self.suite, self.receipt, FINISHED)["status"],
            "inconclusive",
        )


class CardTargetVerifierTests(unittest.TestCase):
    """Revalidate selected card objects rather than trusting matching card IDs."""

    def setUp(self):
        self.suite = load_suite()
        self.receipt = synthetic_receipt(self.suite)

    def test_target_legality_counterexamples_fail(self):
        mutations = (
            ("graveyard_targeting_blocked", "after_first", "stack", 1),
            ("graveyard_targeting_blocked", "after_first", "target_count", 1),
            ("graveyard_targeting_blocked", "after_first", "tokens", 0),
            (
                "graveyard_targeting_blocked",
                "after_second",
                "equipment_zone",
                "Battlefield",
            ),
            (
                "targeting_blocked_in_response",
                "after_response",
                "graveyard_guard_active",
                False,
            ),
            (
                "targeting_blocked_in_response",
                "after_second",
                "equipment_zone",
                "Battlefield",
            ),
            (
                "targeting_restored_before_selection",
                "before_first",
                "graveyard_guard_active",
                True,
            ),
            ("targeting_restored_before_selection", "after_first", "stack", 0),
            ("targeting_restored_before_selection", "after_second", "attached", False),
            ("target_zone_roundtrip", "after_second", "equipment_zone", "Battlefield"),
            ("target_zone_roundtrip", "after_second", "token_power", 3),
        )
        for identity, stage, field, value in mutations:
            with self.subTest(case=identity, stage=stage, field=field):
                receipt = copy.deepcopy(self.receipt)
                case = next(case for case in receipt["cases"] if case["id"] == identity)
                case["states"][stage][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
                )

    def test_raw_card_identity_mismatches_fail_even_with_matching_summary_flags(self):
        mutations = (
            ("target_timestamps", [999]),
            ("targets", [999]),
            ("equipment_timestamp", 10),
            ("equipment_id", 999),
            (
                "equipment_transitions",
                [
                    {"id": 1019, "timestamp": 10, "zone": "Exile"},
                    {"id": 1019, "timestamp": 12, "zone": "Graveyard"},
                ],
            ),
            (
                "equipment_transitions",
                [
                    {"id": 1019, "timestamp": 11, "zone": "Hand"},
                    {"id": 1019, "timestamp": 12, "zone": "Graveyard"},
                ],
            ),
        )
        for field, value in mutations:
            with self.subTest(field=field, value=value):
                receipt = copy.deepcopy(self.receipt)
                state = next(
                    case
                    for case in receipt["cases"]
                    if case["id"] == "target_zone_roundtrip"
                )["states"]["after_response"]
                self.assertTrue(state["equipment_targeted"])
                state[field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
                )
        for identity in (
            "targeting_blocked_in_response",
            "targeting_restored_before_selection",
        ):
            receipt = copy.deepcopy(self.receipt)
            case = next(case for case in receipt["cases"] if case["id"] == identity)
            case["states"]["after_first"]["target_timestamps"] = [999]
            self.assertEqual(
                evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
            )

    def test_missing_or_malformed_card_identity_is_inconclusive(self):
        for field, value in (
            ("target_timestamps", None),
            ("target_timestamps", [True]),
            ("targets", []),
            ("equipment_id", "1019"),
            ("equipment_timestamp", -1),
            ("equipment_transitions", []),
            ("equipment_transitions", [None, None]),
        ):
            with self.subTest(field=field, value=value):
                receipt = copy.deepcopy(self.receipt)
                next(
                    case
                    for case in receipt["cases"]
                    if case["id"] == "target_zone_roundtrip"
                )["states"]["after_response"][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"],
                    "inconclusive",
                )
        receipt = copy.deepcopy(self.receipt)
        del next(
            case for case in receipt["cases"] if case["id"] == "target_zone_roundtrip"
        )["states"]["after_response"]["equipment_transitions"]
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "inconclusive"
        )

    def test_missing_initial_target_restriction_is_inconclusive(self):
        case = next(
            case
            for case in self.receipt["cases"]
            if case["id"] == "graveyard_targeting_blocked"
        )
        case["states"]["before"]["graveyard_guard_active"] = False
        self.assertEqual(
            evaluate_receipt(self.suite, self.receipt, FINISHED)["status"],
            "inconclusive",
        )


class ProtectionVerifierTests(unittest.TestCase):
    """Reject false defense, targeting, attachment, and native response claims."""

    def setUp(self):
        self.suite = load_suite()
        self.receipt = synthetic_receipt(self.suite)

    def test_defense_and_attachment_counterexamples_fail(self):
        mutations = (
            (
                "assassin_protection_artifacts",
                "after_response",
                "assassin_protection_artifacts",
                False,
            ),
            ("assassin_protection_artifacts", "after_second", "attached", True),
            (
                "assassin_protection_artifacts",
                "after_second",
                "equipment_zone",
                "Graveyard",
            ),
            ("assassin_protection_artifacts", "after_second", "token_power", 3),
            ("assassin_hexproof", "after_response", "assassin_hexproof", False),
            (
                "assassin_hexproof",
                "after_response",
                "opponent_can_target_assassin",
                True,
            ),
            ("assassin_hexproof", "after_response", "actor_can_target_assassin", False),
            ("assassin_hexproof", "after_second", "attached", False),
            ("assassin_hexproof", "after_second", "token_power", 3),
            ("assassin_hexproof", "after_second", "token_toughness", 1),
            ("assassin_shroud", "after_response", "assassin_shroud", False),
            ("assassin_shroud", "after_response", "actor_can_target_assassin", True),
            ("assassin_shroud", "after_response", "opponent_can_target_assassin", True),
            ("assassin_shroud", "after_second", "attached", False),
        )
        for identity, stage, field, value in mutations:
            with self.subTest(case=identity, stage=stage, field=field):
                receipt = copy.deepcopy(self.receipt)
                case = next(case for case in receipt["cases"] if case["id"] == identity)
                case["states"][stage][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
                )

    def test_response_must_target_original_token_above_the_pending_trigger(self):
        for field, value in (
            ("targets", [999]),
            ("target_timestamps", [99]),
            ("stack_ability_ids", [300, 999]),
            ("stack_ability_ids", [300, 300]),
            ("zone", "Exile"),
        ):
            with self.subTest(field=field, value=value):
                receipt = copy.deepcopy(self.receipt)
                state = next(
                    case
                    for case in receipt["cases"]
                    if case["id"] == "assassin_hexproof"
                )["states"]["after_response"]
                state["response_spell"][field] = value
                self.assertTrue(state["assassin_hexproof"])
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
                )
        receipt = copy.deepcopy(self.receipt)
        state = next(
            case for case in receipt["cases"] if case["id"] == "assassin_shroud"
        )["states"]["after_response"]
        state["targets"] = state["token_ids"]
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
        )

    def test_missing_or_malformed_response_evidence_is_inconclusive(self):
        for field, value in (
            ("targets", None),
            ("targets", [True]),
            ("target_timestamps", []),
            ("stack_ability_ids", [1]),
            ("zone", None),
        ):
            with self.subTest(field=field, value=value):
                receipt = copy.deepcopy(self.receipt)
                state = next(
                    case
                    for case in receipt["cases"]
                    if case["id"] == "assassin_hexproof"
                )["states"]["after_response"]
                state["response_spell"][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"],
                    "inconclusive",
                )
        receipt = copy.deepcopy(self.receipt)
        case = next(
            case for case in receipt["cases"] if case["id"] == "assassin_shroud"
        )
        del case["states"]["after_response"]["response_spell"]
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "inconclusive"
        )


class DetachmentVerifierTests(unittest.TestCase):
    """Require witnessed attachment, native protection, and stable object identities."""

    def setUp(self):
        self.suite = load_suite()
        self.receipt = synthetic_receipt(self.suite)

    def _case(self, receipt):
        return next(
            case
            for case in receipt["cases"]
            if case["id"] == "protection_detaches_equipment"
        )

    def test_attachment_and_detachment_counterexamples_fail(self):
        for stage, field, value in (
            ("after_second", "attached", False),
            ("after_second", "token_power", 1),
            ("after_second", "assassin_protection_artifacts", True),
            ("after_detachment", "attached", True),
            ("after_detachment", "token_power", 3),
            ("after_detachment", "assassin_protection_artifacts", False),
            ("after_detachment", "equipment_zone", "Graveyard"),
            ("after_detachment", "tokens", 0),
        ):
            with self.subTest(stage=stage, field=field):
                receipt = copy.deepcopy(self.receipt)
                self._case(receipt)["states"][stage][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
                )

    def test_conflicting_raw_identities_fail_despite_matching_summary_flags(self):
        for field, value in (
            ("equipment_id", 999),
            ("equipment_timestamp", 999),
            ("token_ids", [999]),
            ("token_timestamps", [999]),
            ("equipment_attached_to_id", 999),
            ("stack_ability_ids", [999]),
        ):
            with self.subTest(field=field):
                receipt = copy.deepcopy(self.receipt)
                state = self._case(receipt)["states"]["after_detachment"]
                self.assertFalse(state["attached"])
                state[field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
                )
        for field, value in (
            ("targets", [999]),
            ("target_timestamps", [999]),
            ("zone", "Exile"),
        ):
            receipt = copy.deepcopy(self.receipt)
            self._case(receipt)["states"]["after_detachment"]["response_spell"][
                field
            ] = value
            self.assertEqual(
                evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
            )
        receipt = copy.deepcopy(self.receipt)
        self._case(receipt)["states"]["after_second"]["equipment_attached_to_id"] = 999
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "failed"
        )

    def test_missing_or_malformed_detachment_evidence_is_inconclusive(self):
        for field, value in (
            ("targets", []),
            ("target_timestamps", [True]),
            ("stack_ability_ids", []),
            ("zone", None),
        ):
            with self.subTest(field=field):
                receipt = copy.deepcopy(self.receipt)
                self._case(receipt)["states"]["after_detachment"]["response_spell"][
                    field
                ] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"],
                    "inconclusive",
                )
        receipt = copy.deepcopy(self.receipt)
        del self._case(receipt)["states"]["after_detachment"]["response_spell"]
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "inconclusive"
        )

    def test_only_the_detachment_case_accepts_its_extra_checkpoint(self):
        receipt = copy.deepcopy(self.receipt)
        del self._case(receipt)["states"]["after_detachment"]
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "inconclusive"
        )
        receipt = copy.deepcopy(self.receipt)
        receipt["cases"][0]["states"]["after_detachment"] = {}
        self.assertEqual(
            evaluate_receipt(self.suite, receipt, FINISHED)["status"], "inconclusive"
        )
        for identity, extra in (
            ("protection_detaches_equipment", False),
            ("baseline", True),
        ):
            suite = copy.deepcopy(self.suite)
            case = next(case for case in suite["cases"] if case["id"] == identity)
            if extra:
                case["expected"]["after_detachment"] = {"attached": False}
            else:
                del case["expected"]["after_detachment"]
            with (
                patch(
                    "commander.engine.interactions.suite.load_definitions",
                    return_value=suite,
                ),
                self.assertRaisesRegex(ValueError, "checkpoint"),
            ):
                load_suite()


class ReceiptBoundaryTests(unittest.TestCase):
    """Reject incomplete, malformed, or incompatible verification evidence."""

    def setUp(self):
        self.suite = load_suite()
        self.receipt = synthetic_receipt(self.suite)

    def test_bool_is_not_an_integer_observation(self):
        self.receipt["cases"][0]["states"]["after_damage"]["tokens"] = False
        self.assertEqual(
            evaluate_receipt(self.suite, self.receipt, FINISHED)["status"], "failed"
        )

    def test_process_failure_cannot_be_overridden_by_good_state_data(self):
        for process in (
            {"status": "timeout", "returncode": 0},
            {"status": "log_limit", "returncode": 0},
            {"status": "finished", "returncode": 1},
        ):
            with self.subTest(process=process):
                self.assertEqual(
                    evaluate_receipt(self.suite, self.receipt, process)["status"],
                    "inconclusive",
                )

    def test_missing_duplicate_and_reordered_cases_are_inconclusive(self):
        for cases in (
            self.receipt["cases"][:-1],
            list(reversed(self.receipt["cases"])),
            [self.receipt["cases"][0]] * len(self.receipt["cases"]),
        ):
            self.assertEqual(
                evaluate_receipt(self.suite, {"cases": cases}, FINISHED)["status"],
                "inconclusive",
            )

    def test_cannot_replace_builtin_suite_with_other_cases_or_incomplete_states(self):
        legacy = copy.deepcopy(self.suite)
        legacy["suite_id"] = "raton_equipment_v1"
        with (
            patch(
                "commander.engine.interactions.suite.load_definitions",
                return_value=legacy,
            ),
            self.assertRaisesRegex(ValueError, "version"),
        ):
            load_suite()
        changed = [copy.deepcopy(self.suite) for _ in range(3)]
        changed[0]["cases"][0]["id"] = "arbitrary-user-state"
        changed[1]["cases"].append(None)
        changed[2]["cards"][1]["role"] = "source"
        for candidate in changed:
            with (
                patch(
                    "commander.engine.interactions.suite.load_definitions",
                    return_value=candidate,
                ),
                self.assertRaises(ValueError),
            ):
                load_suite()
        changed = copy.deepcopy(self.suite)
        del changed["cases"][0]["expected"]["before"]
        with (
            patch(
                "commander.engine.interactions.suite.load_definitions",
                return_value=changed,
            ),
            self.assertRaises(ValueError),
        ):
            load_suite()

    def test_json_size_duplicates_root_and_recursion_are_bounded(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "receipt.json"
            for raw in (
                b"x" * 131073,
                b'{"key":1,"key":2}',
                b"[]",
                b'{"x":' * 2000 + b"0" + b"}" * 2000,
            ):
                path.write_bytes(raw)
                with self.assertRaises(ValueError):
                    read_bounded_json(path)

    def test_oracle_fingerprint_ignores_printing_metadata_but_not_rules(self):
        self.assertNotEqual(
            oracle_fingerprint({"loyalty": "5"}), oracle_fingerprint({"loyalty": "4"})
        )
        card = {"name": "Card", "oracle_id": "oracle", "oracle_text": "Draw a card."}
        self.assertEqual(
            oracle_fingerprint(card),
            oracle_fingerprint({**card, "artist": "Another", "id": "another-printing"}),
        )
        self.assertNotEqual(
            oracle_fingerprint(card),
            oracle_fingerprint({**card, "oracle_text": "Draw two cards."}),
        )

    def test_depth_guard_ignores_escaped_quotes_and_braces_in_strings(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "quoted.json"
            expected = {"text": '"' + "{" * 100 + "\\"}
            path.write_text(json.dumps(expected), encoding="utf-8")
            self.assertEqual(read_bounded_json(path), expected)

    def test_worker_receipt_size_is_bounded_before_writing_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "observations.json"
            with self.assertRaises(ValueError):
                save_observations(path, {"large": "x" * 131073})
            self.assertFalse(path.exists())
            save_observations(path, {"kept": True})
            with self.assertRaises(FileExistsError):
                save_observations(path, {"kept": False})
            self.assertEqual(read_bounded_json(path), {"kept": True})
