"""Versioned expectations and conservative evaluation of controlled engine states."""

import hashlib
import json
from pathlib import Path

from commander.json_safety import require_json_depth
from commander.paths import CONFIG_ROOT

from .ordering import ORDER_CASES, ordering_evidence
from .responses import (
    DETACHMENT_CASE,
    RESPONSE_CARDS,
    detachment_evidence,
    response_evidence,
)

SUITE_PATH = CONFIG_ROOT / "interaction_suite.json"
SUITE_ID = "raton_equipment_v7"
CASE_IDS = (
    "baseline",
    "no_equipment",
    "opponent_equipment",
    "target_exiled",
    "token_removed",
    "source_removed",
    "equipment_creature",
    "noncombat_damage",
    "combat_prevented",
    "noncombat_prevented",
    "combat_unaffected_by_noncombat_prevention",
    "combat_doubled",
    "noncombat_doubled",
    "doubled_damage_prevented",
    "initial_trigger_countered",
    "reflexive_trigger_countered",
    "graveyard_targeting_blocked",
    "targeting_blocked_in_response",
    "targeting_restored_before_selection",
    "target_zone_roundtrip",
    "assassin_protection_artifacts",
    "assassin_hexproof",
    "assassin_shroud",
    DETACHMENT_CASE,
    *ORDER_CASES,
)
PARTITIONS = {
    "equipment": CASE_IDS[:8],
    "damage": CASE_IDS[8:14],
    "countering": CASE_IDS[14:16],
    "targeting": CASE_IDS[16:20],
    "protection": CASE_IDS[20:24],
    "replacement_order": CASE_IDS[24:],
}
CARD_ROLES = {
    "source",
    "equipment",
    "modifier",
    "basic",
    "preventer",
    "noncombat_preventer",
    "doubler",
    "counter",
    "graveyard_guard",
    "artifact_protection",
    "hexproof",
    "shroud",
    "damage_reducer",
}
STAGES = (
    "before",
    "after_damage",
    "before_first",
    "after_first",
    "after_response",
    "after_second",
)
ORACLE_FIELDS = (
    "oracle_id",
    "name",
    "layout",
    "mana_cost",
    "type_line",
    "oracle_text",
    "power",
    "toughness",
    "loyalty",
    "color_identity",
)
MAX_RECEIPT_BYTES = 131072


def case_stages(case_id: str) -> tuple:
    """Require only repository-defined checkpoints, including the late protection response."""
    return (*STAGES, "after_detachment") if case_id == DETACHMENT_CASE else STAGES


