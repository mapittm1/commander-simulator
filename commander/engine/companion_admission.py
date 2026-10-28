"""Bounded native condition checks for explicit Commander companions."""

import hashlib
import json
import sys
from functools import lru_cache
from pathlib import Path

from commander.engine.catalog_probe_io import source_hashes
from commander.engine.companion_reviews import condition_matches, condition_review
from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import (
    EngineSettings,
    file_hash,
    read_settings,
    require_engine_capabilities,
    supported_java_version,
)
from commander.engine.forge_decks import card_index, script_match
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.engine.native_runtime import (
    _engine_profile,
    _environment,
    create_run,
    jvm_library,
    require_bridge,
)
from commander.paths import PROJECT_ROOT


def check_companion_condition(starting: list, companion: dict, cards: dict) -> dict:
    """Use native characteristics/abilities, including hidden-zone CDAs and keywords."""
    require_bridge()
    settings = read_settings()
    engine = settings.verify()
    require_engine_capabilities(engine, starting)
    index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
    plan = {
        "schema_version": 1,
        "home": str(settings.home),
        "java": str(settings.java),
        "lock": str(settings.lock_path) if settings.lock_path else None,
        "engine": engine,
        "jvm": jvm_library(settings.java),
        "source_hashes": source_hashes(),
        "starting": sorted(
            (_identity(entry, cards, index) for entry in starting),
            key=lambda entry: (entry["card_id"], entry["quantity"]),
        ),
        "companion": _identity(companion, cards, index),
        "condition_text": next(
            line.removeprefix("Companion — ").split(" (", 1)[0]
            for line in cards[companion["card"]["card_id"]]["oracle_text"].splitlines()
            if line.startswith("Companion — ")
        ),
        "oracle_sha256": hashlib.sha256(
            json.dumps(cards, sort_keys=True).encode()
        ).hexdigest(),
    }
    plan["condition_review"] = condition_review(plan)
    frozen = json.dumps(plan, sort_keys=True)
    # The cache is bounded and identity/source/engine-bound; callers receive a
    # fresh dictionary, so mutating a result cannot alter later admission.
    return json.loads(_run_admission(frozen))


def _identity(entry, cards, index):
    card_id = entry["card"]["card_id"]
    card = cards[card_id]
    match = script_match(card["name"], index, card_id)
    if match["script_status"] != "script_present":
        raise ValueError(
            "Companion admission requires every starting card's exact engine script"
        )
    result = {
        "card_id": card_id,
        "name": card["name"],
        "engine_name": match["engine_name"],
        "quantity": entry["quantity"],
    }
    if "commander_color" in entry:
        result["commander_color"] = entry["commander_color"]
    return result


@lru_cache(maxsize=64)
def _run_admission(frozen):
    plan = json.loads(frozen)
    settings = EngineSettings(
        Path(plan["home"]),
        Path(plan["java"]),
        Path(plan["lock"]) if plan["lock"] else None,
    )
    run = create_run()
    path = run / "companion-plan.json"
    write_bounded_json(path, plan)
    digest = file_hash(path)
    with _engine_profile(settings, run):
        process = run_bounded(
            [
                sys.executable,
                "-I",
                str(PROJECT_ROOT / "tools/companion_worker.py"),
                str(path),
            ],
            settings.home,
            run / "companion.log",
            ProcessLimits(90, environment=_environment(run)),
        )
    receipt = read_bounded_json(run / "companion-receipt.json")
    validate_receipt(receipt, plan, digest, process)
    if (
        settings.verify() != plan["engine"]
        or source_hashes() != plan["source_hashes"]
        or file_hash(path) != digest
    ):
        raise ValueError("Companion admission inputs changed during native execution")
    if condition_review(plan) != plan.get("condition_review"):
        raise ValueError("Companion condition review changed during native execution")
    result = {
        **receipt,
        "run_id": run.name,
        "process": process,
        "log_sha256": file_hash(run / "companion.log"),
        "basis": "pinned_native_companion_condition_including_commander",
        "condition_review": plan.get("condition_review"),
    }
    write_bounded_json(run / "companion-report.json", result, indent=2)
    return json.dumps(result)


def validate_receipt(receipt, plan, digest, process):
    """Incomplete, changed or malformed evidence must never approve a companion."""
    if process.get("status") != "finished" or process.get("returncode") != 0:
        raise ValueError("Companion condition worker did not finish successfully")
    expected = {
        "schema_version": 1,
        "plan_sha256": digest,
        "engine": plan["engine"],
        "starting_cards": 100,
        "companion_name": plan["companion"]["engine_name"],
    }
    if any(receipt.get(key) != value for key, value in expected.items()):
        raise ValueError("Companion condition receipt provenance mismatch")
    for key in ("schema_version", "starting_cards"):
        if not isinstance(receipt.get(key), int) or isinstance(receipt[key], bool):
            raise ValueError(
                "Companion condition receipt requires integer protocol/count"
            )
    if not isinstance(receipt.get("eligible"), bool) or not supported_java_version(
        receipt.get("java_version")
    ):
        raise ValueError(
            "Companion condition receipt lacks native eligibility/version evidence"
        )
    condition = receipt.get("condition")
    if (
        not isinstance(condition, str)
        or not condition.startswith("Companion:")
        or len(condition) > 2000
    ):
        raise ValueError("Companion condition receipt lacks a bounded native condition")

    if not condition_matches(plan, condition):
        raise ValueError(
            "Native companion condition differs from the current Oracle condition"
        )
