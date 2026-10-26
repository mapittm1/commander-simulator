"""Immutable, bounded engine-coverage snapshots and paginated historical inspection."""

import hashlib
import json

MAX_AUDIT_CARDS = 100000
SCOPES = ("commander_legal", "other_legality", "unknown_legality")
SCRIPT_STATUSES = ("script_present", "missing_script", "unsafe_export")
SCHEMA = """
CREATE TABLE IF NOT EXISTS engine_audits (
    id TEXT PRIMARY KEY,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sealed INTEGER NOT NULL DEFAULT 0 CHECK(sealed IN (0,1)),
    report_json TEXT,
    report_hash TEXT
);
CREATE TABLE IF NOT EXISTS engine_audit_cards (
    audit_id TEXT NOT NULL REFERENCES engine_audits(id),
    card_id TEXT NOT NULL,
    name TEXT NOT NULL,
    scope TEXT NOT NULL CHECK(scope IN ('commander_legal','other_legality','unknown_legality')),
    script_status TEXT NOT NULL CHECK(script_status IN ('script_present','missing_script','unsafe_export')),
    snapshot_json TEXT NOT NULL,
    snapshot_hash TEXT NOT NULL,
    PRIMARY KEY(audit_id,card_id)
);
CREATE INDEX IF NOT EXISTS engine_audit_filter
ON engine_audit_cards(audit_id,scope,script_status,name,card_id);
CREATE TRIGGER IF NOT EXISTS engine_audit_sealed
BEFORE UPDATE ON engine_audits
WHEN OLD.sealed=1 OR NEW.id IS NOT OLD.id OR NEW.created_at IS NOT OLD.created_at
BEGIN SELECT RAISE(ABORT, 'Sealed engine audits are immutable'); END;
CREATE TRIGGER IF NOT EXISTS engine_audit_no_delete
BEFORE DELETE ON engine_audits
BEGIN SELECT RAISE(ABORT, 'Engine audit history cannot be deleted'); END;
CREATE TRIGGER IF NOT EXISTS engine_audit_cards_sealed
BEFORE INSERT ON engine_audit_cards
WHEN (SELECT sealed FROM engine_audits WHERE id=NEW.audit_id)=1
BEGIN SELECT RAISE(ABORT, 'Sealed audit cards cannot be extended'); END;
CREATE TRIGGER IF NOT EXISTS engine_audit_cards_no_update
BEFORE UPDATE ON engine_audit_cards
BEGIN SELECT RAISE(ABORT, 'Engine audit cards are immutable'); END;
CREATE TRIGGER IF NOT EXISTS engine_audit_cards_no_delete
BEFORE DELETE ON engine_audit_cards
BEGIN SELECT RAISE(ABORT, 'Engine audit cards cannot be deleted'); END;
"""


def ensure_audit_schema(connection) -> None:
    """Do not implicitly commit a caller's unrelated transaction."""
    if connection.in_transaction:
        raise ValueError("Finish the active transaction before using engine audits")
    connection.executescript(SCHEMA)


def canonical_record(record: dict) -> tuple[str, str]:
    """Return deterministic JSON and a content integrity digest."""
    raw = json.dumps(record, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return raw, hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _decode(raw, digest) -> dict:
    if (
        not isinstance(raw, str)
        or hashlib.sha256(raw.encode("utf-8")).hexdigest() != digest
    ):
        raise ValueError("Engine audit integrity check failed")
    return json.loads(raw)


def read_audit(connection, audit_id: str) -> dict:
    """Read sealed historical evidence; this never approves a deck for gameplay."""
    if not isinstance(audit_id, str) or len(audit_id) != 32:
        raise ValueError("Expected a generated engine audit identifier")
    row = connection.execute(
        "SELECT * FROM engine_audits WHERE id=?", (audit_id,)
    ).fetchone()
    if row is None or row["sealed"] != 1:
        raise ValueError("Unknown or incomplete engine audit")
    return {
        "audit_id": audit_id,
        "created_at": row["created_at"],
        "report_hash": row["report_hash"],
        "report": _decode(row["report_json"], row["report_hash"]),
        "historical_snapshot_only": True,
    }


def show_audit(connection, audit_id: str) -> dict:
    """Inspect an existing audit without Java execution or current engine assets."""
    ensure_audit_schema(connection)
    return read_audit(connection, audit_id)


def audit_cards(connection, audit_id: str, filters=None) -> dict:
    """Return bounded, deterministic pages with per-card snapshot integrity checks."""
    options = {} if filters is None else filters
    if not isinstance(options, dict) or set(options) - {
        "scope",
        "status",
        "offset",
        "limit",
    }:
        raise ValueError("Unknown engine audit filters")
    scope, status = options.get("scope", "commander_legal"), options.get(
        "status", "missing_script"
    )
    offset, limit = options.get("offset", 0), options.get("limit", 100)
    _validate_page(scope, status, offset, limit)
    ensure_audit_schema(connection)
    stored = read_audit(connection, audit_id)
    rows = connection.execute(
        "SELECT snapshot_json,snapshot_hash FROM engine_audit_cards WHERE audit_id=? "
        "AND (?='all' OR scope=?) AND (?='all' OR script_status=?) "
        "ORDER BY name COLLATE NOCASE,card_id LIMIT ? OFFSET ?",
        (audit_id, scope, scope, status, status, limit, offset),
    )
    counts = stored["report"]["counts"]
    selected = SCOPES if scope == "all" else (scope,)
    statuses = SCRIPT_STATUSES if status == "all" else (status,)
    total = sum(counts[group][key] for group in selected for key in statuses)
    return {
        "audit_id": audit_id,
        "scope": scope,
        "status_filter": status,
        "offset": offset,
        "limit": limit,
        "total_matching": total,
        "cards": [_decode(row["snapshot_json"], row["snapshot_hash"]) for row in rows],
        "historical_snapshot_only": True,
    }


def _validate_page(scope, status, offset, limit) -> None:
    if scope not in (*SCOPES, "all") or status not in (*SCRIPT_STATUSES, "all"):
        raise ValueError("Unknown engine audit scope or script status")
    if (
        not isinstance(offset, int)
        or isinstance(offset, bool)
        or not 0 <= offset <= MAX_AUDIT_CARDS
    ):
        raise ValueError("Engine audit offset is outside its supported range")
    if not isinstance(limit, int) or isinstance(limit, bool) or not 1 <= limit <= 1000:
        raise ValueError(
            "Engine audit page size must be an integer from 1 through 1000"
        )
