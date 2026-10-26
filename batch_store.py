"""Durable local batch state, immutable specifications, and auditable attempts."""

import hashlib
import json
import uuid
from sqlite3 import Connection

SCHEMA = """
CREATE TABLE IF NOT EXISTS simulation_jobs (
    id TEXT PRIMARY KEY,
    spec_json TEXT NOT NULL,
    spec_hash TEXT NOT NULL,
    state TEXT NOT NULL CHECK(state IN
        ('queued','running','paused','blocked','completed','cancelled')),
    cancel_requested INTEGER NOT NULL DEFAULT 0 CHECK(cancel_requested IN (0,1)),
    worker_token TEXT,
    error TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER IF NOT EXISTS simulation_jobs_frozen
BEFORE UPDATE ON simulation_jobs
WHEN NEW.id IS NOT OLD.id OR NEW.spec_json IS NOT OLD.spec_json
 OR NEW.spec_hash IS NOT OLD.spec_hash OR NEW.created_at IS NOT OLD.created_at
BEGIN SELECT RAISE(ABORT, 'Batch specifications are immutable'); END;
CREATE TRIGGER IF NOT EXISTS simulation_jobs_no_delete
BEFORE DELETE ON simulation_jobs
BEGIN SELECT RAISE(ABORT, 'Batch history cannot be deleted'); END;
CREATE TABLE IF NOT EXISTS simulation_attempts (
    job_id TEXT NOT NULL REFERENCES simulation_jobs(id),
    game_index INTEGER NOT NULL CHECK(game_index >= 0),
    state TEXT NOT NULL CHECK(state IN ('running','completed','failed','interrupted')),
    result_json TEXT,
    started_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TEXT,
    PRIMARY KEY(job_id, game_index)
);
CREATE TRIGGER IF NOT EXISTS simulation_attempts_frozen
BEFORE UPDATE ON simulation_attempts
WHEN OLD.state != 'running' OR NEW.state = 'running'
 OR NEW.job_id IS NOT OLD.job_id OR NEW.game_index IS NOT OLD.game_index
 OR NEW.started_at IS NOT OLD.started_at
BEGIN SELECT RAISE(ABORT, 'Completed attempts are immutable'); END;
CREATE TRIGGER IF NOT EXISTS simulation_attempts_no_delete
BEFORE DELETE ON simulation_attempts
BEGIN SELECT RAISE(ABORT, 'Attempt history cannot be deleted'); END;
"""


def ensure_schema(connection: Connection) -> None:
    """Initialize outside a caller's transaction without committing unrelated work."""
    if connection.in_transaction:
        raise ValueError("Finish the active transaction before using batch services")
    connection.executescript(SCHEMA)