def oracle_fingerprint(card: dict) -> str:
    """Pin rules characteristics without coupling fixtures to artwork or printing dates."""
    selected = {key: card.get(key) for key in ORACLE_FIELDS}
    raw = json.dumps(selected, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def read_bounded_json(path: Path, limit: int = MAX_RECEIPT_BYTES) -> dict:
    """Read a bounded local harness document and reject duplicate object fields."""
    return read_hashed_json(path, limit)[0]


def read_hashed_json(path: Path, limit: int = MAX_RECEIPT_BYTES) -> tuple:
    """Hash exactly the bounded bytes that were parsed, including formatting."""
    with path.open("rb") as stream:
        raw = stream.read(limit + 1)
    if len(raw) > limit:
        raise ValueError("Interaction harness document exceeds its size limit")
    try:
        text = raw.decode("utf-8-sig")
        require_json_depth(text, 32)
        result = json.loads(text, object_pairs_hook=_unique_fields)
    except (RecursionError, UnicodeError) as error:
        raise ValueError("Malformed interaction harness JSON") from error
    if not isinstance(result, dict):
        raise ValueError("Interaction harness document must be an object")
    return result, hashlib.sha256(raw).hexdigest()


def write_bounded_json(path: Path, value: dict, *, indent=None) -> None:
    """Bound generated documents before writing and never overwrite existing evidence."""
    options = {"indent": indent} if indent is not None else {"separators": (",", ":")}
    serialized = json.dumps(value, ensure_ascii=True, **options)
    if len(serialized.encode("utf-8")) > MAX_RECEIPT_BYTES:
        raise ValueError("Interaction harness document exceeds its size limit")
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(serialized)


def definition_paths() -> dict:
    """Return a fixed registry; configuration and receipts never select filesystem paths."""
    return {
        "interaction_suite.json": SUITE_PATH,
        **{
            f"interactions/{name}.json": CONFIG_ROOT / "interactions" / f"{name}.json"
            for name in PARTITIONS
        },
    }


def definition_fingerprint(hashes: dict) -> str:
    """Bind the suite manifest and every partition definition into one identity."""
    raw = json.dumps(hashes, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def definitions_unchanged(hashes: dict) -> bool:
    """Re-read only fixed bounded definition paths, rejecting changed or malformed files."""
    try:
        current = {
            name: read_hashed_json(path)[1] for name, path in definition_paths().items()
        }
        return hashes == current
    except (OSError, ValueError):
        return False


def load_definitions() -> dict:
    """Assemble the fixed families while retaining each definition's exact-byte hash."""
    paths = definition_paths()
    suite, digest = read_hashed_json(SUITE_PATH)
    if (
        set(suite)
        != {
            "schema_version",
            "suite_id",
            "reviewed_on",
            "rules_sources",
            "upstream_reference",
            "cards",
            "partitions",
        }
        or suite.get("schema_version") != 2
        or suite.get("suite_id") != SUITE_ID
        or suite.get("partitions") != list(PARTITIONS)
    ):
        raise ValueError("Unknown built-in interaction suite manifest or partitions")
    hashes = {"interaction_suite.json": digest}
    cases = []
    for name, case_ids in PARTITIONS.items():
        relative = f"interactions/{name}.json"
        document, hashes[relative] = read_hashed_json(paths[relative])
        rows = document.get("cases")
        if (
            set(document) != {"schema_version", "partition_id", "cases"}
            or document.get("schema_version") != 1
            or document.get("partition_id") != name
        ):
            raise ValueError("Unknown interaction partition header: " + name)
        if (
            not isinstance(rows, list)
            or not all(isinstance(row, dict) for row in rows)
            or tuple(row.get("id") for row in rows) != case_ids
        ):
            raise ValueError(
                "Unknown, duplicate, or incomplete interaction partition: " + name
            )
        cases.extend(rows)
    return {**suite, "cases": cases, "definition_hashes": hashes}


def _unique_fields(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ValueError("Duplicate interaction harness field")
        value[key] = item
    return value


def load_suite() -> dict:
    """Accept the built-in suite only; configuration supplies expectations, never code."""
    suite = load_definitions()
    if suite.get("schema_version") != 2 or suite.get("suite_id") != SUITE_ID:
        raise ValueError("Unknown built-in interaction suite version")
    cases = suite.get("cases")
    if (
        not isinstance(cases, list)
        or len(cases) != len(CASE_IDS)
        or not all(isinstance(case, dict) for case in cases)
        or tuple(case.get("id") for case in cases) != CASE_IDS
    ):
        raise ValueError("Unknown or incomplete built-in interaction suite")
    for case in cases:
        expected = case.get("expected")
        if (
            not isinstance(expected, dict)
            or set(expected) != set(case_stages(case["id"]))
            or any(
                not isinstance(expected[stage], dict) or not expected[stage]
                for stage in case_stages(case["id"])
            )
        ):
            raise ValueError("Interaction expectations require every state checkpoint")
    if not isinstance(suite.get("cards"), list) or len(suite["cards"]) != len(
        CARD_ROLES
    ):
        raise ValueError("Interaction suite requires its reviewed card identities")
    if any(
        not isinstance(card, dict)
        or set(card) != {"role", "name", "oracle_id", "oracle_sha256"}
        or any(
            not isinstance(value, str) or not 0 < len(value) <= 300
            for value in card.values()
        )
        for card in suite["cards"]
    ):
        raise ValueError("Malformed reviewed fixture identity")
    if {card["role"] for card in suite["cards"]} != CARD_ROLES or len(
        {card["oracle_id"] for card in suite["cards"]}
    ) != len(CARD_ROLES):
        raise ValueError("Interaction fixture identities or roles are duplicated")
    return suite


def evaluate_receipt(
    suite: dict, receipt: dict, process: dict, *, case_ids=CASE_IDS
) -> dict:
    """Distinguish a rules mismatch from absent evidence or a failed harness process."""
    if case_ids not in (CASE_IDS, *PARTITIONS.values()):
        return {
            "status": "inconclusive",
            "checks": [],
            "reason": "Unknown interaction partition",
        }
    valid_process = (
        process.get("status") == "finished" and process.get("returncode") == 0
    )
    cases = receipt.get("cases", [])
    expected_cases = [case for case in suite["cases"] if case["id"] in case_ids]
    complete = (
        isinstance(cases, list)
        and len(cases) == len(case_ids)
        and all(isinstance(case, dict) for case in cases)
    )
    if (
        not valid_process
        or not complete
        or tuple(case.get("id") for case in cases) != case_ids
        or tuple(case["id"] for case in expected_cases) != case_ids
    ):
        return {
            "status": "inconclusive",
            "checks": [],
            "reason": "Process failure or incomplete/duplicate/out-of-order case evidence",
        }
    checks = [
        _evaluate_case(expected, observed)
        for expected, observed in zip(expected_cases, cases)
    ]
    status = "passed"
    if any(check["status"] == "failed" for check in checks):
        status = "failed"
    elif any(check["status"] != "passed" for check in checks):
        status = "inconclusive"
    return {"status": status, "checks": checks}


def _evaluate_case(expected, observed) -> dict:
    states = observed.get("states")
    if (
        observed.get("error")
        or not isinstance(states, dict)
        or set(states) != set(case_stages(expected["id"]))
    ):
        return {
            "id": expected["id"],
            "status": "not_exercised",
            "reason": observed.get("error", "Missing controlled state checkpoints"),
        }
    mismatches = []
    for stage, fields in expected["expected"].items():
        actual = states[stage]
        if not isinstance(actual, dict) or any(key not in actual for key in fields):
            return {
                "id": expected["id"],
                "status": "not_exercised",
                "reason": "Missing state fields",
            }
        for key, value in fields.items():
            if type(actual[key]) is not type(value) or actual[key] != value:
                mismatches.append(
                    {
                        "stage": stage,
                        "field": key,
                        "expected": value,
                        "observed": actual[key],
                    }
                )
    status = "failed" if mismatches else "passed"
    for evidence in (
        _counter_evidence(expected["id"], states),
        _target_evidence(expected["id"], states),
        response_evidence(expected["id"], states),
        detachment_evidence(expected["id"], states),
        ordering_evidence(expected["id"], states),
    ):
        if evidence is not None:
            if evidence.get("status") == "not_exercised":
                return {"id": expected["id"], **evidence}
            mismatches.append(evidence)
            status = "failed"
    if any(row["stage"] == "before" for row in mismatches):
        status = "not_exercised"
    return {
        "id": expected["id"],
        "status": status,
        "mismatches": mismatches,
        "states": states,
    }


def _counter_evidence(case_id, states) -> dict | None:
    checkpoints = {
        "initial_trigger_countered": ("before_first", "after_damage"),
        "reflexive_trigger_countered": ("after_response", "after_first"),
    }
    if case_id not in checkpoints:
        return None
    current, previous = checkpoints[case_id]
    stack = states[current].get("stack_ability_ids")
    target = states[current].get("spell_target_ids")
    pending = states[previous].get("stack_ability_ids")
    if (
        any(
            not isinstance(ids, list)
            or any(
                not isinstance(identity, int) or isinstance(identity, bool)
                for identity in ids
            )
            for ids in (stack, target, pending)
        )
        or len(stack) != 2
        or len(target) != 1
        or len(pending) != 1
    ):
        return {
            "status": "not_exercised",
            "reason": "Missing or malformed raw counter target identities",
        }
    if stack[0] != stack[1] and stack[1] == target[0] == pending[0]:
        return None
    return {
        "stage": current,
        "field": "counter_target_identity",
        "expected": pending[0],
        "observed": {"stack_ability_ids": stack, "spell_target_ids": target},
    }


def _target_evidence(case_id, states) -> dict | None:
    if case_id not in {
        "targeting_blocked_in_response",
        "targeting_restored_before_selection",
        "target_zone_roundtrip",
        *RESPONSE_CARDS,
    }:
        return None
    first, response = states["after_first"], states["after_response"]
    try:
        original = _equipment_identity(first)
        current = _equipment_identity(response)
        first_target = _selected_identity(first)
        response_target = _selected_identity(response)
        valid = first_target == original == response_target
        if case_id == "target_zone_roundtrip":
            valid = _roundtrip_evidence(response, original, current) and valid
        else:
            valid = current == original and valid
    except (KeyError, TypeError, ValueError):
        return {
            "status": "not_exercised",
            "reason": "Missing or malformed raw card target identities",
        }
    if valid:
        return None
    return {
        "stage": "after_response",
        "field": "card_target_identity",
        "expected": "Original selected object; zone roundtrip creates a distinct object",
        "observed": {
            "original": original,
            "current": current,
            "selected": first_target,
            "pending": response_target,
            "transitions": response.get("equipment_transitions"),
        },
    }


def _equipment_identity(state) -> tuple:
    return _identity(state["equipment_id"], state["equipment_timestamp"])


def _selected_identity(state) -> tuple:
    ids, timestamps = state["targets"], state["target_timestamps"]
    if (
        not isinstance(ids, list)
        or not isinstance(timestamps, list)
        or len(ids) != 1
        or len(timestamps) != 1
    ):
        raise ValueError("A single card target is required")
    return _identity(ids[0], timestamps[0])


def _identity(card_id, timestamp) -> tuple:
    if any(
        not isinstance(value, int) or isinstance(value, bool) or value < 0
        for value in (card_id, timestamp)
    ):
        raise ValueError("Card identity requires nonnegative integer ID and timestamp")
    return card_id, timestamp


def _roundtrip_evidence(state, original, current) -> bool:
    transitions = state["equipment_transitions"]
    if (
        not isinstance(transitions, list)
        or len(transitions) != 2
        or not all(isinstance(item, dict) for item in transitions)
    ):
        raise ValueError("Two zone transitions are required")
    exiled, returned = transitions
    exile_identity = _identity(exiled["id"], exiled["timestamp"])
    returned_identity = _identity(returned["id"], returned["timestamp"])
    return (
        exiled["zone"] == "Exile"
        and returned["zone"] == "Graveyard"
        and original[0] == exile_identity[0] == current[0]
        and original[1] < exile_identity[1] < current[1]
        and returned_identity == current
    )
