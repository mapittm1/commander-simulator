"""Catalog-wide script availability, without claims of card or AI correctness."""

import hashlib
import uuid

from commander.catalog.database import get_card_details
from commander.engine.engine_audit_store import (
    MAX_AUDIT_CARDS,
    SCOPES,
    SCRIPT_STATUSES,
    canonical_record,
    ensure_audit_schema,
    read_audit,
)
from commander.engine.engine_gaps import classify_gap
from commander.engine.forge_decks import card_index, script_match
from commander.rules.legality import require_current_catalog

AUDITOR_VERSION = 2


def create_engine_audit(connection, settings) -> dict:
    """Validate provenance and atomically freeze all bounded catalog identities."""
    ensure_audit_schema(connection)
    catalog = require_current_catalog(connection)
    engine = settings.verify()
    index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
    audit_id = uuid.uuid4().hex
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        if require_current_catalog(connection) != catalog:
            raise ValueError("Catalog changed during audit preparation; retry")
        connection.execute("INSERT INTO engine_audits(id) VALUES (?)", (audit_id,))
        observation = _scan_catalog(connection, audit_id, index)
        if settings.verify() != engine:
            raise ValueError("Engine evidence changed during audit creation; retry")
        report = {
            "schema_version": 1,
            "auditor_version": AUDITOR_VERSION,
            "status": "audited",
            "catalog": catalog,
            "engine": {
                key: engine[key]
                for key in ("forge_version", "jar_sha256", "cards_sha256")
            },
            **observation,
            "basis": "exact_alias_and_safe_export_only",
            "rules_correctness": "not_verified",
            "ai_competence": "not_verified",
            "scope": "all_identities_currently_retained_in_local_catalog",
            "limitations": [
                "A script match does not validate Oracle text, interactions, construction rules, or AI behavior.",
                "Local imports retain older identities; this is not proof of coverage of every published Magic card.",
                "Commander scope uses the catalog legality field; this audit does not approve a deck.",
                "This snapshot must not replace current legality, bracket, and engine checks at launch.",
            ],
        }
        raw, digest = canonical_record(report)
        connection.execute(
            "UPDATE engine_audits SET sealed=1,report_json=?,report_hash=? WHERE id=?",
            (raw, digest, audit_id),
        )
    return read_audit(connection, audit_id)


def _scan_catalog(connection, audit_id, index) -> dict:
    counts = {scope: dict.fromkeys(SCRIPT_STATUSES, 0) for scope in SCOPES}
    fingerprint = hashlib.sha256()
    total = 0
    rows = connection.execute(
        "SELECT card_id,name,commander_legality,layout FROM cards ORDER BY card_id"
    )
    for row in rows:
        total += 1
        if total > MAX_AUDIT_CARDS:
            raise ValueError("Catalog exceeds the engine audit identity limit")
        snapshot = _snapshot(dict(row), index)
        if (
            snapshot["scope"] == "commander_legal"
            and snapshot["script_status"] != "script_present"
        ):
            details = get_card_details(connection, snapshot["card_id"])
            if details["name"] != snapshot["name"]:
                raise ValueError(
                    "Catalog name/detail mismatch during gap classification"
                )
            snapshot["gap"] = classify_gap(details, index, snapshot["script_status"])
        raw, digest = canonical_record(snapshot)
        counts[snapshot["scope"]][snapshot["script_status"]] += 1
        fingerprint.update((digest + "\n").encode("ascii"))
        connection.execute(
            "INSERT INTO engine_audit_cards "
            "(audit_id,card_id,name,scope,script_status,snapshot_json,snapshot_hash) "
            "VALUES (?,?,?,?,?,?,?)",
            (
                audit_id,
                snapshot["card_id"],
                snapshot["name"],
                snapshot["scope"],
                snapshot["script_status"],
                raw,
                digest,
            ),
        )
    if not total:
        raise ValueError("No catalog identities to audit; sync first")
    legal_total = sum(counts["commander_legal"].values())
    return {
        "total_identities": total,
        "counts": counts,
        "commander_legal_total": legal_total,
        "commander_script_match_fraction": (
            counts["commander_legal"]["script_present"] / legal_total
            if legal_total
            else None
        ),
        "cards_hash": fingerprint.hexdigest(),
    }


def _snapshot(card: dict, index: dict) -> dict:
    for key, limit in (
        ("card_id", 200),
        ("name", 300),
        ("layout", 100),
        ("commander_legality", 64),
    ):
        value = card[key]
        if value is None and key in {"layout", "commander_legality"}:
            continue
        if (
            not isinstance(value, str)
            or not value
            or len(value) > limit
            or not value.isprintable()
        ):
            raise ValueError("Invalid or oversized catalog audit field: " + key)
    legality = card["commander_legality"]
    scope = "unknown_legality"
    if legality == "legal":
        scope = "commander_legal"
    elif legality in {"not_legal", "banned"}:
        scope = "other_legality"
    return {
        **card,
        "scope": scope,
        **script_match(card["name"], index, card["card_id"]),
    }
