"""Bounded controlled-state tests, with pinned source evidence and preserved receipts."""

import json
import sys
from importlib import metadata
from pathlib import Path
from sqlite3 import Connection

from commander.catalog.database import get_card_details
from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import (
    EngineSettings,
    file_hash,
    supported_java_version,
)
from commander.engine.forge_decks import card_index, script_match
from commander.engine.forge_engine import _engine_profile, _environment, _new_run
from commander.engine.interactions.suite import (
    MAX_RECEIPT_BYTES,
    ORACLE_FIELDS,
    PARTITIONS,
    definition_fingerprint,
    definitions_unchanged,
    evaluate_receipt,
    load_suite,
    oracle_fingerprint,
    read_hashed_json,
    write_bounded_json,
)
from commander.engine.native_runtime import BRIDGE_VERSION, jvm_library
from commander.engine.native_runtime import require_bridge as _require_native_bridge
from commander.paths import PROJECT_ROOT
from commander.rules.legality import require_current_catalog

from .native_bridge import bridge_unchanged, build_native_bridge


def run_interaction_suite(connection: Connection, settings: EngineSettings) -> dict:
    """Run only built-in partial-state fixtures; never admit user-supplied game states."""
    require_bridge()
    suite = load_suite()
    inputs = _inputs(connection, settings, suite)
    if not definitions_unchanged(inputs["definition_hashes"]):
        raise ValueError("Interaction suite changed during preflight; retry")
    run = _new_run()
    if len(json.dumps(inputs).encode("utf-8")) > MAX_RECEIPT_BYTES:
        raise ValueError("Interaction harness document exceeds its size limit")
    inputs["native_bridge"] = build_native_bridge(settings, run, _environment(run))
    path = run / "interaction-inputs.json"
    write_bounded_json(path, inputs, indent=2)
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
    if not _unchanged_inputs(settings, inputs, run):
        report = {
            "status": "inconclusive",
            "checks": [],
            "reason": "Engine, suite, or harness source changed while running",
        }
    report.update(
        schema_version=2,
        suite_id=suite["suite_id"],
        run_id=run.name,
        directory=str(run),
        purpose="controlled_partial_state_fixture_not_a_deck_simulation",
        process=process,
        engine=inputs["engine"],
        catalog=inputs["catalog"],
        suite_hash=inputs["suite_hash"],
        definition_hashes=inputs["definition_hashes"],
        inputs_sha256=file_hash(path),
        log_sha256=file_hash(run / "interactions.log"),
        source_hashes=inputs["source_hashes"],
        bootstrap_sha256=inputs["bootstrap_sha256"],
        bridge_version=BRIDGE_VERSION,
        native_bridge=inputs["native_bridge"],
        rules_sources=suite["rules_sources"],
        limitations=[
            f"{len(suite['cases'])} fixed scenarios, not comprehensive card/rules or AI validation.",
            "Initial states and action choices are controlled; the priority family uses native pass cycles and paid responses. Complete priority rules, default AI strategy and production deck legality remain separate.",
            "A 2 GiB JVM heap and wall/log limits do not provide an OS sandbox or total process memory quota.",
        ],
    )
    with (run / "interaction-report.json").open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2)
    return report


def require_bridge() -> None:
    """Keep the native bridge optional and pinned; ordinary simulations need no bridge."""
    _require_native_bridge(metadata.version)


def _unchanged_inputs(settings, inputs, run):
    try:
        folder = Path(__file__).parent
        sources_match = all(
            file_hash(folder / name) == digest
            for name, digest in inputs["source_hashes"].items()
        )
        return (
            sources_match
            and settings.verify() == inputs["engine"]
            and definitions_unchanged(inputs["definition_hashes"])
            and bridge_unchanged(run, inputs["native_bridge"])
            and file_hash(PROJECT_ROOT / "tools/interaction_worker.py")
            == inputs["bootstrap_sha256"]
        )
    except (OSError, ValueError):
        return False


def _jvm_library(java):
    return jvm_library(java)


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
        "schema_version": 2,
        "suite_id": suite["suite_id"],
        "partitions": list(PARTITIONS),
        "definition_hashes": suite["definition_hashes"],
        "suite_hash": definition_fingerprint(suite["definition_hashes"]),
        "engine": engine,
        "home": str(settings.home),
        "engine_lock": str(settings.lock_path) if settings.lock_path else None,
        "java": str(settings.java),
        "jvm": _jvm_library(settings.java),
        "catalog": catalog,
        "cards": cards,
        "bridge_version": BRIDGE_VERSION,
        "bootstrap_sha256": file_hash(PROJECT_ROOT / "tools/interaction_worker.py"),
        "source_hashes": {
            name: file_hash(folder / name)
            for name in (
                "worker.py",
                "../native_runtime.py",
                "fixtures.py",
                "suite.py",
                "responses.py",
                "casting.py",
                "casting_fixtures.py",
                "ordering.py",
                "ordering_fixtures.py",
                "native_bridge.py",
                "priority.py",
                "chains.py",
                "priority_fixtures.py",
                "native/PriorityTestController.java",
                "native/ReplacementChoiceController.java",
            )
        },
    }


def _read_result(run, suite, inputs, process):
    """Keep every fixed family visible; one missing receipt cannot turn into a pass."""
    parts = [_read_partition(run, suite, inputs, process, name) for name in PARTITIONS]
    status = "passed"
    if any(part["status"] == "failed" for part in parts):
        status = "failed"
    elif any(part["status"] != "passed" for part in parts):
        status = "inconclusive"
    versions = {part["java_version"] for part in parts if "java_version" in part}
    if len(versions) > 1:
        return {
            "status": "inconclusive",
            "checks": [],
            "reason": "Partition Java versions conflict",
        }
    return {
        "status": status,
        "checks": [check for part in parts for check in part.get("checks", [])],
        "partition_results": [
            {
                "partition_id": name,
                **{key: value for key, value in part.items() if key != "checks"},
            }
            for name, part in zip(PARTITIONS, parts)
        ],
    }


def _read_partition(run, suite, inputs, process, name):
    try:
        receipt, digest = read_hashed_json(run / f"observations-{name}.json")
    except (OSError, ValueError) as error:
        return {"status": "inconclusive", "checks": [], "reason": str(error)}
    expected = {
        "schema_version": 2,
        "suite_id": inputs["suite_id"],
        "partition_id": name,
        "suite_hash": inputs["suite_hash"],
        "engine": inputs["engine"],
        "bridge_version": inputs["bridge_version"],
        "native_class_sha256": inputs["native_bridge"]["class_sha256"],
        "priority_class_sha256": inputs["native_bridge"]["priority_class_sha256"],
    }
    if any(receipt.get(key) != value for key, value in expected.items()):
        return {
            "status": "inconclusive",
            "checks": [],
            "reason": "Receipt provenance does not match generated inputs",
        }
    version = receipt.get("java_version")
    if not supported_java_version(version):
        return {
            "status": "inconclusive",
            "checks": [],
            "reason": "Missing or unsupported Java runtime evidence",
        }
    result = evaluate_receipt(suite, receipt, process, case_ids=PARTITIONS[name])
    result["observations_sha256"] = digest
    result["java_version"] = receipt.get("java_version")
    return result
