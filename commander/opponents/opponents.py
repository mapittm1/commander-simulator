"""Immutable, source-scoped opponent collections pinned to reviewed deck revisions."""

import hashlib
import json
from datetime import datetime, timedelta, timezone
from sqlite3 import Connection

from commander.decks.deck_store import ensure_deck_schema, get_revision
from commander.opponents.opponent_manifest import (
    TARGET_OPPONENTS,
    collection_time,
    validate_manifest,
)
from commander.rules.brackets import load_policy, require_bracket_review

MAX_COLLECTION_AGE = timedelta(days=30)
SCHEMA = """
    CREATE TABLE IF NOT EXISTS opponent_collections (
        id INTEGER PRIMARY KEY,
        name TEXT NOT NULL,
        bracket INTEGER NOT NULL CHECK(bracket BETWEEN 1 AND 5),
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        snapshot_hash TEXT NOT NULL,
        snapshot_json TEXT NOT NULL
    );
    CREATE TRIGGER IF NOT EXISTS immutable_opponent_collection_update
    BEFORE UPDATE ON opponent_collections BEGIN
        SELECT RAISE(ABORT, 'Opponent collections are immutable');
    END;
    CREATE TRIGGER IF NOT EXISTS immutable_opponent_collection_delete
    BEFORE DELETE ON opponent_collections BEGIN
        SELECT RAISE(ABORT, 'Opponent collections are immutable');
    END;
"""


def _ensure_schema(connection: Connection) -> None:
    ensure_deck_schema(connection)
    connection.executescript(SCHEMA)


def _member(connection: Connection, entry: dict, bracket: int) -> dict:
    revision = get_revision(connection, entry["deck_id"], entry["version"])
    review = require_bracket_review(connection, entry["deck_id"], entry["version"])
    if revision["declared_bracket"] != bracket:
        raise ValueError("Opponent revision does not match the collection bracket")
    commanders = sorted(
        card["card"]["card_id"]
        for card in revision["cards"]
        if card["zone"] == "commander"
    )
    names = sorted(
        card["card"]["name"]
        for card in revision["cards"]
        if card["zone"] == "commander"
    )
    return {
        **entry,
        "revision_id": revision["id"],
        "deck_hash": revision["deck_hash"],
        "snapshot_hash": revision["snapshot_hash"],
        "commander_ids": commanders,
        "commanders": names,
        "review_id": review["review_id"],
        "policy_hash": review["policy_hash"],
        "catalog": review["catalog"],
    }


def _check_members(members: list) -> None:
    configurations = {tuple(member["commander_ids"]) for member in members}
    if len(configurations) != len(members):
        raise ValueError(
            "Only one representative list per commander configuration is allowed"
        )
    if len({member["deck_hash"] for member in members}) != len(members):
        raise ValueError(
            "Duplicate deck snapshots cannot occupy multiple opponent slots"
        )
    catalogs = {json.dumps(member["catalog"], sort_keys=True) for member in members}
    policies = {member["policy_hash"] for member in members}
    if len(catalogs) != 1 or len(policies) != 1:
        raise ValueError(
            "All opponent reviews must use the same current catalog and policy"
        )


def save_collection(connection: Connection, manifest: dict) -> int:
    """Validate attribution and all representatives, then append a frozen collection."""
    manifest = validate_manifest(manifest)
    _ensure_schema(connection)
    members = [
        _member(connection, entry, manifest["bracket"]) for entry in manifest["entries"]
    ]
    _check_members(members)
    members.sort(
        key=lambda member: (-member["observed_decks"], member["commander_ids"])
    )
    for rank, member in enumerate(members, 1):
        member["sample_rank"] = rank
    snapshot = {
        **manifest,
        "entries": members,
        "provenance_basis": "operator_supplied",
        "metric": "observed_deck_count",
        "target_count": TARGET_OPPONENTS,
        "coverage": (
            "ten_representatives" if len(members) == TARGET_OPPONENTS else "partial"
        ),
        "limitation": "Ranks order submitted representatives by reported sample counts; these are not verified top-ten rankings.",
    }
    raw = json.dumps(snapshot, sort_keys=True, ensure_ascii=False)
    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        current = connection.execute(
            "SELECT * FROM imports ORDER BY id DESC LIMIT 1"
        ).fetchone()
        if (
            dict(current) != members[0]["catalog"]
            or load_policy()["hash"] != members[0]["policy_hash"]
        ):
            raise ValueError(
                "Catalog or policy changed during collection creation; retry"
            )
        cursor = connection.execute(
            "INSERT INTO opponent_collections(name, bracket, snapshot_hash, snapshot_json) VALUES (?, ?, ?, ?)",
            (manifest["name"], manifest["bracket"], digest, raw),
        )
    return cursor.lastrowid


def get_collection(connection: Connection, collection_id: int) -> dict:
    """Return a historical collection without implying that it is currently usable."""
    _ensure_schema(connection)
    row = connection.execute(
        "SELECT * FROM opponent_collections WHERE id = ?", (collection_id,)
    ).fetchone()
    if row is None:
        raise ValueError(f"Unknown opponent collection: {collection_id}")
    digest = hashlib.sha256(row["snapshot_json"].encode("utf-8")).hexdigest()
    if digest != row["snapshot_hash"]:
        raise ValueError("Opponent collection integrity check failed")
    return {
        "id": row["id"],
        "created_at": row["created_at"],
        "snapshot_hash": row["snapshot_hash"],
        "collection": json.loads(row["snapshot_json"]),
    }


def list_collections(connection: Connection) -> list[dict]:
    """List collection headers; readiness requires a separate current-data check."""
    _ensure_schema(connection)
    return [
        dict(row)
        for row in connection.execute(
            "SELECT id, name, bracket, created_at, snapshot_hash FROM opponent_collections ORDER BY id"
        )
    ]


def require_collection(connection: Connection, collection_id: int) -> dict:
    """Require ten distinct reviewed representatives and fresh collection evidence."""
    stored = get_collection(connection, collection_id)
    collection = stored["collection"]
    if len(collection["entries"]) != TARGET_OPPONENTS:
        raise ValueError(
            "Opponent collection is partial; ten representatives are required"
        )
    if (
        datetime.now(timezone.utc) - collection_time(collection["collected_at"])
        > MAX_COLLECTION_AGE
    ):
        raise ValueError("Opponent source sample is stale; collect current evidence")
    for entry in collection["entries"]:
        current = _member(
            connection,
            {
                key: entry[key]
                for key in (
                    "deck_id",
                    "version",
                    "observed_decks",
                    "deck_source_url",
                    "author",
                )
            },
            collection["bracket"],
        )
        if any(
            current[key] != entry[key]
            for key in (
                "revision_id",
                "snapshot_hash",
                "review_id",
                "catalog",
                "policy_hash",
            )
        ):
            raise ValueError("Opponent evidence changed; create a refreshed collection")
    return stored
