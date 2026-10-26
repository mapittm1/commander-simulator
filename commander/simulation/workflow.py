"""Finish a local batch with progress and a compact, preserved results report."""

import json
from collections.abc import Callable
from pathlib import Path
from sqlite3 import Connection

from commander.diagnostics.batch_diagnostics import batch_diagnostics
from commander.engine.forge_config import EngineSettings
from commander.simulation.batch_store import attempt_results, job_summary
from commander.simulation.batches import run_batch


def finish_batch(
    connection: Connection,
    settings: EngineSettings,
    job_id: str,
    database: Path,
    progress: Callable[[int, dict], None] | None = None,
) -> dict:
    """Run remaining reserved capacity; Ctrl+C preserves an inspectable interrupted attempt."""
    initial = job_summary(connection, job_id)
    stopped = False
    try:
        final = run_batch(
            connection,
            settings,
            job_id,
            max(1, initial["requested"] - initial["attempted"]),
            progress,
        )
    except KeyboardInterrupt:
        stopped = True
        final = job_summary(connection, job_id)
    status = _status(final, stopped)
    pod = [
        {
            key: seat["revision"][key]
            for key in (
                "deck_id",
                "version",
                "name",
                "declared_bracket",
                "snapshot_hash",
            )
        }
        for seat in final["spec"]["inputs"]["seats"]
    ]
    preview = []
    for attempt in attempt_results(connection, job_id, limit=20)["attempts"]:
        result = attempt["result"] or {}
        preview.append(
            {
                "game_index": attempt["game_index"],
                "state": attempt["state"],
                "result": {
                    key: result.get(key)
                    for key in (
                        "status",
                        "run_id",
                        "directory",
                        "winner_pod_index",
                        "seed",
                    )
                },
            }
        )
    return {
        "schema_version": 1,
        "status": status,
        "database": str(database.resolve()),
        "batch": {key: value for key, value in final.items() if key != "spec"},
        "pod": pod,
        "engine": final["spec"]["inputs"]["engine"],
        "diagnostics": batch_diagnostics(connection, job_id),
        "attempt_preview": preview,
        "attempt_preview_limit": 20,
        "limitations": [
            "The pod is explicitly supplied, not an automatically ranked benchmark.",
            "Observed land plays and attacks do not prove mana issues or human motives.",
            "Script presence and AI results do not establish full card/rules correctness.",
        ],
    }


def _status(summary, stopped):
    if stopped:
        return "interrupted"
    if summary["failed"] or summary["interrupted"]:
        return "failed"
    return "completed" if summary["status"] == "completed" else summary["status"]


def save_report(report: dict, destination: Path) -> None:
    """Write an operator-selected local report exclusively, never overwrite an existing file."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2, ensure_ascii=False)
        stream.write("\n")
