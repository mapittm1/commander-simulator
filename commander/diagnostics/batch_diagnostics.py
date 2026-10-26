"""Coverage-aware batch observations mapped back to original deck order."""

import json

from commander.diagnostics.game_diagnostics import UNSUPPORTED_METRICS
from commander.diagnostics.game_events import PARSER_VERSION
from commander.simulation.batch_store import ensure_schema, read_job

TOTAL_FIELDS = (
    "land_plays",
    "casts",
    "attacks_received",
    "attacks_assigned",
    "complete_main_turns",
    "turns_without_logged_land_play",
    "observed_turn_markers",
    "incomplete_turns",
    "off_turn_or_unscoped_land_plays",
)


def _eligible(result: dict) -> bool:
    report = result.get("diagnostics", {})
    return (
        result.get("status") == "completed"
        and report.get("schema_version") == 1
        and report.get("parser_version") == PARSER_VERSION
        and report.get("eligible_for_batch_aggregate") is True
    )


def batch_diagnostics(connection, job_id: str) -> dict:
    """Aggregate stored observations only; never reinterpret missing metrics as zero."""
    ensure_schema(connection)
    with connection:
        connection.execute("BEGIN")
        job = read_job(connection, job_id)
        totals = [
            {"original_pod_index": index, **dict.fromkeys(TOTAL_FIELDS, 0)}
            for index in range(4)
        ]
        matrix = [[0] * 4 for _ in range(4)]
        coverage = {"attempts_seen": 0, "games_included": 0, "games_excluded": 0}
        for row in connection.execute(
            "SELECT state,result_json FROM simulation_attempts WHERE job_id=?",
            (job_id,),
        ):
            coverage["attempts_seen"] += 1
            result = json.loads(row["result_json"]) if row["result_json"] else {}
            if row["state"] != "completed" or not _eligible(result):
                coverage["games_excluded"] += 1
                continue
            _accumulate(result, totals, matrix)
            coverage["games_included"] += 1
    for total in totals:
        denominator = total["complete_main_turns"]
        total["turns_without_logged_land_play_fraction"] = (
            total["turns_without_logged_land_play"] / denominator
            if denominator
            else None
        )
    return {
        "job_id": job_id,
        "schema_version": 1,
        "parser_version": PARSER_VERSION,
        "status": _coverage_status(coverage),
        "requested": job["spec"]["runs"],
        "coverage": coverage,
        "decks": totals if coverage["games_included"] else None,
        "attack_assignment_matrix": matrix if coverage["games_included"] else None,
        "unsupported_metrics": UNSUPPORTED_METRICS,
        "limitations": [
            "Only completed games with supported diagnostic coverage are included.",
            "Partial, legacy, failed, interrupted, and running games are excluded, not assigned zero metrics.",
            "No logged land play does not establish mana issues; attacks do not explain human motives.",
        ],
    }


def _accumulate(result, totals, matrix) -> None:
    order = result["seat_order"]
    if sorted(order) != [0, 1, 2, 3]:
        raise ValueError("Invalid stored diagnostic seat mapping")
    report = result["diagnostics"]
    for stats in report["seats"]:
        destination = totals[order[stats["seat"] - 1]]
        for key in TOTAL_FIELDS:
            destination[key] += stats[key]
    for attacker, row in enumerate(report["attack_assignment_matrix"]):
        for target, count in enumerate(row):
            matrix[order[attacker]][order[target]] += count


def _coverage_status(coverage) -> str:
    if not coverage["games_included"]:
        return "unavailable"
    return "partial" if coverage["games_excluded"] else "observed"
