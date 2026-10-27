"""Counterexamples and missing evidence must never pass controlled interaction checks."""

import copy
import io
import json
import tempfile
import unittest
from contextlib import ExitStack, redirect_stderr, redirect_stdout
from importlib import metadata
from pathlib import Path
from unittest.mock import patch

from commander.catalog.database import connect
from commander.cli.app import main
from commander.engine.forge_config import EngineSettings
from commander.engine.interactions.runner import require_bridge, run_interaction_suite
from commander.engine.interactions.suite import (
    evaluate_receipt,
    load_suite,
    oracle_fingerprint,
    read_bounded_json,
)
from commander.engine.interactions.worker import save_observations

# Test names describe behavior.
# pylint: disable=missing-function-docstring

FINISHED = {"status": "finished", "returncode": 0}


def synthetic_receipt(suite: dict) -> dict:
    """Build verifier input only; this is not a native-engine conformance result."""
    cases = []
    for index, case in enumerate(suite["cases"]):
        states = copy.deepcopy(case["expected"])
        initial, reflexive, counter = 100 + index * 3, 101 + index * 3, 102 + index * 3
        for stage, state in states.items():
            pending = (
                initial if stage in {"after_damage", "before_first"} else reflexive
            )
            if state["counter_on_stack"]:
                state["stack_ability_ids"] = [counter, pending]
                state["spell_target_ids"] = [pending]
            else:
                state["stack_ability_ids"] = [pending] if state["stack"] else []
                state["spell_target_ids"] = []
            state["equipment_id"] = 1000 + index
            state["equipment_timestamp"] = 10
            state["targets"] = [1000 + index] if state.get("equipment_targeted") else []
            state["target_timestamps"] = [10] if state.get("equipment_targeted") else []
        if case["id"] == "target_zone_roundtrip":
            for stage in ("after_response", "after_second"):
                states[stage]["equipment_timestamp"] = 12
            states["after_response"]["equipment_transitions"] = [
                {"id": 1000 + index, "timestamp": 11, "zone": "Exile"},
                {"id": 1000 + index, "timestamp": 12, "zone": "Graveyard"},
            ]
        cases.append({"id": case["id"], "states": states})
    return {"cases": cases}


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
                state = receipt["cases"][-1]["states"]["after_response"]
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
                receipt["cases"][-1]["states"]["after_response"][field] = value
                self.assertEqual(
                    evaluate_receipt(self.suite, receipt, FINISHED)["status"],
                    "inconclusive",
                )
        receipt = copy.deepcopy(self.receipt)
        del receipt["cases"][-1]["states"]["after_response"]["equipment_transitions"]
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
                "commander.engine.interactions.suite.read_bounded_json",
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
                    "commander.engine.interactions.suite.read_bounded_json",
                    return_value=candidate,
                ),
                self.assertRaises(ValueError),
            ):
                load_suite()
        changed = copy.deepcopy(self.suite)
        del changed["cases"][0]["expected"]["before"]
        with (
            patch(
                "commander.engine.interactions.suite.read_bounded_json",
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


class RunnerBoundaryTests(unittest.TestCase):
    """Use fake native execution to exercise admission, receipt integrity, and failure paths."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.root = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        self.run = self.root / "run"
        self.run.mkdir()
        java = self.root / "bin/java.exe"
        java.parent.mkdir()
        java.write_bytes(b"fixture")
        (java.parent / "server").mkdir()
        (java.parent / "server/jvm.dll").write_bytes(b"fixture")
        self.settings = EngineSettings(self.root, java)
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        self.suite = load_suite()
        self.cards = {}
        for expected in self.suite["cards"]:
            record = {"name": expected["name"], "oracle_id": expected["oracle_id"]}
            self.cards[expected["oracle_id"]] = record
            expected["oracle_sha256"] = oracle_fingerprint(record)
        self.engine = {
            "jar_name": "test.jar",
            "forge_version": "test",
            "jar_sha256": "jar",
            "cards_sha256": "cards",
        }
        for name, value in (
            ("load_suite", self.suite),
            (
                "card_index",
                {card["name"]: card["name"] for card in self.cards.values()},
            ),
            ("require_current_catalog", {"id": 1}),
            ("_new_run", self.run),
        ):
            stack.enter_context(
                patch(
                    "commander.engine.interactions.runner." + name, return_value=value
                )
            )
        stack.enter_context(
            patch.object(EngineSettings, "verify", return_value=self.engine)
        )
        stack.enter_context(
            patch(
                "commander.engine.interactions.runner.metadata.version",
                return_value="1.7.1",
            )
        )
        stack.enter_context(
            patch(
                "commander.engine.interactions.runner.get_card_details",
                side_effect=lambda _connection, identity: self.cards[identity],
            )
        )

    def _execute(self, command, _cwd, log, _limits):
        inputs = json.loads(Path(command[-1]).read_text(encoding="utf-8"))
        receipt = {
            **synthetic_receipt(self.suite),
            "schema_version": 1,
            "suite_hash": inputs["suite_hash"],
            "engine": inputs["engine"],
            "bridge_version": "1.7.1",
            "java_version": "17.0.20.1",
        }
        log.write_text("Synthetic worker evidence only\n", encoding="utf-8")
        (self.run / "observations.json").write_text(
            json.dumps(receipt), encoding="utf-8"
        )
        return FINISHED

    def test_bounded_isolated_process_and_report_preserve_provenance(self):
        with patch(
            "commander.engine.interactions.runner.run_bounded",
            side_effect=self._execute,
        ) as execute:
            report = run_interaction_suite(self.db, self.settings)
        self.assertEqual(report["status"], "passed")
        command, _cwd, _log, limits = execute.call_args.args
        self.assertIn("-I", command)
        self.assertEqual(limits.timeout, 90)
        self.assertNotIn("JAVA_TOOL_OPTIONS", limits.environment)
        self.assertTrue((self.run / "interaction-report.json").is_file())
        self.assertTrue(report["observations_sha256"])
        self.assertFalse((self.root / ".commander-simulator.lock").exists())

    def test_changed_oracle_cannot_launch_worker(self):
        next(iter(self.cards.values()))["oracle_text"] = "Changed Oracle text"
        with (
            patch("commander.engine.interactions.runner.run_bounded") as execute,
            self.assertRaisesRegex(ValueError, "Oracle characteristics changed"),
        ):
            run_interaction_suite(self.db, self.settings)
        execute.assert_not_called()

    def test_missing_engine_export_cannot_launch_worker(self):
        with (
            patch("commander.engine.interactions.runner.card_index", return_value={}),
            patch("commander.engine.interactions.runner.run_bounded") as execute,
            self.assertRaisesRegex(ValueError, "engine export"),
        ):
            run_interaction_suite(self.db, self.settings)
        execute.assert_not_called()

    def test_missing_receipt_never_passes_even_with_zero_exit(self):
        def no_receipt(_command, _cwd, log, _limits):
            log.write_text("No state evidence", encoding="utf-8")
            return FINISHED

        with patch(
            "commander.engine.interactions.runner.run_bounded", side_effect=no_receipt
        ):
            report = run_interaction_suite(self.db, self.settings)
        self.assertEqual(report["status"], "inconclusive")

    def test_tampered_receipt_is_inconclusive(self):
        def tamper(*args):
            result = self._execute(*args)
            path = self.run / "observations.json"
            receipt = json.loads(path.read_text(encoding="utf-8"))
            receipt["suite_hash"] = "changed"
            path.write_text(json.dumps(receipt), encoding="utf-8")
            return result

        with patch(
            "commander.engine.interactions.runner.run_bounded", side_effect=tamper
        ):
            report = run_interaction_suite(self.db, self.settings)
        self.assertEqual(report["status"], "inconclusive")

    def test_changed_engine_during_execution_invalidates_state_results(self):
        changed = {**self.engine, "jar_sha256": "changed"}
        with (
            patch.object(EngineSettings, "verify", side_effect=[self.engine, changed]),
            patch(
                "commander.engine.interactions.runner.run_bounded",
                side_effect=self._execute,
            ),
        ):
            report = run_interaction_suite(self.db, self.settings)
        self.assertEqual(report["status"], "inconclusive")
        self.assertIn("changed while running", report["reason"])

    def test_bridge_version_and_absence_give_actionable_errors(self):
        with (
            patch(
                "commander.engine.interactions.runner.metadata.version",
                side_effect=metadata.PackageNotFoundError,
            ),
            self.assertRaisesRegex(ValueError, "requirements-interactions"),
        ):
            require_bridge()
        with (
            patch(
                "commander.engine.interactions.runner.metadata.version",
                return_value="different",
            ),
            self.assertRaises(ValueError),
        ):
            require_bridge()

    def test_cli_only_returns_success_for_passed_interactions(self):
        path = self.root / "cli.sqlite3"
        for status in ("passed", "failed", "inconclusive"):
            with (
                patch(
                    "commander.cli.engine_commands.read_settings",
                    return_value=self.settings,
                ),
                patch(
                    "commander.cli.engine_commands.run_interaction_suite",
                    return_value={"status": status},
                ),
                redirect_stdout(io.StringIO()),
                redirect_stderr(io.StringIO()),
            ):
                if status == "passed":
                    main(["--database", str(path), "engine-interactions"])
                else:
                    with self.assertRaises(SystemExit) as raised:
                        main(["--database", str(path), "engine-interactions"])
                    self.assertEqual(raised.exception.code, 1)
