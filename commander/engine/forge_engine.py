"""Single-game Forge adapter: validated revisions, bounded execution, preserved logs."""

import json
import re
from pathlib import Path
from sqlite3 import Connection

from commander.catalog.printing_choices import printing_choice_errors
from commander.decks.deck_store import get_revision
from commander.diagnostics.diagnostic_artifacts import record_diagnostics
from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import EngineSettings, require_engine_capabilities
from commander.engine.forge_decks import card_index, coverage, serialize_deck
from commander.engine.forge_results import parse_game_result
from commander.engine.native_runtime import _engine_profile, _environment, create_run
from commander.engine.printing_admission import (
    EVIDENCE_FIELDS,
    check_printing_choices,
    verify_printing_evidence,
)
from commander.paths import DATA_ROOT
from commander.rules.brackets import load_policy, require_bracket_review

RUNS_ROOT = DATA_ROOT / "runs"


def prepare_seats(connection: Connection, seats: list) -> list[dict]:
    """Resolve four explicit versions and require current legality and bracket review."""
    if not isinstance(seats, (list, tuple)) or len(seats) != 4:
        raise ValueError("A Commander game requires exactly four seats")
    prepared = []
    for pair in seats:
        if not isinstance(pair, (list, tuple)) or len(pair) != 2:
            raise ValueError("Each seat must be a deck ID and explicit version pair")
        if any(
            not isinstance(value, int)
            or isinstance(value, bool)
            or not 1 <= value < 2**63
            for value in pair
        ):
            raise ValueError(
                "Each seat must contain a positive deck ID and explicit version"
            )
        revision = get_revision(connection, pair[0], pair[1])
        review = require_bracket_review(connection, pair[0], pair[1])
        prepared.append({"revision": revision, "review": review})
    if len({seat["revision"]["declared_bracket"] for seat in prepared}) != 1:
        raise ValueError("All seats must use the same declared bracket")
    if (
        len(
            {json.dumps(seat["review"]["catalog"], sort_keys=True) for seat in prepared}
        )
        != 1
    ):
        raise ValueError("Seat reviews use different catalog evidence")
    return prepared


def _new_run() -> Path:
    return create_run(RUNS_ROOT)


def engine_health(settings: EngineSettings) -> dict:
    """Check pinned resources and the selected Java runtime without starting a game."""
    lock = settings.verify()
    run = _new_run()
    process = run_bounded(
        [str(settings.java), "-version"],
        settings.home,
        run / "java.log",
        ProcessLimits(15, 16384, _environment(run)),
    )
    version = (run / "java.log").read_text(encoding="utf-8", errors="replace")
    match = re.search(r'version "(\d+)', version)
    if (
        process["status"] != "finished"
        or process["returncode"] != 0
        or not match
        or int(match[1]) < 17
    ):
        raise ValueError("Java 17 or later is required; see the preserved runtime log")
    index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
    return {
        "status": "ready",
        "forge_version": lock["forge_version"],
        "capabilities": lock.get("capabilities", []),
        "jar_sha256": lock["jar_sha256"],
        "cards_sha256": lock["cards_sha256"],
        "indexed_names": len(index),
        "java_version": version.strip(),
        "diagnostic_directory": str(run),
        "limitation": "Script presence is not proof of AI or rules correctness.",
    }


def _command(settings, lock, run, seed, game_clock):
    return [
        str(settings.java),
        "-Xmx2048m",
        "-XX:ActiveProcessorCount=2",
        "-Djava.awt.headless=true",
        "-Dfile.encoding=UTF-8",
        f"-Duser.home={run / 'home'}",
        "-jar",
        str(settings.home / lock["jar_name"]),
        "sim",
        "-d",
        *(f"seat-{seat}.dck" for seat in range(1, 5)),
        "-D",
        str(run),
        "-f",
        "Commander",
        "-n",
        "1",
        "-s",
        str(seed),
        "-c",
        str(game_clock),
    ]


def check_game_evidence(connection, prepared) -> None:
    """Check catalog, policy, and review identities inside a caller's admission transaction."""
    for seat in prepared:
        entries = seat["revision"]["cards"]
        if any("printing" in entry for entry in entries):
            cards = {entry["card"]["card_id"]: entry["oracle"] for entry in entries}
            if printing_choice_errors(connection, entries, cards):
                raise ValueError(
                    "Attraction printing facts changed during game admission"
                )
    current = connection.execute(
        "SELECT * FROM imports ORDER BY id DESC LIMIT 1"
    ).fetchone()
    for seat in prepared:
        review_id = connection.execute(
            "SELECT id FROM bracket_reviews WHERE revision_id=? ORDER BY id DESC LIMIT 1",
            (seat["revision"]["id"],),
        ).fetchone()
        if (
            current is None
            or dict(current) != seat["review"]["catalog"]
            or load_policy()["hash"] != seat["review"]["policy_hash"]
            or review_id is None
            or review_id[0] != seat["review"]["review_id"]
        ):
            raise ValueError(
                "Catalog or bracket policy changed before launch; review again"
            )


