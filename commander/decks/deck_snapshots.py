"""Read and verify immutable snapshots without depending on deck storage services."""

import hashlib
from sqlite3 import Connection


def read_revision(connection: Connection, deck_id: int, version=None) -> dict:
    """Load exactly the requested revision, or latest, and check its content hash."""
    query = "SELECT * FROM deck_revisions WHERE deck_id = ?"
    parameters = [deck_id]
    if version is not None:
        query += " AND version = ?"
        parameters.append(version)
    row = connection.execute(
        query + " ORDER BY version DESC LIMIT 1", parameters
    ).fetchone()
    if row is None:
        raise ValueError(f"Unknown deck/revision: {deck_id}/{version}")
    revision = dict(row)
    digest = hashlib.sha256(revision["snapshot_json"].encode("utf-8")).hexdigest()
    if digest != revision["snapshot_hash"]:
        raise ValueError("Deck snapshot integrity check failed")
    return revision
