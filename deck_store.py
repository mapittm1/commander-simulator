"""Versioned deck storage independent of file parsing or user interfaces."""

import hashlib
import json
from sqlite3 import Connection

from deck_snapshots import read_revision
from legality import require_legal_entries, validate_entries

SCHEMA = """
    CREATE TABLE IF NOT EXISTS decks (
        id INTEGER PRIMARY KEY, name TEXT NOT NULL,
        declared_bracket INTEGER NOT NULL CHECK(declared_bracket BETWEEN 1 AND 5),
        source_url TEXT, imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        validation_json TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS deck_cards (
        deck_id INTEGER NOT NULL REFERENCES decks(id),
        card_id TEXT NOT NULL REFERENCES cards(card_id),
        zone TEXT NOT NULL CHECK(zone IN ('mainboard', 'commander')),
        quantity INTEGER NOT NULL CHECK(quantity > 0),
        PRIMARY KEY(deck_id, card_id, zone)
    );
    CREATE TABLE IF NOT EXISTS deck_revisions (
        id INTEGER PRIMARY KEY,
        deck_id INTEGER NOT NULL REFERENCES decks(id),
        version INTEGER NOT NULL CHECK(version > 0),
        name TEXT NOT NULL,
        declared_bracket INTEGER NOT NULL CHECK(declared_bracket BETWEEN 1 AND 5),
        source_url TEXT,
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        deck_hash TEXT NOT NULL,
        snapshot_hash TEXT NOT NULL,
        validation_json TEXT NOT NULL,
        snapshot_json TEXT NOT NULL,
        UNIQUE(deck_id, version)
    );
    CREATE TRIGGER IF NOT EXISTS immutable_deck_revision_update
    BEFORE UPDATE ON deck_revisions BEGIN
        SELECT RAISE(ABORT, 'Deck revisions are immutable');
    END;
    CREATE TRIGGER IF NOT EXISTS immutable_deck_revision_delete
    BEFORE DELETE ON deck_revisions BEGIN
        SELECT RAISE(ABORT, 'Deck revisions are immutable');
    END;
"""


def _snapshot(connection: Connection, entries: list) -> str:
    """Freeze names, quantities, zones, and current Oracle records for this revision."""
    snapshot = []
    for entry in entries:
        card_id = entry["card"]["card_id"]
        row = connection.execute(
            "SELECT raw_json FROM card_details WHERE card_id = ?", (card_id,)
        ).fetchone()
        if row is None:
            raise ValueError(f"Missing card details for snapshot: {card_id}")
        oracle = json.loads(row["raw_json"])
        snapshot.append(
            {
                "card": {"card_id": card_id, "name": oracle["name"]},
                "quantity": entry["quantity"],
                "zone": entry["zone"],
                "oracle": oracle,
            }
        )
    snapshot.sort(key=lambda entry: (entry["zone"], entry["card"]["card_id"]))
    return json.dumps(snapshot, ensure_ascii=False, sort_keys=True)


