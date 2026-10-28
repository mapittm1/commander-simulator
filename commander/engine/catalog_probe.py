"""Catalog-wide native card construction with bounded, identity-bound evidence."""

import sys
from collections import Counter
from itertools import zip_longest

from commander.engine.catalog_probe_io import (
    MAX_ROWS,
    read_rows,
    source_hashes,
    stream_hash,
    write_rows,
)
from commander.engine.engine_audit import create_engine_audit
from commander.engine.engine_audit_store import audit_cards
from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import file_hash, supported_java_version
from commander.engine.forge_engine import _engine_profile, _environment, _new_run
from commander.engine.interactions.runner import (
    BRIDGE_VERSION,
    _jvm_library,
    require_bridge,
)
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.paths import PROJECT_ROOT
from commander.rules.legality import require_current_catalog

STATUSES = {
    "constructed",
    "script_unavailable",
    "construction_error",
    "missing_native_card",
    "native_identity_mismatch",
}


def run_catalog_probe(connection, settings) -> dict:
    """Check every retained Commander-format identity; never simulate user decks here."""
    require_bridge()
    stored = create_engine_audit(connection, settings)
    run = _new_run()
    count, digest = write_rows(
        run / "catalog-inputs.jsonl", _proposals(connection, stored["audit_id"])
    )
    plan = {
        "schema_version": 1,
        "audit_id": stored["audit_id"],
        "audit_hash": stored["report_hash"],
        "catalog": stored["report"]["catalog"],
        "engine": settings.verify(),
        "home": str(settings.home),
        "java": str(settings.java),
        "engine_lock": str(settings.lock_path) if settings.lock_path else None,
        "jvm": _jvm_library(settings.java),
        "bridge_version": BRIDGE_VERSION,
        "source_hashes": source_hashes(),
        "rows": count,
        "inputs_sha256": digest,
    }
    path = run / "catalog-plan.json"
    write_bounded_json(path, plan, indent=2)
    plan_hash = file_hash(path)
    environment = _environment(run)
    environment["PYTHONIOENCODING"] = "utf-8"
    with _engine_profile(settings, run):
        process = run_bounded(
            [
                sys.executable,
                "-I",
                str(PROJECT_ROOT / "tools/catalog_probe_worker.py"),
                str(path),
            ],
            settings.home,
            run / "catalog-probe.log",
            ProcessLimits(600, environment=environment),
        )
    result = _read_result(run, plan, plan_hash, process)
    if not _unchanged(connection, settings, run, plan, plan_hash):
        result.update(
            status="inconclusive",
            reason="Catalog, engine, inputs or probe source changed during execution",
        )
    result.update(
        schema_version=1,
        run_id=run.name,
        directory=str(run),
        process=process,
        audit_id=plan["audit_id"],
        audit_hash=plan["audit_hash"],
        catalog=plan["catalog"],
        engine=plan["engine"],
        source_hashes=plan["source_hashes"],
        plan_sha256=plan_hash,
        inputs_sha256=plan["inputs_sha256"],
        log_sha256=file_hash(run / "catalog-probe.log"),
        purpose="native_card_factory_construction_not_full_rules_or_ai_validation",
        limitations=[
            "Constructing a card parses native abilities; it does not execute every ability or interaction.",
            "The scope is the frozen local catalog's Commander-format identities, including supplementary objects.",
            "A 2 GiB JVM heap and process/stream limits are not an OS sandbox or total memory quota.",
        ],
    )
    write_bounded_json(run / "catalog-report.json", result, indent=2)
    return result


def _proposals(connection, audit_id):
    offset = 0
    while True:
        page = audit_cards(
            connection,
            audit_id,
            {
                "scope": "commander_legal",
                "status": "all",
                "offset": offset,
                "limit": 1000,
            },
        )
        for card in page["cards"]:
            yield {
                key: card[key]
                for key in ("card_id", "name", "engine_name", "script_status")
            } | {
                "gap_category": card.get("gap", {}).get("category"),
            }
        offset += len(page["cards"])
        if offset >= page["total_matching"]:
            return
        if not page["cards"]:
            raise ValueError("Incomplete catalog audit page")


