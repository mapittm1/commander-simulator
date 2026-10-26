"""Runnable CLI workflows preserve admission, progress, failure, and interruption evidence."""

import io
import json
from contextlib import redirect_stderr, redirect_stdout
from unittest.mock import patch

from commander.cli.app import main
from commander.cli.project_health import readiness_report
from commander.decks.pod_import import import_reviewed_pod
from commander.simulation.batch_store import cancel_job, job_summary
from commander.simulation.batches import BatchOptions, create_batch
from commander.simulation.workflow import finish_batch, save_report
from tests.support.batch_fixture import BatchFixture

# Test names express behavior.
# pylint: disable=missing-function-docstring


class WorkflowTests(BatchFixture):
    """Run real backend gates and storage with fake engine execution only."""

    def test_complete_workflow_emits_progress_only_after_terminal_attempts(self):
        job = self._create(3)
        observed = []

        def progress(index, result):
            state = self.db.execute(
                "SELECT state FROM simulation_attempts WHERE job_id=? AND game_index=?",
                (job["job_id"], index),
            ).fetchone()[0]
            observed.append((index, state, result["winner_pod_index"]))

        with patch(
            "commander.engine.forge_engine._execute_game",
            return_value={"status": "completed", "outcome": "win", "winner_seat": 1},
        ):
            report = finish_batch(
                self.db, self.settings, job["job_id"], self.path, progress
            )
        self.assertEqual(report["status"], "completed")
        self.assertEqual(
            observed, [(0, "completed", 0), (1, "completed", 1), (2, "completed", 2)]
        )
        self.assertEqual(
            report["batch"]["outcomes"]["wins_by_original_pod_index"], [1, 1, 1, 0]
        )
        self.assertNotIn("spec", report["batch"])
        self.assertEqual(report["diagnostics"]["coverage"]["games_included"], 0)

    def test_complete_workflow_stops_and_preserves_engine_failure(self):
        job = self._create(3)
        with patch(
            "commander.engine.forge_engine._execute_game",
            return_value={"status": "engine_timeout", "winner_seat": None},
        ):
            report = finish_batch(self.db, self.settings, job["job_id"], self.path)
        self.assertEqual(report["status"], "failed")
        self.assertEqual(
            (report["batch"]["attempted"], report["batch"]["failed"]), (1, 1)
        )
        self.assertEqual(
            report["attempt_preview"][0]["result"]["status"], "engine_timeout"
        )

    def test_interrupted_workflow_resumes_remaining_capacity_without_retrying_slot(
        self,
    ):
        job = self._create(3)
        with patch(
            "commander.engine.forge_engine._execute_game", side_effect=KeyboardInterrupt
        ):
            interrupted = finish_batch(self.db, self.settings, job["job_id"], self.path)
        self.assertEqual(interrupted["status"], "interrupted")
        with patch(
            "commander.engine.forge_engine._execute_game",
            return_value={"status": "completed", "outcome": "win", "winner_seat": 1},
        ) as engine:
            resumed = finish_batch(self.db, self.settings, job["job_id"], self.path)
        self.assertEqual(engine.call_count, 2)
        self.assertEqual(resumed["status"], "failed")
        self.assertEqual(
            (resumed["batch"]["completed"], resumed["batch"]["interrupted"]), (2, 1)
        )

    def test_cancelled_workflow_does_not_execute(self):
        job = self._create(3)
        cancel_job(self.db, job["job_id"])
        with patch("commander.engine.forge_engine._execute_game") as engine:
            report = finish_batch(self.db, self.settings, job["job_id"], self.path)
        engine.assert_not_called()
        self.assertEqual(report["status"], "cancelled")

    def test_report_never_overwrites_an_existing_file(self):
        path = self.path.parent / "report.json"
        save_report({"status": "first"}, path)
        with self.assertRaises(FileExistsError):
            save_report({"status": "second"}, path)
        self.assertEqual(json.loads(path.read_text())["status"], "first")

    def test_cli_workflow_json_is_not_polluted_by_progress(self):
        output, progress = io.StringIO(), io.StringIO()
        destination = self.path.parent / "cli-report.json"
        seats = [
            part
            for deck, version in self.seats
            for part in ("--seat", f"{deck}:{version}")
        ]
        with (
            patch(
                "commander.cli.workflow_commands.read_settings",
                return_value=self.settings,
            ),
            patch(
                "commander.engine.forge_engine._execute_game",
                return_value={
                    "status": "completed",
                    "outcome": "win",
                    "winner_seat": 1,
                },
            ),
            redirect_stdout(output),
            redirect_stderr(progress),
        ):
            main(
                [
                    "--database",
                    str(self.path),
                    "simulate",
                    *seats,
                    "--runs",
                    "2",
                    "--output",
                    str(destination),
                    "--json",
                ]
            )
        report = json.loads(output.getvalue())
        self.assertEqual(report["status"], "completed")
        self.assertIn("Game 2: completed", progress.getvalue())
        self.assertEqual(json.loads(destination.read_text())["batch"]["completed"], 2)

    def test_cli_rejects_existing_report_before_loading_engine(self):
        path = self.path.parent / "keep.json"
        path.write_text("keep")
        with (
            patch("commander.cli.workflow_commands.read_settings") as settings,
            redirect_stderr(io.StringIO()),
            self.assertRaises(SystemExit) as raised,
        ):
            main(["--database", str(self.path), "demo", "--output", str(path)])
        self.assertEqual(raised.exception.code, 1)
        settings.assert_not_called()
        self.assertEqual(path.read_text(), "keep")

    def test_cli_rejects_resume_option_changes(self):
        job = self._create(2)
        with (
            patch(
                "commander.cli.workflow_commands.read_settings",
                return_value=self.settings,
            ),
            redirect_stderr(io.StringIO()),
            self.assertRaises(SystemExit) as raised,
        ):
            main(
                [
                    "--database",
                    str(self.path),
                    "simulate",
                    "--resume",
                    job["job_id"],
                    "--runs",
                    "4",
                ]
            )
        self.assertEqual(raised.exception.code, 1)
        self.assertEqual(job_summary(self.db, job["job_id"])["attempted"], 0)

    def test_doctor_reports_catalog_and_engine_independently(self):
        with self.db:
            self.db.execute("DELETE FROM imports")
        with (
            patch(
                "commander.cli.project_health.read_settings", return_value=self.settings
            ),
            patch(
                "commander.cli.project_health.engine_health",
                return_value={"status": "ready"},
            ),
        ):
            report = readiness_report(self.db, self.path)
        self.assertEqual(report["status"], "needs_setup")
        self.assertEqual(
            [check["status"] for check in report["checks"]], ["blocked", "ready"]
        )

    def _pod_files(self):
        files = []
        for name in ("Leader A", "Leader B", "Leader C", "Leader D"):
            path = self.path.parent / (name + ".txt")
            path.write_text(
                f"Commander\n1 {name}\nMainboard\n99 Forest\n", encoding="utf-8"
            )
            files.append(path)
        return files

    def test_four_file_import_uses_normal_legality_and_review_services(self):
        files = self._pod_files()
        index = {
            name: name
            for name in ("Leader A", "Leader B", "Leader C", "Leader D", "Forest")
        }
        with patch("commander.decks.pod_import.card_index", return_value=index):
            seats = import_reviewed_pod(self.db, self.settings, files, 2, self.review)
        self.assertEqual(seats, [(5, 1), (6, 1), (7, 1), (8, 1)])
        create_batch(self.db, self.settings, seats, BatchOptions(1))

    def test_invalid_fourth_deck_does_not_save_first_three(self):
        files = self._pod_files()
        files[3].write_text("Commander\n1 Leader D\nMainboard\n100 Forest\n")
        with self.assertRaisesRegex(ValueError, "illegal"):
            import_reviewed_pod(self.db, self.settings, files, 2, self.review)
        self.assertEqual(self.db.execute("SELECT COUNT(*) FROM decks").fetchone()[0], 4)

    def test_unapproved_review_and_missing_scripts_do_not_import_decks(self):
        files = self._pod_files()
        incomplete = {
            **self.review,
            "checks": dict.fromkeys(self.review["checks"], False),
        }
        with self.assertRaisesRegex(ValueError, "incomplete"):
            import_reviewed_pod(self.db, self.settings, files, 2, incomplete)
        with (
            patch("commander.decks.pod_import.card_index", return_value={}),
            self.assertRaisesRegex(ValueError, "unsupported engine cards"),
        ):
            import_reviewed_pod(self.db, self.settings, files, 2, self.review)
        self.assertEqual(self.db.execute("SELECT COUNT(*) FROM decks").fetchone()[0], 4)
