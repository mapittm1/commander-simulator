"""Batch admission, durable progress, failure accounting, and concurrency regressions."""

import io
import json
import sqlite3
from contextlib import redirect_stdout
from datetime import datetime, timezone
from unittest.mock import patch

from commander.catalog.database import connect
from commander.cli.app import main
from commander.engine.forge_config import EngineSettings
from commander.engine.forge_engine import prepare_game
from commander.rules.brackets import save_bracket_review
from commander.simulation.batch_store import (
    attempt_results,
    cancel_job,
    claim_job,
    ensure_schema,
    job_summary,
    read_job,
)
from commander.simulation.batches import BatchOptions, plan_game, run_batch
from tests.support.batch_fixture import BatchFixture

# Behavior is expressed in test names.
# pylint: disable=missing-function-docstring


class BatchTests(BatchFixture):
    """Use real legality, review, snapshot, and persistence services with fake execution."""

    def test_run_count_bounds_are_enforced_before_engine_or_persistence(self):
        for count in (0, -1, 100001, True, 1.5, "100"):
            with self.subTest(count=count), self.assertRaises(ValueError):
                self._create(count)
        for count in (1, 100, 1000, 10000, 100000):
            BatchOptions(count).validate()
        with self.assertRaises(ValueError):
            BatchOptions(2, seed=2**63 - 1).validate()
        self.assertIsNone(
            self.db.execute(
                "SELECT name FROM sqlite_master WHERE name='simulation_jobs'"
            ).fetchone()
        )

    def test_100000_game_admission_is_lazy_and_has_no_attempt_rows(self):
        job = self._create(100000)
        self.assertEqual(job["requested"], 100000)
        self.assertEqual(job["attempted"], 0)
        self.assertEqual(job["outcomes"]["win_rates"], [None] * 4)

    def test_chunk_resume_rotates_seats_and_maps_winners_to_original_pod(self):
        job = self._create()
        first = self._worker(job["job_id"], 3)
        self.assertEqual(
            (first["status"], first["completed"], first["unattempted"]),
            ("paused", 3, 1),
        )
        final = self._worker(job["job_id"])
        self.assertEqual((final["status"], final["completed"]), ("completed", 4))
        self.assertEqual(final["outcomes"]["wins_by_original_pod_index"], [1, 1, 1, 1])
        self.assertEqual(final["outcomes"]["win_rates"], [0.25] * 4)
        spec = read_job(self.db, job["job_id"])["spec"]
        self.assertEqual(
            [plan_game(spec, index)["seed"] for index in range(4)], [42, 43, 44, 45]
        )
        with patch("commander.simulation.batches.run_frozen_game") as engine:
            self._worker(job["job_id"])
            engine.assert_not_called()

    def test_failed_games_stop_work_and_do_not_inflate_rates(self):
        job = self._create()
        failed = self._worker(
            job["job_id"], 4, {"status": "engine_timeout", "winner_seat": None}
        )
        self.assertEqual(
            (failed["status"], failed["attempted"], failed["failed"]), ("blocked", 1, 1)
        )
        self.assertEqual(failed["outcomes"]["completed_game_denominator"], 0)
        resumed = self._worker(job["job_id"])
        self.assertEqual(
            (resumed["attempted"], resumed["completed"], resumed["failed"]), (2, 1, 1)
        )

    def test_draws_remain_completed_without_inventing_a_winner(self):
        job = self._create(1)
        result = self._worker(
            job["job_id"],
            result={"status": "completed", "outcome": "draw", "winner_seat": None},
        )
        self.assertEqual(result["outcomes"]["draws"], 1)
        self.assertEqual(result["outcomes"]["win_rates"], [0.0] * 4)

    def test_malformed_engine_success_is_a_failed_attempt(self):
        job = self._create(1)
        result = self._worker(
            job["job_id"],
            result={"status": "completed", "outcome": "win", "winner_seat": True},
        )
        self.assertEqual((result["failed"], result["completed"]), (1, 0))

    def test_cancellation_before_launch_and_during_game(self):
        job = self._create()
        cancelled = cancel_job(self.db, job["job_id"])
        self.assertEqual(cancelled["status"], "cancelled")
        self.assertEqual(self._worker(job["job_id"])["attempted"], 0)
        job = self._create()

        def cancel_during_execution(*_):
            cancel_job(self.db, job["job_id"])
            return {"status": "completed", "outcome": "draw", "winner_seat": None}

        with patch(
            "commander.engine.forge_engine._execute_game",
            side_effect=cancel_during_execution,
        ):
            final = run_batch(self.db, self.settings, job["job_id"], 4)
        self.assertEqual(
            (final["status"], final["completed"], final["unattempted"]),
            ("cancelled", 1, 3),
        )

    def test_changed_catalog_blocks_without_attempting_a_game(self):
        job = self._create()
        with self.db:
            self.db.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,5)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )
        result = self._worker(job["job_id"])
        self.assertEqual((result["status"], result["attempted"]), ("blocked", 0))

    def test_changed_review_blocks_without_rebinding_a_frozen_job(self):
        job = self._create()
        save_bracket_review(self.db, self.seats[0][0], self.review)
        result = self._worker(job["job_id"])
        self.assertEqual((result["status"], result["attempted"]), ("blocked", 0))

    def test_admission_detects_a_catalog_change_between_validation_and_insert(self):
        def change_during_admission(*args):
            inputs = prepare_game(*args)
            with self.db:
                self.db.execute(
                    "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,5)",
                    ("oracle_cards", datetime.now(timezone.utc).isoformat()),
                )
            return inputs

        with patch(
            "commander.simulation.batches.prepare_game",
            side_effect=change_during_admission,
        ):
            with self.assertRaisesRegex(ValueError, "changed"):
                self._create()
        self.assertEqual(
            self.db.execute("SELECT count(*) FROM simulation_jobs").fetchone()[0], 0
        )

    def test_changed_engine_evidence_does_not_execute_a_game(self):
        job = self._create()
        with (
            patch.object(
                EngineSettings,
                "verify",
                return_value={**self.lock, "jar_sha256": "changed"},
            ),
            patch("commander.engine.forge_engine._execute_game") as engine,
        ):
            result = run_batch(self.db, self.settings, job["job_id"])
        self.assertEqual((result["status"], result["interrupted"]), ("blocked", 1))
        engine.assert_not_called()

    def test_worker_claim_excludes_a_second_worker(self):
        job = self._create()
        claim_job(self.db, job["job_id"])
        second = connect(self.path)
        self.addCleanup(second.close)
        with self.assertRaisesRegex(ValueError, "worker"):
            run_batch(second, self.settings, job["job_id"])

    def test_keyboard_interrupt_accounts_for_reserved_attempt_and_releases_claim(self):
        job = self._create()
        with patch(
            "commander.engine.forge_engine._execute_game", side_effect=KeyboardInterrupt
        ):
            with self.assertRaises(KeyboardInterrupt):
                run_batch(self.db, self.settings, job["job_id"])
        result = job_summary(self.db, job["job_id"])
        self.assertEqual((result["status"], result["interrupted"]), ("paused", 1))
        self.assertIsNone(read_job(self.db, job["job_id"])["worker_token"])

    def test_specifications_and_terminal_attempts_are_immutable(self):
        job = self._create(1)
        with self.assertRaises(sqlite3.IntegrityError), self.db:
            self.db.execute(
                "UPDATE simulation_jobs SET spec_json='{}' WHERE id=?", (job["job_id"],)
            )
        self._worker(job["job_id"])
        with self.assertRaises(sqlite3.IntegrityError), self.db:
            self.db.execute(
                "DELETE FROM simulation_attempts WHERE job_id=?", (job["job_id"],)
            )
        with self.assertRaises(sqlite3.IntegrityError), self.db:
            self.db.execute(
                "UPDATE simulation_attempts SET result_json='{}' WHERE job_id=?",
                (job["job_id"],),
            )

    def test_batch_service_never_commits_an_unrelated_transaction(self):
        ensure_schema(self.db)
        self.db.execute("BEGIN")
        with self.assertRaisesRegex(ValueError, "transaction"):
            job_summary(self.db, "a" * 32)
        self.assertTrue(self.db.in_transaction)
        self.db.rollback()

    def test_cli_inspection_and_failed_worker_exit_status(self):
        job = self._create()
        output = io.StringIO()
        with redirect_stdout(output):
            main(["--database", str(self.path), "batch-show", job["job_id"]])
        self.assertEqual(json.loads(output.getvalue())["status"], "queued")
        with (
            patch(
                "commander.cli.batch_commands.read_settings", return_value=self.settings
            ),
            patch(
                "commander.engine.forge_engine._execute_game",
                return_value={"status": "engine_timeout", "winner_seat": None},
            ),
            redirect_stdout(io.StringIO()),
        ):
            with self.assertRaises(SystemExit) as failure:
                main(["--database", str(self.path), "batch-run", job["job_id"]])
        self.assertEqual(failure.exception.code, 1)

    def test_invalid_worker_count_cannot_claim_a_job(self):
        job = self._create()
        for count in (0, 100001, True):
            with self.assertRaises(ValueError):
                run_batch(self.db, self.settings, job["job_id"], count)
        self.assertEqual(job_summary(self.db, job["job_id"])["status"], "queued")

    def test_attempt_results_are_ordered_and_bounded(self):
        job = self._create()
        self._worker(job["job_id"], 3)
        page = attempt_results(self.db, job["job_id"], offset=1, limit=1)
        self.assertEqual([row["game_index"] for row in page["attempts"]], [1])
        self.assertEqual(page["attempts"][0]["result"]["seed"], 43)
        self.assertEqual(
            attempt_results(self.db, job["job_id"], offset=3)["attempts"], []
        )
        for offset, limit in ((-1, 1), (True, 1), (0, True), (0, 1001)):
            with self.assertRaises(ValueError):
                attempt_results(self.db, job["job_id"], offset, limit)

    def test_seat_rotation_preserves_global_printing_admission_evidence(self):
        job = self._create()
        spec = read_job(self.db, job["job_id"])["spec"]
        selected = {
            "printings": [{"id": "frozen-print", "attraction_lights": [2, 5, 6]}],
            "editions": {"sha256": "reviewed"},
        }
        spec["inputs"]["printing_admission"] = selected
        for index in range(4):
            with self.subTest(index=index):
                planned = plan_game(spec, index)
                self.assertEqual(planned["inputs"]["printing_admission"], selected)
                self.assertEqual(
                    planned["inputs"]["seats"][0], spec["inputs"]["seats"][index]
                )
