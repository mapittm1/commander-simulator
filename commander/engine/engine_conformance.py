"""Run a fixed set of legal Commander games and preserve each rules witness."""

import json
from pathlib import Path
from sqlite3 import Connection

from commander.engine.conformance_checks import check_rules_fixture
from commander.engine.engine_fixtures import FOREST_POD, legal_fixture_pod
from commander.engine.forge_config import EngineSettings, file_hash
from commander.engine.forge_engine import run_game

WITNESS_SEEDS = (42, 0, 4096)
CHECK_IDS = (
    "commander_tax_recast",
    "goreclaw_attack_trigger",
    "commander_loss_with_positive_life",
)


def _collect_report(result: dict, seed: int, engine: dict) -> dict:
    run = Path(result["directory"])
    inputs_path, log_path = run / "inputs.json", run / "forge.log"
    inputs = json.loads(inputs_path.read_text(encoding="utf-8"))
    if inputs["engine"] != engine or inputs["seed"] != seed:
        raise ValueError("Conformance game engine or seed changed")
    text = (
        log_path.read_text(encoding="utf-8", errors="replace")
        if log_path.is_file()
        else ""
    )
    report = check_rules_fixture(text, inputs, result)
    report.update(
        run_id=run.name,
        directory=str(run),
        seed=seed,
        engine=engine,
        inputs_sha256=file_hash(inputs_path),
        log_sha256=file_hash(log_path) if log_path.is_file() else None,
    )
    return report


def _merge_check(check_id: str, reports: list[dict]) -> dict:
    observations = [
        (report, case)
        for report in reports
        for case in report["checks"]
        if case["id"] == check_id
    ]
    status = "not_exercised"
    if any(case["status"] == "failed" for _, case in observations):
        status = "failed"
    elif any(
        case["status"] == "passed" and case.get("evidence") for _, case in observations
    ):
        status = "passed"
    return {
        "id": check_id,
        "status": status,
        "scope": next(
            (case["scope"] for _, case in observations if "scope" in case), None
        ),
        "evidence": [
            {**row, "seed": report["seed"], "run_id": report["run_id"]}
            for report, case in observations
            for row in case.get("evidence", ())
        ],
    }


def _combine_reports(reports: list[dict]) -> dict:
    """Require the whole fixed suite, a witness for each rule, and no contradiction."""
    checks = [_merge_check(check_id, reports) for check_id in CHECK_IDS]
    complete = tuple(report["seed"] for report in reports) == WITNESS_SEEDS and all(
        tuple(case["id"] for case in report["checks"]) == CHECK_IDS
        for report in reports
    )
    if any(case["status"] == "failed" for case in checks):
        status = "failed"
    elif complete and all(case["status"] == "passed" for case in checks):
        status = "passed"
    else:
        status = "inconclusive"
    return {"status": status, "checks": checks}


def run_rules_conformance(connection: Connection, settings: EngineSettings) -> dict:
    """Execute every fixed seed; never retry or stop early to obtain a passing game."""
    engine = settings.verify()
    reports = []
    with legal_fixture_pod(connection, FOREST_POD) as (fixture, seats):
        for seed in WITNESS_SEEDS:
            result = run_game(fixture, settings, seats, seed=seed, game_clock=60)
            reports.append(_collect_report(result, seed, engine))
    report = {
        **reports[0],
        **_combine_reports(reports),
        "checker_version": 2,
        "seeds": list(WITNESS_SEEDS),
        "witness_runs": reports,
        "purpose": "limited_engine_regression_not_a_production_simulation",
    }
    with (Path(report["directory"]) / "conformance.json").open(
        "x", encoding="utf-8"
    ) as stream:
        json.dump(report, stream, indent=2)
    return report
