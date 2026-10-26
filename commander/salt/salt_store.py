"""Immutable salt evidence resolved to catalog identities with explicit unknowns."""

import hashlib
import json
import re
import uuid

from commander.catalog.database import find_deck_candidates
from commander.rules.legality import require_current_catalog
from commander.salt.salt_data import validate_salt_manifest

SCHEMA = """
CREATE TABLE IF NOT EXISTS salt_snapshots (
    id TEXT PRIMARY KEY,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    snapshot_json TEXT NOT NULL,
    snapshot_hash TEXT NOT NULL
);
CREATE TRIGGER IF NOT EXISTS salt_snapshot_no_update
BEFORE UPDATE ON salt_snapshots
BEGIN SELECT RAISE(ABORT, 'Salt snapshots are immutable'); END;
CREATE TRIGGER IF NOT EXISTS salt_snapshot_no_delete
BEFORE DELETE ON salt_snapshots
BEGIN SELECT RAISE(ABORT, 'Salt snapshot history cannot be deleted'); END;
"""


def ensure_salt_schema(connection) -> None:
    """Never commit unrelated caller work while initializing the source store."""
    if connection.in_transaction:
        raise ValueError("Finish the active transaction before using salt snapshots")
    connection.executescript(SCHEMA)


def save_salt_snapshot(connection, data: dict) -> dict:
    """Resolve one local, operator-attributed sample inside an atomic catalog transaction."""
    snapshot = validate_salt_manifest(data)
    ensure_salt_schema(connection)
    snapshot_id = uuid.uuid4().hex
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        snapshot["catalog"] = require_current_catalog(connection)
        snapshot["verification_basis"] = "operator_provided_source_attribution"
        snapshot["cards"] = [_resolve(connection, entry) for entry in snapshot["cards"]]
        resolved = [
            entry["card_id"]
            for entry in snapshot["cards"]
            if entry["card_id"] is not None
        ]
        if len(resolved) != len(set(resolved)):
            raise ValueError(
                "Multiple salt entries resolve to the same catalog identity"
            )
        snapshot["resolved_cards"] = len(resolved)
        snapshot["unresolved_cards"] = len(snapshot["cards"]) - len(resolved)
        raw = json.dumps(
            snapshot, sort_keys=True, separators=(",", ":"), ensure_ascii=False
        )
        digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
        connection.execute(
            "INSERT INTO salt_snapshots(id,snapshot_json,snapshot_hash) VALUES (?,?,?)",
            (snapshot_id, raw, digest),
        )
    return read_salt_snapshot(connection, snapshot_id)


def _resolve(connection, entry) -> dict:
    matches = find_deck_candidates(connection, entry["source_name"])
    if len(matches) != 1:
        return {
            **entry,
            "card_id": None,
            "catalog_name": None,
            "resolution": "unknown" if not matches else "ambiguous",
        }
    return {
        **entry,
        "card_id": matches[0]["card_id"],
        "catalog_name": matches[0]["name"],
        "resolution": "resolved",
    }


def read_salt_snapshot(connection, snapshot_id: str) -> dict:
    """Read frozen source evidence inside or outside a caller's read transaction."""
    if not isinstance(snapshot_id, str) or not re.fullmatch(
        r"[0-9a-f]{32}", snapshot_id
    ):
        raise ValueError("Expected a generated salt snapshot identifier")
    row = connection.execute(
        "SELECT * FROM salt_snapshots WHERE id=?", (snapshot_id,)
    ).fetchone()
    if row is None:
        raise ValueError("Unknown salt snapshot")
    if (
        hashlib.sha256(row["snapshot_json"].encode("utf-8")).hexdigest()
        != row["snapshot_hash"]
    ):
        raise ValueError("Salt snapshot integrity check failed")
    return {
        "snapshot_id": snapshot_id,
        "created_at": row["created_at"],
        "snapshot_hash": row["snapshot_hash"],
        "snapshot": json.loads(row["snapshot_json"]),
    }


def show_salt_snapshot(connection, snapshot_id: str) -> dict:
    """Inspect historical evidence without recalculating or refreshing its ratings."""
    ensure_salt_schema(connection)
    return read_salt_snapshot(connection, snapshot_id)
