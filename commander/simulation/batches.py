"""Sequential batches with frozen pods, deterministic seat rotation, and cancellation."""

import sqlite3
import uuid
from collections.abc import Callable
from dataclasses import dataclass

from commander.engine.forge_engine import (
    check_game_evidence,
    game_evidence,
    prepare_game,
    run_frozen_game,
    validate_game_options,
)
from commander.simulation.batch_store import (
    claim_job,
    encode_spec,
    ensure_schema,
    finish_attempt,
    job_summary,
    owned_job,
    release_job,
)

MAX_BATCH_RUNS = 100000


@dataclass(frozen=True)
class BatchOptions:
    """Backend-validated options independent of CLI or future web form validation."""

    runs: int
    seed: int = 42
    game_clock: int = 120

    def validate(self) -> None:
        """Reject booleans, excessive counts, and seed ranges that would overflow."""
        bounded_count(self.runs)
        validate_game_options(self.seed, self.game_clock)
        if self.seed + self.runs > 2**63:
            raise ValueError("Batch seed range exceeds signed 64-bit integers")


def bounded_count(value: int) -> int:
    """Require an actual integer within the shared batch/worker limit."""
    if (
        not isinstance(value, int)
        or isinstance(value, bool)
        or not 1 <= value <= MAX_BATCH_RUNS
    ):
        raise ValueError("Game count must be an integer from 1 through 100000")
    return value


def create_batch(connection, settings, seats: list, options: BatchOptions) -> dict:
    """Validate all seats and coverage before atomically admitting immutable work."""
    options.validate()
    inputs = prepare_game(connection, settings, seats)
    spec = {
        "schema_version": 1,
        "runs": options.runs,
        "seed": options.seed,
        "game_clock": options.game_clock,
        "seats": seats,
        "inputs": game_evidence(inputs),
        "opponent_mode": "explicit_fixed_pod",
    }
    raw, digest = encode_spec(spec)
    ensure_schema(connection)
    job_id = uuid.uuid4().hex
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        check_game_evidence(connection, inputs["seats"])
        connection.execute(
            "INSERT INTO simulation_jobs(id,spec_json,spec_hash,state) "
            "VALUES (?,?,?,'queued')",
            (job_id, raw, digest),
        )
    return job_summary(connection, job_id)


def plan_game(spec: dict, index: int) -> dict:
    """Rotate all four seats equally; derive each seed without mutable random state."""
    if (
        not isinstance(index, int)
        or isinstance(index, bool)
        or not 0 <= index < spec["runs"]
    ):
        raise ValueError("Game index is outside this batch")
    order = [(index + seat) % 4 for seat in range(4)]
    return {
        "seed": spec["seed"] + index,
        "game_clock": spec["game_clock"],
        "seat_order": order,
        "seats": [spec["seats"][seat] for seat in order],
        "inputs": {
            "engine": spec["inputs"]["engine"],
            "seats": [spec["inputs"]["seats"][seat] for seat in order],
            "coverage": [spec["inputs"]["coverage"][seat] for seat in order],
        },
    }


def _reserve_attempt(connection, job_id, token) -> tuple[int, dict] | None:
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        job = owned_job(connection, job_id, token)
        if job["cancel_requested"]:
            return None
        index = connection.execute(
            "SELECT count(*) FROM simulation_attempts WHERE job_id=?", (job_id,)
        ).fetchone()[0]
        if index >= job["spec"]["runs"]:
            return None
        check_game_evidence(connection, job["spec"]["inputs"]["seats"])
        connection.execute(
            "INSERT INTO simulation_attempts(job_id,game_index,state) "
            "VALUES (?,?,'running')",
            (job_id, index),
        )
        return index, plan_game(job["spec"], index)


def _run_attempt(connection, settings, reservation: tuple) -> dict:
    index, game = reservation
    result = run_frozen_game(connection, settings, game)
    winner = result.get("winner_seat")
    if result["status"] == "completed":
        valid_win = (
            result.get("outcome") == "win"
            and isinstance(winner, int)
            and not isinstance(winner, bool)
            and 1 <= winner <= 4
        )
        valid_draw = result.get("outcome") == "draw" and winner is None
        if not (valid_win or valid_draw):
            result = {"status": "unrecognized_result", "winner_seat": None}
            winner = None
    result.update(
        game_index=index,
        seed=game["seed"],
        seat_order=game["seat_order"],
        winner_pod_index=game["seat_order"][winner - 1] if winner else None,
    )
    return result


def run_batch(
    connection,
    settings,
    job_id: str,
    max_games: int = 1,
    progress: Callable[[int, dict], None] | None = None,
) -> dict:
    """Run a bounded chunk; stop on failure, cancellation, or changed evidence."""
    bounded_count(max_games)
    ensure_schema(connection)
    token = claim_job(connection, job_id)
    if token is None:
        return job_summary(connection, job_id)
    error = None
    try:
        for _ in range(max_games):
            reservation = _reserve_attempt(connection, job_id, token)
            if reservation is None:
                break
            result = _run_attempt(connection, settings, reservation)
            finish_attempt(connection, job_id, reservation[0], result, token)
            if progress is not None:
                progress(reservation[0], result)
            if result["status"] != "completed":
                error = "Engine attempt failed: " + result["status"]
                break
    except (OSError, ValueError, RuntimeError, sqlite3.Error) as failure:
        error = str(failure)
    finally:
        release_job(connection, job_id, token, error)
    return job_summary(connection, job_id)
