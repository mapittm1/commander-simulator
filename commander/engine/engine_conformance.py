"""Run a bounded, legal, disposable regression pod and preserve witness evidence."""

import json
from pathlib import Path
from sqlite3 import Connection

from commander.engine.conformance_checks import check_rules_fixture
from commander.engine.engine_fixtures import FOREST_POD, legal_fixture_pod
from commander.engine.forge_config import EngineSettings, file_hash
from commander.engine.forge_engine import run_game


def run_rules_conformance(connection: Connection, settings: EngineSettings) -> dict:
    """Execute the built-in pod only; this is never a benchmark or user simulation."""
    with legal_fixture_pod(connection, FOREST_POD) as (fixture, seats):
        result = run_game(fixture, settings, seats, seed=42, game_clock=60)
    run = Path(result["directory"])
    inputs = json.loads((run / "inputs.json").read_text(encoding="utf-8"))
    text = (
        (run / "forge.log").read_text(encoding="utf-8", errors="replace")
        if (run / "forge.log").is_file()
        else ""
    )
    report = check_rules_fixture(text, inputs, result)
    report.update(
        run_id=run.name,
        directory=str(run),
        seed=42,
        purpose="limited_engine_regression_not_a_production_simulation",
        engine=inputs["engine"],
        inputs_sha256=file_hash(run / "inputs.json"),
        log_sha256=(
            file_hash(run / "forge.log") if (run / "forge.log").is_file() else None
        ),
    )
    with (run / "conformance.json").open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2)
    return report
