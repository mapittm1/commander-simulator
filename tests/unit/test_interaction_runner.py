"""Boundary tests for bounded native execution and multi-file provenance."""

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
    PARTITIONS,
    load_suite,
    oracle_fingerprint,
)
from tests.support.interaction_fixture import synthetic_receipt

# Test names describe behavior.
# pylint: disable=missing-function-docstring

FINISHED = {"status": "finished", "returncode": 0}


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
            (
                "build_native_bridge",
                {
                    "class_sha256": "fixture-class",
                    "priority_class_sha256": "fixture-priority",
                },
            ),
            ("bridge_unchanged", True),
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
        cases = synthetic_receipt(self.suite)["cases"]
        header = {
            key: inputs[key]
            for key in (
                "schema_version",
                "suite_id",
                "suite_hash",
                "engine",
                "bridge_version",
            )
        }
        for name, case_ids in PARTITIONS.items():
            receipt = {
                "cases": [case for case in cases if case["id"] in case_ids],
                **header,
                "partition_id": name,
                "native_class_sha256": inputs["native_bridge"]["class_sha256"],
                "priority_class_sha256": inputs["native_bridge"][
                    "priority_class_sha256"
                ],
                "java_version": "17.0.20.1",
            }
            (self.run / f"observations-{name}.json").write_text(
                json.dumps(receipt), encoding="utf-8"
            )
        log.write_text("Synthetic worker evidence only\n", encoding="utf-8")
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
        self.assertEqual(
            [part["partition_id"] for part in report["partition_results"]],
            list(PARTITIONS),
        )
        self.assertTrue(
            all(part["observations_sha256"] for part in report["partition_results"])
        )
        self.assertIn("responses.py", report["source_hashes"])
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
            path = self.run / "observations-equipment.json"
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

    def _alter_partition(self, change):
        def execute(*args):
            result = self._execute(*args)
            path = self.run / "observations-equipment.json"
            receipt = json.loads(path.read_text(encoding="utf-8"))
            change(receipt)
            path.write_text(json.dumps(receipt), encoding="utf-8")
            return result

        with patch(
            "commander.engine.interactions.runner.run_bounded", side_effect=execute
        ):
            return run_interaction_suite(self.db, self.settings)

    def test_wrong_partition_identity_cannot_be_relabelled_as_valid_evidence(self):
        report = self._alter_partition(
            lambda receipt: receipt.update(partition_id="damage")
        )
        self.assertEqual(report["status"], "inconclusive")
        self.assertEqual(len(report["partition_results"]), 9)
        self.assertEqual(
            [part["status"] for part in report["partition_results"]],
            [
                "inconclusive",
                "passed",
                "passed",
                "passed",
                "passed",
                "passed",
                "passed",
                "passed",
                "passed",
            ],
        )

    def test_one_observed_failure_still_fails_the_complete_report(self):
        def change(receipt):
            receipt["cases"][0]["states"]["after_second"]["attached"] = False

        report = self._alter_partition(change)
        self.assertEqual(report["status"], "failed")
        self.assertEqual(report["partition_results"][0]["status"], "failed")
        self.assertEqual(len(report["checks"]), 41)

    def test_conflicting_native_runtime_versions_invalidate_all_results(self):
        report = self._alter_partition(
            lambda receipt: receipt.update(java_version="21.0.1")
        )
        self.assertEqual(report["status"], "inconclusive")
        self.assertEqual(report["checks"], [])

    def test_missing_single_partition_retains_other_partition_results(self):
        def execute(*args):
            result = self._execute(*args)
            (self.run / "observations-counter_chain.json").unlink()
            return result

        with patch(
            "commander.engine.interactions.runner.run_bounded", side_effect=execute
        ):
            report = run_interaction_suite(self.db, self.settings)
        self.assertEqual(report["status"], "inconclusive")
        self.assertEqual(report["partition_results"][-1]["status"], "inconclusive")
        self.assertTrue(
            all(part["status"] == "passed" for part in report["partition_results"][:-1])
        )

    def test_nonzero_exit_with_complete_receipts_is_inconclusive(self):
        def execute(*args):
            self._execute(*args)
            return {"status": "finished", "returncode": 1}

        with patch(
            "commander.engine.interactions.runner.run_bounded", side_effect=execute
        ):
            report = run_interaction_suite(self.db, self.settings)
        self.assertEqual(report["status"], "inconclusive")
        self.assertEqual(report["checks"], [])

    def test_changed_definitions_before_execution_cannot_launch_worker(self):
        with (
            patch(
                "commander.engine.interactions.runner.definitions_unchanged",
                return_value=False,
            ),
            patch("commander.engine.interactions.runner.run_bounded") as execute,
            self.assertRaisesRegex(ValueError, "changed during preflight"),
        ):
            run_interaction_suite(self.db, self.settings)
        execute.assert_not_called()

    def test_changed_definition_after_execution_invalidates_all_partitions(self):
        with (
            patch(
                "commander.engine.interactions.runner.definitions_unchanged",
                side_effect=[True, False],
            ),
            patch(
                "commander.engine.interactions.runner.run_bounded",
                side_effect=self._execute,
            ),
        ):
            report = run_interaction_suite(self.db, self.settings)
        self.assertEqual(report["status"], "inconclusive")
        self.assertEqual(report["checks"], [])

    def test_oversized_generated_inputs_are_rejected_before_launch(self):
        card = next(iter(self.cards.values()))
        card["oracle_text"] = "x" * 131073
        self.suite["cards"][0]["oracle_sha256"] = oracle_fingerprint(card)
        with (
            patch("commander.engine.interactions.runner.run_bounded") as execute,
            self.assertRaisesRegex(ValueError, "size limit"),
        ):
            run_interaction_suite(self.db, self.settings)
        execute.assert_not_called()
        self.assertFalse((self.run / "interaction-inputs.json").exists())

    def test_changed_compiled_controller_invalidates_all_partitions(self):
        with (
            patch(
                "commander.engine.interactions.runner.bridge_unchanged",
                return_value=False,
            ),
            patch(
                "commander.engine.interactions.runner.run_bounded",
                side_effect=self._execute,
            ),
        ):
            report = run_interaction_suite(self.db, self.settings)
        self.assertEqual(report["status"], "inconclusive")
        self.assertEqual(report["checks"], [])

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
