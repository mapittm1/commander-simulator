"""Fail-closed native checks for explicit functional Attraction printings."""

import json
import sys
from functools import lru_cache

from commander.catalog.printing_choices import require_printing_choice
from commander.engine.catalog_probe_io import source_hashes
from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import (
    file_hash,
    require_engine_capabilities,
    supported_java_version,
)
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.engine.native_runtime import (
    _engine_profile,
    _environment,
    create_run,
    native_plan_identity,
    require_bridge,
    settings_from_plan,
)
from commander.engine.printing_resources import edition_fingerprint
from commander.paths import PROJECT_ROOT

EVIDENCE_FIELDS = ("engine", "editions", "printings", "basis", "source_hashes")


def check_printing_choices(settings, revisions, index):
    """Check every selected functional printing before a game or batch job is created."""
    choices = {}
    for revision in revisions:
        for entry in revision["cards"]:
            if "printing" not in entry:
                continue
            choice = entry["printing"]
            require_printing_choice(
                choice, entry["card"]["card_id"], entry["card"]["name"]
            )
            if entry["zone"] != "attractions":
                raise ValueError(
                    "Native printing selection is restricted to Attractions"
                )
            selected = {**choice, "engine_name": index[entry["card"]["name"]]}
            if choice["id"] in choices and choices[choice["id"]] != selected:
                raise ValueError("Conflicting native printing identities")
            choices[choice["id"]] = selected
    if not choices:
        return None
    if len(choices) > 256:
        raise ValueError(
            "Native printing check exceeds its 256-selection process bound"
        )
    require_bridge()
    plan = {
        **native_plan_identity(settings),
        "source_hashes": source_hashes(),
        "editions": edition_fingerprint(settings.home),
        "printings": sorted(choices.values(), key=lambda choice: choice["id"]),
    }
    require_engine_capabilities(plan["engine"], [{"zone": "attractions"}])
    return json.loads(_run_printing_check(json.dumps(plan, sort_keys=True)))


@lru_cache(maxsize=64)
def _run_printing_check(frozen):
    plan = json.loads(frozen)
    settings = settings_from_plan(plan)
    run = create_run()
    path = run / "printing-plan.json"
    write_bounded_json(path, plan)
    digest = file_hash(path)
    with _engine_profile(settings, run):
        if edition_fingerprint(settings.home) != plan["editions"]:
            raise ValueError("Printing resources changed before worker launch")
        process = run_bounded(
            [
                sys.executable,
                "-I",
                str(PROJECT_ROOT / "tools/printing_worker.py"),
                str(path),
            ],
            settings.home,
            run / "printing.log",
            ProcessLimits(90, environment=_environment(run)),
        )
    if process.get("status") != "finished" or process.get("returncode") != 0:
        raise ValueError("Native printing check failed; inspect run " + run.name)
    receipt = read_bounded_json(run / "printing-receipt.json")
    validate_receipt(receipt, plan, digest)
    if (
        settings.verify() != plan["engine"]
        or source_hashes() != plan["source_hashes"]
        or edition_fingerprint(settings.home) != plan["editions"]
        or file_hash(path) != digest
    ):
        raise ValueError("Native printing check evidence changed during execution")
    report = {
        **receipt,
        "run_id": run.name,
        "source_hashes": plan["source_hashes"],
        "process": process,
        "log_sha256": file_hash(run / "printing.log"),
        "basis": "exact_native_collector_identity_and_attraction_lights",
    }
    write_bounded_json(run / "printing-report.json", report, indent=2)
    return json.dumps(report)


def validate_receipt(receipt, plan, digest):
    """Reject native fallbacks, missing/reordered selections and malformed observations."""
    if (
        not isinstance(receipt.get("schema_version"), int)
        or isinstance(receipt["schema_version"], bool)
    ) or receipt["schema_version"] != 1:
        raise ValueError("Invalid native printing receipt protocol")
    if (
        receipt.get("plan_sha256") != digest
        or receipt.get("engine") != plan["engine"]
        or receipt.get("editions") != plan["editions"]
        or not supported_java_version(receipt.get("java_version"))
    ):
        raise ValueError("Native printing receipt provenance mismatch")
    expected = [
        {
            "id": printing["id"],
            "name": printing["engine_name"],
            "set": printing["set"],
            "collector_number": printing["collector_number"],
            "attraction_lights": printing["attraction_lights"],
            "is_attraction": True,
        }
        for printing in plan["printings"]
    ]
    rows = receipt.get("printings")
    if not isinstance(rows, list) or rows != expected:
        raise ValueError(
            "Native printing metadata/light combination does not match selected identities"
        )
    for row in rows:
        if row["is_attraction"] is not True or any(
            (not isinstance(light, int) or isinstance(light, bool))
            for light in row["attraction_lights"]
        ):
            raise ValueError(
                "Native printing observations require explicit typed facts"
            )


def verify_printing_evidence(settings, report):
    """Recheck actual resources under the game profile lock immediately before launch."""
    if (
        settings.verify() != report["engine"]
        or edition_fingerprint(settings.home) != report["editions"]
        or source_hashes() != report["source_hashes"]
    ):
        raise ValueError("Attraction printing execution evidence changed before launch")