def prepare_game(connection, settings, seats) -> dict:
    """Complete all gates and serialization before creating any engine process."""
    prepared = prepare_seats(connection, seats)
    lock = settings.verify()
    index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
    for seat in prepared:
        require_engine_capabilities(lock, seat["revision"]["cards"])
    reports = [coverage(seat["revision"], index) for seat in prepared]
    if any(report["missing"] for report in reports):
        raise ValueError("Engine card coverage failed: " + json.dumps(reports))
    decks = [
        serialize_deck(seat["revision"], index, number)
        for number, seat in enumerate(prepared, 1)
    ]
    printing = check_printing_choices(
        settings, [seat["revision"] for seat in prepared], index
    )
    check_game_evidence(connection, prepared)
    result = {"engine": lock, "seats": prepared, "coverage": reports, "decks": decks}
    if printing is not None:
        result["printing_admission"] = printing
    return result


def _write_inputs(run, inputs, seed, game_clock) -> None:
    """Preserve the evidence and generated decks for one identified run."""
    manifest = {key: value for key, value in inputs.items() if key != "decks"}
    manifest.update(schema_version=1, seed=seed, game_clock_seconds=game_clock)
    (run / "inputs.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    for number, deck in enumerate(inputs["decks"], 1):
        (run / f"seat-{number}.dck").write_text(deck, encoding="utf-8")


def run_game(
    connection: Connection,
    settings: EngineSettings,
    seats: list,
    seed: int = 42,
    game_clock: int = 120,
) -> dict:
    """Execute one approved four-player game; return failures separately from draws."""
    validate_game_options(seed, game_clock)
    inputs = prepare_game(connection, settings, seats)
    return _execute_game(settings, inputs, seed, game_clock)


def validate_game_options(seed: int, game_clock: int) -> None:
    """Validate finite, bounded engine parameters before preparing game inputs."""
    if not isinstance(seed, int) or isinstance(seed, bool) or not 0 <= seed < 2**63:
        raise ValueError("Seed must be a nonnegative signed 64-bit integer")
    if (
        not isinstance(game_clock, int)
        or isinstance(game_clock, bool)
        or not 1 <= game_clock <= 300
    ):
        raise ValueError("Game clock must be between 1 and 300 seconds")


def run_frozen_game(connection, settings, game: dict) -> dict:
    """Revalidate a batch game's exact frozen evidence immediately before execution."""
    validate_game_options(game["seed"], game["game_clock"])
    current = prepare_game(connection, settings, game["seats"])
    if game_evidence(current) != game["inputs"]:
        raise ValueError("Frozen game evidence changed; create a new batch")
    return _execute_game(settings, current, game["seed"], game["game_clock"])


def game_evidence(inputs: dict) -> dict:
    """Freeze semantic evidence; recheck timestamps and generated deck text may change."""
    seats = [
        {
            **seat,
            "review": {
                key: value
                for key, value in seat["review"].items()
                if key != "checked_at"
            },
        }
        for seat in inputs["seats"]
    ]
    result = {
        "engine": inputs["engine"],
        "seats": seats,
        "coverage": inputs["coverage"],
    }
    if "printing_admission" in inputs:
        result["printing_admission"] = {
            key: inputs["printing_admission"][key] for key in EVIDENCE_FIELDS
        }
    return result


def _execute_game(settings, inputs, seed, game_clock) -> dict:
    """Execute only service-prepared inputs and preserve failure diagnostics."""
    run = _new_run()
    _write_inputs(run, inputs, seed, game_clock)
    try:
        with _engine_profile(settings, run):
            if "printing_admission" in inputs:
                verify_printing_evidence(settings, inputs["printing_admission"])
            process = run_bounded(
                _command(settings, inputs["engine"], run, seed, game_clock),
                settings.home,
                run / "forge.log",
                ProcessLimits(game_clock + 90, environment=_environment(run)),
            )
        result = _read_game_output(run, process)
    except (OSError, RuntimeError, ValueError) as error:
        result = {"status": "engine_error", "winner_seat": None, "error": str(error)}
    result.update(
        {
            "run_id": run.name,
            "directory": str(run),
            "seed": seed,
            "forge_version": inputs["engine"]["forge_version"],
            "limitation": "AI performance is not a calibrated estimate of human play.",
        }
    )
    (run / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    return result


def _read_game_output(run, process) -> dict:
    text = (run / "forge.log").read_text(encoding="utf-8", errors="replace")
    result = {**parse_game_result(text, process), "process": process}
    try:
        result["diagnostics"] = record_diagnostics(run, text, result["status"])
    except (OSError, ValueError) as error:
        result["diagnostics"] = {
            "status": "unavailable",
            "eligible_for_batch_aggregate": False,
            "error": str(error),
        }
    return result