def evaluate_observations(proposals, observations, expected_count) -> dict:
    """Missing, duplicate, reordered or malformed raw evidence can never pass."""
    if (
        not isinstance(expected_count, int)
        or isinstance(expected_count, bool)
        or not 1 <= expected_count <= MAX_ROWS
    ):
        raise ValueError("Catalog probe requires a positive bounded expected inventory")
    counts, gaps, failures = Counter(), Counter(), []
    seen = set()
    for proposal, observed in zip_longest(proposals, observations):
        if proposal is None or observed is None:
            raise ValueError("Incomplete catalog probe inventory")
        identity = proposal["card_id"]
        if identity in seen:
            raise ValueError("Duplicate catalog probe identity")
        seen.add(identity)
        _validate_observation(proposal, observed)
        status = observed["status"]
        counts[status] += 1
        if status == "script_unavailable":
            gaps[proposal["gap_category"] or "unclassified"] += 1
        if status != "constructed" and len(failures) < 20:
            failures.append(observed)
    if not seen or len(seen) != expected_count:
        raise ValueError("Catalog probe count differs from its frozen plan")
    status = "passed"
    if any(counts[key] for key in STATUSES - {"constructed", "script_unavailable"}):
        status = "failed"
    elif any(
        count
        for category, count in gaps.items()
        if category not in {"non_starting_meld_result", "non_starting_sticker_sheet"}
    ):
        status = "gaps_remain"
    return {
        "status": status,
        "checked": len(seen),
        "counts": dict(counts),
        "unavailable_categories": dict(gaps),
        "first_findings": failures,
    }


def _validate_observation(proposal, observed):
    for key in ("card_id", "name", "engine_name"):
        if observed.get(key) != proposal[key]:
            raise ValueError("Native catalog identity/order mismatch")
    status = observed.get("status")
    if not isinstance(status, str) or status not in STATUSES:
        raise ValueError("Unknown native catalog observation status")
    if (proposal["script_status"] != "script_present") != (
        status == "script_unavailable"
    ):
        raise ValueError("Native catalog result contradicts script availability")
    if status == "construction_error" and (
        not isinstance(observed.get("error"), str)
        or not 0 < len(observed["error"]) <= 500
    ):
        raise ValueError("Native construction errors require bounded evidence")
    if status == "native_identity_mismatch":
        name = observed.get("native_name")
        if (
            not isinstance(name, str)
            or not 0 < len(name) <= 300
            or name == proposal["engine_name"]
        ):
            raise ValueError(
                "Native identity mismatch requires a different bounded name"
            )
    if status == "constructed":
        for key in ("states", "abilities", "statics", "triggers", "replacements"):
            value = observed.get(key)
            if (
                not isinstance(value, int)
                or isinstance(value, bool)
                or not 0 <= value <= 10000
            ):
                raise ValueError("Missing/malformed native ability metrics")
        if observed["states"] < 1 or observed.get("pool") not in {"common", "variant"}:
            raise ValueError("Native card construction lacks a card state or pool")
        name = observed.get("native_name")
        if not isinstance(name, str) or not 0 < len(name) <= 300:
            raise ValueError("Native card construction lacks a bounded native name")


def _read_result(run, plan, plan_hash, process):
    try:
        receipt = read_bounded_json(run / "catalog-receipt.json")
        _validate_receipt(receipt, plan, plan_hash, run)
        version = receipt.get("java_version")
        if not supported_java_version(version):
            raise ValueError("Missing native Java version evidence")
        result = evaluate_observations(
            read_rows(run / "catalog-inputs.jsonl"),
            read_rows(run / "catalog-observations.jsonl"),
            plan["rows"],
        )
        result.update(
            observations_sha256=receipt["observations_sha256"], java_version=version
        )
        if process.get("status") != "finished" or process.get("returncode") != 0:
            result.update(
                status="inconclusive",
                reason="Native process did not finish successfully",
            )
        return result
    except (OSError, ValueError, KeyError, TypeError) as error:
        return {"status": "inconclusive", "reason": str(error)[:1000]}


def _unchanged(connection, settings, run, plan, plan_hash):
    try:
        return (
            settings.verify() == plan["engine"]
            and source_hashes() == plan["source_hashes"]
            and file_hash(run / "catalog-plan.json") == plan_hash
            and stream_hash(run / "catalog-inputs.jsonl") == plan["inputs_sha256"]
            and require_current_catalog(connection) == plan["catalog"]
        )
    except (OSError, ValueError):
        return False


def _validate_receipt(receipt, plan, plan_hash, run):
    for key in ("schema_version", "rows"):
        value = receipt.get(key)
        expected = 1 if key == "schema_version" else plan["rows"]
        if not isinstance(value, int) or isinstance(value, bool) or value != expected:
            raise ValueError("Catalog probe receipt protocol/inventory mismatch")
    if (
        receipt.get("plan_sha256") != plan_hash
        or receipt.get("engine") != plan["engine"]
        or receipt.get("observations_sha256")
        != stream_hash(run / "catalog-observations.jsonl")
    ):
        raise ValueError("Catalog probe receipt provenance mismatch")
