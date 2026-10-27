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
    return {
        "cases": [
            {"id": case["id"], "states": copy.deepcopy(case["expected"])}
            for case in suite["cases"]
        ]
    }


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
            [self.receipt["cases"][0]] * 8,
        ):
            self.assertEqual(
                evaluate_receipt(self.suite, {"cases": cases}, FINISHED)["status"],
                "inconclusive",
            )

    def test_cannot_replace_builtin_suite_with_other_cases_or_incomplete_states(self):
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