def _insert_revision(connection, metadata, entries, report) -> int:
    snapshot = _snapshot(connection, entries)
    snapshot_hash = hashlib.sha256(snapshot.encode("utf-8")).hexdigest()
    version = connection.execute(
        "SELECT COALESCE(MAX(version), 0) + 1 FROM deck_revisions WHERE deck_id = ?",
        (metadata["id"],),
    ).fetchone()[0]
    cursor = connection.execute(
        """INSERT INTO deck_revisions (
            deck_id, version, name, declared_bracket, source_url, deck_hash,
            snapshot_hash, validation_json, snapshot_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (
            metadata["id"],
            version,
            metadata["name"],
            metadata["declared_bracket"],
            metadata["source_url"],
            report["deck_hash"],
            snapshot_hash,
            json.dumps(report),
            snapshot,
        ),
    )
    return cursor.lastrowid


def ensure_deck_schema(connection: Connection) -> None:
    """Create versioned tables and migrate unversioned decks once, preserving IDs."""
    if connection.in_transaction:
        raise ValueError(
            "Finish the active transaction before initializing deck storage"
        )
    connection.executescript(SCHEMA)
    legacy = connection.execute(
        "SELECT * FROM decks WHERE NOT EXISTS "
        "(SELECT 1 FROM deck_revisions WHERE deck_id = decks.id)"
    ).fetchall()
    with connection:
        for row in legacy:
            entries = [
                {
                    "card": {"card_id": card["card_id"]},
                    "zone": card["zone"],
                    "quantity": card["quantity"],
                }
                for card in connection.execute(
                    "SELECT * FROM deck_cards WHERE deck_id = ?", (row["id"],)
                )
            ]
            report = validate_entries(connection, entries)
            report["migration_note"] = (
                "Legacy list; Oracle snapshot captured at migration"
            )
            report["legacy_validation"] = json.loads(row["validation_json"])
            _insert_revision(connection, dict(row), entries, report)


def _validate_metadata(name: str, bracket: int) -> str:
    name = name.strip()
    if not name or len(name) > 200 or any(ord(char) < 32 for char in name):
        raise ValueError(
            "Deck name must be 1–200 characters without control characters"
        )
    if (
        not isinstance(bracket, int)
        or isinstance(bracket, bool)
        or bracket not in range(1, 6)
    ):
        raise ValueError("Bracket must be between 1 and 5")
    return name


def save_deck(connection, name, bracket, prepared, source_url=None):
    """Create a deck and its first legal revision in one transaction."""
    name = _validate_metadata(name, bracket)
    ensure_deck_schema(connection)
    with connection:
        # Acquire the write lock before checking the catalog to prevent a refresh race.
        connection.execute("BEGIN IMMEDIATE")
        report = require_legal_entries(connection, prepared["cards"])
        cursor = connection.execute(
            "INSERT INTO decks(name, declared_bracket, source_url, validation_json) "
            "VALUES (?, ?, ?, ?)",
            (name, bracket, source_url, json.dumps(report)),
        )
        metadata = {
            "id": cursor.lastrowid,
            "name": name,
            "declared_bracket": bracket,
            "source_url": source_url,
        }
        _insert_revision(connection, metadata, prepared["cards"], report)
        _update_projection(connection, metadata, prepared["cards"], report)
    return metadata["id"]


def _update_projection(connection, metadata, entries, report):
    """Maintain legacy latest-deck tables while revisions remain authoritative."""
    connection.execute(
        "UPDATE decks SET declared_bracket = ?, source_url = ?, validation_json = ? "
        "WHERE id = ?",
        (
            metadata["declared_bracket"],
            metadata["source_url"],
            json.dumps(report),
            metadata["id"],
        ),
    )
    connection.execute("DELETE FROM deck_cards WHERE deck_id = ?", (metadata["id"],))
    connection.executemany(
        "INSERT INTO deck_cards VALUES (?, ?, ?, ?)",
        [
            (metadata["id"], entry["card"]["card_id"], entry["zone"], entry["quantity"])
            for entry in entries
        ],
    )


def save_revision(connection, deck_id: int, prepared: dict, bracket=None) -> int:
    """Append a validated revision without changing any previous snapshot."""
    ensure_deck_schema(connection)
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        row = connection.execute(
            "SELECT * FROM decks WHERE id = ?", (deck_id,)
        ).fetchone()
        if row is None:
            raise ValueError(f"Unknown deck: {deck_id}")
        metadata = dict(row)
        if bracket is not None:
            _validate_metadata(metadata["name"], bracket)
            metadata["declared_bracket"] = bracket
        report = require_legal_entries(connection, prepared["cards"])
        revision_id = _insert_revision(connection, metadata, prepared["cards"], report)
        _update_projection(connection, metadata, prepared["cards"], report)
    return revision_id


def get_revision(connection, deck_id: int, version=None) -> dict:
    """Read a specific version, or the latest, and verify its snapshot hash."""
    ensure_deck_schema(connection)
    revision = read_revision(connection, deck_id, version)
    revision["cards"] = json.loads(revision.pop("snapshot_json"))
    revision["validation"] = json.loads(revision.pop("validation_json"))
    return revision


def list_revisions(connection, deck_id: int) -> list[dict]:
    """List immutable revisions without expanding their Oracle snapshots."""
    ensure_deck_schema(connection)
    if (
        connection.execute("SELECT 1 FROM decks WHERE id = ?", (deck_id,)).fetchone()
        is None
    ):
        raise ValueError(f"Unknown deck: {deck_id}")
    return [
        dict(row)
        for row in connection.execute(
            "SELECT id, version, created_at, declared_bracket, deck_hash, snapshot_hash "
            "FROM deck_revisions WHERE deck_id = ? ORDER BY version",
            (deck_id,),
        )
    ]


def list_decks(connection) -> list[dict]:
    """List deck headers and the IDs of their current revisions."""
    ensure_deck_schema(connection)
    return [
        dict(row)
        for row in connection.execute(
            "SELECT decks.*, deck_revisions.id AS revision_id, deck_revisions.version "
            "FROM decks JOIN deck_revisions ON deck_revisions.deck_id = decks.id "
            "WHERE deck_revisions.version = (SELECT MAX(version) FROM deck_revisions r "
            "WHERE r.deck_id = decks.id) ORDER BY decks.id"
        )
    ]
