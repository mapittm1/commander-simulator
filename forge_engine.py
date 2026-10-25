"""Single-game Forge adapter: validated revisions, bounded execution, preserved logs."""

import json
import os
import re
import uuid
from contextlib import contextmanager
from pathlib import Path
from sqlite3 import Connection

from brackets import load_policy, require_bracket_review
from deck_store import get_revision
from engine_process import ProcessLimits, run_bounded
from forge_config import EngineSettings
from forge_decks import card_index, coverage, serialize_deck
from forge_results import parse_game_result

RUNS_ROOT = Path(__file__).resolve().parent / "data" / "runs"


def _environment(run: Path) -> dict:
    """Do not pass account tokens, Java option injection, or other parent secrets."""
    environment = {
        name: value
        for name, value in os.environ.items()
        if name.upper() in {"SYSTEMROOT", "WINDIR", "LANG", "LC_ALL"}
    }
    for variable, folder in (
        ("TEMP", "temp"),
        ("TMP", "temp"),
        ("APPDATA", "appdata"),
        ("LOCALAPPDATA", "localappdata"),
    ):
        path = run / folder
        path.mkdir(exist_ok=True)
        environment[variable] = str(path)
    return environment


@contextmanager
def _engine_profile(settings: EngineSettings, run: Path):
    """Serialize access to shared engine assets and restore the exact original profile."""
    profile = settings.home / "forge.profile.properties"
    if profile.is_symlink():
        raise ValueError("Engine profile must not be a symbolic link")
    lock = settings.home / ".commander-simulator.lock"
    with lock.open("x", encoding="utf-8") as handle:
        handle.write(json.dumps({"pid": os.getpid(), "run": run.name}))
    original = None
    captured = False
    try:
        original = profile.read_bytes() if profile.exists() else None
        captured = True
        profile.write_text(
            f"userDir={(run / 'user').as_posix()}\ncacheDir={(run / 'cache').as_posix()}\n",
            encoding="utf-8",
        )
        yield
    finally:
        try:
            if captured:
                if original is None:
                    profile.unlink(missing_ok=True)
                else:
                    profile.write_bytes(original)
        finally:
            lock.unlink(missing_ok=True)


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
    run = RUNS_ROOT / uuid.uuid4().hex
    run.mkdir(parents=True, exist_ok=False)
    return run


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


def _check_current_evidence(connection, prepared) -> None:
    current = connection.execute(
        "SELECT * FROM imports ORDER BY id DESC LIMIT 1"
    ).fetchone()
    for seat in prepared:
        if (
            current is None
            or dict(current) != seat["review"]["catalog"]
            or load_policy()["hash"] != seat["review"]["policy_hash"]
        ):
            raise ValueError(
                "Catalog or bracket policy changed before launch; review again"
            )


def _prepare_inputs(connection, settings, seats) -> dict:
    """Complete all gates and serialization before creating any engine process."""
    prepared = prepare_seats(connection, seats)
    lock = settings.verify()
    index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
    reports = [coverage(seat["revision"], index) for seat in prepared]
    if any(report["missing"] for report in reports):
        raise ValueError("Engine card coverage failed: " + json.dumps(reports))
    decks = [
        serialize_deck(seat["revision"], index, number)
        for number, seat in enumerate(prepared, 1)
    ]
    _check_current_evidence(connection, prepared)
    return {"engine": lock, "seats": prepared, "coverage": reports, "decks": decks}


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
    if not isinstance(seed, int) or isinstance(seed, bool) or not 0 <= seed < 2**63:
        raise ValueError("Seed must be a nonnegative signed 64-bit integer")
    if (
        not isinstance(game_clock, int)
        or isinstance(game_clock, bool)
        or not 1 <= game_clock <= 300
    ):
        raise ValueError("Game clock must be between 1 and 300 seconds")
    inputs = _prepare_inputs(connection, settings, seats)
    run = _new_run()
    _write_inputs(run, inputs, seed, game_clock)
    try:
        with _engine_profile(settings, run):
            process = run_bounded(
                _command(settings, inputs["engine"], run, seed, game_clock),
                settings.home,
                run / "forge.log",
                ProcessLimits(game_clock + 90, environment=_environment(run)),
            )
        text = (run / "forge.log").read_text(encoding="utf-8", errors="replace")
        result = {**parse_game_result(text, process), "process": process}
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