def encode_spec(spec: dict) -> tuple[str, str]:
    """Canonicalize a specification and compute its integrity digest."""
    raw = json.dumps(spec, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return raw, hashlib.sha256(raw.encode("utf-8")).hexdigest()


def read_job(connection: Connection, job_id: str) -> dict:
    """Read one verified batch, without implicitly running or retrying it."""
    if not isinstance(job_id, str) or len(job_id) != 32:
        raise ValueError("Expected a generated batch identifier")
    row = connection.execute(
        "SELECT * FROM simulation_jobs WHERE id = ?", (job_id,)
    ).fetchone()
    if row is None:
        raise ValueError("Unknown simulation batch")
    job = dict(row)
    if hashlib.sha256(job["spec_json"].encode("utf-8")).hexdigest() != job["spec_hash"]:
        raise ValueError("Batch specification integrity check failed")
    job["spec"] = json.loads(job.pop("spec_json"))
    return job


def owned_job(connection, job_id, token) -> dict:
    """Require ownership inside the caller's transaction before worker mutations."""
    job = read_job(connection, job_id)
    if job["worker_token"] != token:
        raise ValueError("Batch worker ownership changed")
    return job


def job_summary(connection: Connection, job_id: str) -> dict:
    """Report exact counters with failed games outside completed-game outcomes."""
    ensure_schema(connection)
    with connection:
        connection.execute("BEGIN")
        return _read_summary(connection, job_id)


def _read_summary(connection, job_id) -> dict:
    job = read_job(connection, job_id)
    counters = dict.fromkeys(
        ("attempted", "completed", "failed", "interrupted", "running"), 0
    )
    for row in connection.execute(
        "SELECT state, count(*) AS total FROM simulation_attempts WHERE job_id = ? GROUP BY state",
        (job_id,),
    ):
        counters[row["state"]] = row["total"]
        counters["attempted"] += row["total"]
    return {
        "job_id": job_id,
        "status": job["state"],
        "requested": job["spec"]["runs"],
        **counters,
        "unattempted": job["spec"]["runs"] - counters["attempted"],
        "cancel_requested": bool(job["cancel_requested"]),
        "error": job["error"],
        "spec_hash": job["spec_hash"],
        "spec": job["spec"],
        "outcomes": _outcomes(connection, job_id, counters["completed"]),
    }


def _outcomes(connection, job_id, completed) -> dict:
    wins = [0, 0, 0, 0]
    draws = 0
    for row in connection.execute(
        "SELECT result_json FROM simulation_attempts WHERE job_id=? AND state='completed'",
        (job_id,),
    ):
        result = json.loads(row[0])
        if result["outcome"] == "draw":
            draws += 1
        else:
            wins[result["winner_pod_index"]] += 1
    return {
        "wins_by_original_pod_index": wins,
        "draws": draws,
        "completed_game_denominator": completed,
        "win_rates": [count / completed if completed else None for count in wins],
        "limitation": "Rates describe this fixed pod and AI pilots, not human performance.",
    }


def claim_job(connection: Connection, job_id: str) -> str | None:
    """Atomically claim a batch; never steal a running worker's ownership."""
    token = uuid.uuid4().hex
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        job = read_job(connection, job_id)
        if job["state"] in {"cancelled", "completed"}:
            return None
        if job["worker_token"] is not None or job["state"] == "running":
            raise ValueError(
                "Batch already has a worker; never auto-recover an active claim"
            )
        connection.execute(
            "UPDATE simulation_jobs SET state='running', worker_token=?, "
            "error=NULL, updated_at=CURRENT_TIMESTAMP WHERE id=?",
            (token, job_id),
        )
    return token


def finish_attempt(
    connection: Connection, job_id: str, index: int, result: dict, token: str
) -> None:
    """Finalize one reserved attempt; terminal records cannot be overwritten."""
    state = "completed" if result["status"] == "completed" else "failed"
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        owned_job(connection, job_id, token)
        cursor = connection.execute(
            "UPDATE simulation_attempts SET state=?,result_json=?,finished_at=CURRENT_TIMESTAMP "
            "WHERE job_id=? AND game_index=? AND state='running'",
            (state, json.dumps(result), job_id, index),
        )
        if cursor.rowcount != 1:
            raise ValueError("Attempt is not running")


def release_job(connection: Connection, job_id: str, token: str, error=None) -> None:
    """Release ownership and account for any interrupted game on handled exits."""
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        job = owned_job(connection, job_id, token)
        connection.execute(
            "UPDATE simulation_attempts SET state='interrupted', "
            "result_json=?, finished_at=CURRENT_TIMESTAMP "
            "WHERE job_id=? AND state='running'",
            (json.dumps({"status": "interrupted", "winner_seat": None}), job_id),
        )
        count = connection.execute(
            "SELECT count(*) FROM simulation_attempts WHERE job_id=?", (job_id,)
        ).fetchone()[0]
        state = "completed" if count == job["spec"]["runs"] else "paused"
        if error and count < job["spec"]["runs"]:
            state = "blocked"
        if job["cancel_requested"]:
            state = "cancelled"
        connection.execute(
            "UPDATE simulation_jobs SET state=?,worker_token=NULL,error=?, "
            "updated_at=CURRENT_TIMESTAMP WHERE id=?",
            (state, error, job_id),
        )


def cancel_job(connection: Connection, job_id: str) -> dict:
    """Persist cooperative cancellation; a current game finishes within its limits."""
    ensure_schema(connection)
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        job = read_job(connection, job_id)
        if job["state"] not in {"completed", "cancelled"}:
            state = "running" if job["worker_token"] else "cancelled"
            connection.execute(
                "UPDATE simulation_jobs SET cancel_requested=1,state=?, "
                "updated_at=CURRENT_TIMESTAMP WHERE id=?",
                (state, job_id),
            )
    return job_summary(connection, job_id)


def attempt_results(connection, job_id: str, offset: int = 0, limit: int = 100) -> dict:
    """Page ordered attempts without returning unbounded log or result content."""
    if (
        not isinstance(offset, int)
        or isinstance(offset, bool)
        or not 0 <= offset <= 100000
    ):
        raise ValueError("Attempt offset must be an integer from 0 through 100000")
    if not isinstance(limit, int) or isinstance(limit, bool) or not 1 <= limit <= 1000:
        raise ValueError("Result page size must be an integer from 1 through 1000")
    ensure_schema(connection)
    read_job(connection, job_id)
    attempts = []
    for row in connection.execute(
        "SELECT game_index,state,started_at,finished_at,result_json FROM simulation_attempts "
        "WHERE job_id=? ORDER BY game_index LIMIT ? OFFSET ?",
        (job_id, limit, offset),
    ):
        attempt = dict(row)
        raw = attempt.pop("result_json")
        attempt["result"] = json.loads(raw) if raw else None
        attempts.append(attempt)
    return {"job_id": job_id, "offset": offset, "limit": limit, "attempts": attempts}
