"""Bounded controlled-state tests, with pinned source evidence and preserved receipts."""

import json
import sys
from importlib import metadata
from pathlib import Path
from sqlite3 import Connection

from commander.catalog.database import get_card_details
from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import EngineSettings, file_hash
from commander.engine.forge_decks import card_index, script_match
from commander.engine.forge_engine import _engine_profile, _environment, _new_run
from commander.engine.interactions.suite import (
    ORACLE_FIELDS,
    SUITE_PATH,
    evaluate_receipt,
    load_suite,
    oracle_fingerprint,
    read_bounded_json,
)
from commander.paths import PROJECT_ROOT
from commander.rules.legality import require_current_catalog

BRIDGE_VERSION = "1.7.1"


def run_interaction_suite(connection: Connection, settings: EngineSettings) -> dict:
    """Run only built-in partial-state fixtures; never admit user-supplied game states."""
    require_bridge()
    suite_hash = file_hash(SUITE_PATH)
    suite = load_suite()
    inputs = _inputs(connection, settings, suite)
    if inputs["suite_hash"] != suite_hash:
        raise ValueError("Interaction suite changed during preflight; retry")
    run = _new_run()
    path = run / "interaction-inputs.json"
    path.write_text(json.dumps(inputs, indent=2), encoding="utf-8")
    worker = PROJECT_ROOT / "tools" / "interaction_worker.py"
    environment = _environment(run)
    environment["PYTHONIOENCODING"] = "utf-8"
    with _engine_profile(settings, run):
        process = run_bounded(
            [sys.executable, "-I", str(worker), str(path)],
            settings.home,
            run / "interactions.log",
            ProcessLimits(90, environment=environment),
        )
    report = _read_result(run, suite, inputs, process)
    if not _unchanged_inputs(settings, inputs):
        report = {
            "status": "inconclusive",
            "checks": [],
            "reason": "Engine, suite, or harness source changed while running",
        }
    report.update(
        schema_version=1,
        suite_id=suite["suite_id"],
        run_id=run.name,
        directory=str(run),
        purpose="controlled_partial_state_fixture_not_a_deck_simulation",
        process=process,
        engine=inputs["engine"],
        catalog=inputs["catalog"],
        suite_hash=inputs["suite_hash"],
        inputs_sha256=file_hash(path),
        log_sha256=file_hash(run / "interactions.log"),
        source_hashes=inputs["source_hashes"],
        bootstrap_sha256=inputs["bootstrap_sha256"],
        bridge_version=BRIDGE_VERSION,
        rules_sources=suite["rules_sources"],
        limitations=[
            f"{len(suite['cases'])} fixed scenarios, not comprehensive card/rules or AI validation.",
            "Initial states and between-trigger zone changes are controlled; legal casting, complete priority handling, and production deck legality are tested separately.",
            "A 2 GiB JVM heap and wall/log limits do not provide an OS sandbox or total process memory quota.",
        ],
    )
    with (run / "interaction-report.json").open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2)
    return report


def require_bridge() -> None:
    """Keep the native bridge optional and pinned; ordinary simulations need no bridge."""
    try:
        version = metadata.version("JPype1")
    except metadata.PackageNotFoundError as error:
        raise ValueError(
            "Install optional interaction tools: python -m pip install -r requirements-interactions.txt"
        ) from error
    if version != BRIDGE_VERSION:
        raise ValueError(
            "Interaction tests require the pinned JPype1 version; install requirements-interactions.txt"
        )


def _unchanged_inputs(settings, inputs):
    try:
        folder = Path(__file__).parent
        sources_match = all(
            file_hash(folder / name) == digest
            for name, digest in inputs["source_hashes"].items()
        )
        return (
            sources_match
            and settings.verify() == inputs["engine"]
            and file_hash(SUITE_PATH) == inputs["suite_hash"]
            and file_hash(PROJECT_ROOT / "tools/interaction_worker.py")
            == inputs["bootstrap_sha256"]
        )
    except (OSError, ValueError):
        return False


def _jvm_library(java):
    root = java.parent.parent
    candidates = (
        root / "bin/server/jvm.dll",
        root / "lib/server/libjvm.so",
        root / "lib/server/libjvm.dylib",
    )
    for path in candidates:
        if path.is_file():
            return str(path)
    raise ValueError("Configured Java runtime does not expose a supported JVM library")


def _inputs(connection, settings, suite):
    if connection.in_transaction:
        raise ValueError("Finish the active transaction before interaction tests")
    engine = settings.verify()
    index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
    cards = {}
    with connection:
        connection.execute("BEGIN")
        catalog = require_current_catalog(connection)
        for expected in suite["cards"]:
            card = get_card_details(connection, expected["oracle_id"])
            if (
                card["name"] != expected["name"]
                or oracle_fingerprint(card) != expected["oracle_sha256"]
            ):
                raise ValueError(
                    "Interaction fixture Oracle characteristics changed; review: "
                    + expected["name"]
                )
            match = script_match(card["name"], index, expected["oracle_id"])
            if match["script_status"] != "script_present":
                raise ValueError(
                    "Interaction fixture lacks a reviewed engine export: "
                    + card["name"]
                )
            cards[expected["role"]] = {
                **expected,
                **match,
                "oracle": {key: card.get(key) for key in ORACLE_FIELDS},
            }
    folder = Path(__file__).parent
    return {
        "schema_version": 1,
        "suite_hash": file_hash(SUITE_PATH),
        "engine": engine,
        "home": str(settings.home),
        "java": str(settings.java),
        "jvm": _jvm_library(settings.java),
        "catalog": catalog,
        "cards": cards,
        "bridge_version": BRIDGE_VERSION,
        "bootstrap_sha256": file_hash(PROJECT_ROOT / "tools/interaction_worker.py"),
        "source_hashes": {
            name: file_hash(folder / name)
            for name in ("worker.py", "fixtures.py", "suite.py")
        },
    }


def _read_result(run, suite, inputs, process):
    try:
        receipt = read_bounded_json(run / "observations.json")
    except (OSError, ValueError) as error:
        return {"status": "inconclusive", "checks": [], "reason": str(error)}
    if (
        receipt.get("schema_version") != 1
        or receipt.get("suite_hash") != inputs["suite_hash"]
        or receipt.get("engine") != inputs["engine"]
        or receipt.get("bridge_version") != inputs["bridge_version"]
    ):
        return {
            "status": "inconclusive",
            "checks": [],
            "reason": "Receipt provenance does not match generated inputs",
        }
    version = receipt.get("java_version")
    if (
        not isinstance(version, str)
        or len(version) > 100
        or not version.split(".")[0].isdigit()
        or int(version.split(".")[0]) < 17
    ):
        return {
            "status": "inconclusive",
            "checks": [],
            "reason": "Missing or unsupported Java runtime evidence",
        }
    result = evaluate_receipt(suite, receipt, process)
    result["observations_sha256"] = file_hash(run / "observations.json")
    result["java_version"] = receipt.get("java_version")
    return result
