"""Local card catalog; rules text is data, not executable game behavior."""

import json
import sqlite3
from pathlib import Path

from commander.catalog.printed_names import initialize_aliases, store_printing_aliases
from commander.paths import DATA_ROOT

DEFAULT_DATABASE = DATA_ROOT / "cards.sqlite3"


def connect(path=DEFAULT_DATABASE):
    """Open a foreign-key-enforced SQLite catalog and initialize its schema."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(path)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    connection.executescript("""
        CREATE TABLE IF NOT EXISTS cards (
            card_id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            mana_cost TEXT,
            mana_value REAL,
            type_line TEXT,
            oracle_text TEXT,
            colors TEXT NOT NULL,
            color_identity TEXT NOT NULL,
            power TEXT,
            toughness TEXT,
            commander_legality TEXT,
            layout TEXT
        );
        CREATE INDEX IF NOT EXISTS cards_name ON cards(name COLLATE NOCASE);
        CREATE TABLE IF NOT EXISTS card_details (
            card_id TEXT PRIMARY KEY REFERENCES cards(card_id),
            raw_json TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS printings (
            scryfall_id TEXT PRIMARY KEY,
            card_id TEXT NOT NULL REFERENCES cards(card_id),
            set_code TEXT,
            collector_number TEXT,
            language TEXT,
            image_uris TEXT NOT NULL,
            raw_json TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS printings_card ON printings(card_id);
        CREATE TABLE IF NOT EXISTS card_faces (
            scryfall_id TEXT NOT NULL REFERENCES printings(scryfall_id),
            face_index INTEGER NOT NULL,
            name TEXT NOT NULL,
            oracle_id TEXT,
            mana_cost TEXT,
            type_line TEXT,
            oracle_text TEXT,
            power TEXT,
            toughness TEXT,
            image_uris TEXT NOT NULL,
            PRIMARY KEY (scryfall_id, face_index)
        );
        CREATE INDEX IF NOT EXISTS faces_name ON card_faces(name COLLATE NOCASE);
        CREATE TABLE IF NOT EXISTS imports (
            id INTEGER PRIMARY KEY,
            source TEXT NOT NULL,
            source_updated_at TEXT,
            imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            records INTEGER NOT NULL
        );
    """)
    initialize_aliases(connection)
    return connection


def store_card(connection, card, *, allow_token_reassignment: bool = False):
    """Upsert one Scryfall record inside the caller's transaction."""
    # Reversible cards have Oracle IDs on their faces rather than at the root.
    card_id = card.get("oracle_id") or card["id"]
    connection.execute(
        """
        INSERT INTO cards VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(card_id) DO UPDATE SET
            name=excluded.name, mana_cost=excluded.mana_cost,
            mana_value=excluded.mana_value, type_line=excluded.type_line,
            oracle_text=excluded.oracle_text, colors=excluded.colors,
            color_identity=excluded.color_identity, power=excluded.power,
            toughness=excluded.toughness, commander_legality=excluded.commander_legality,
            layout=excluded.layout
    """,
        (
            card_id,
            card["name"],
            card.get("mana_cost"),
            card.get("cmc"),
            card.get("type_line"),
            card.get("oracle_text"),
            json.dumps(card.get("colors", [])),
            json.dumps(card.get("color_identity", [])),
            card.get("power"),
            card.get("toughness"),
            card.get("legalities", {}).get("commander"),
            card.get("layout"),
        ),
    )
    connection.execute(
        "INSERT INTO card_details VALUES (?, ?) "
        "ON CONFLICT(card_id) DO UPDATE SET raw_json=excluded.raw_json",
        (card_id, json.dumps(card, ensure_ascii=False)),
    )
    store_printing(connection, card, allow_token_reassignment=allow_token_reassignment)


def _can_reassign_token_printing(connection, card) -> bool:
    previous = connection.execute(
        "SELECT raw_json FROM printings WHERE scryfall_id = ?", (card["id"],)
    ).fetchone()
    old = json.loads(previous["raw_json"])
    return (
        old.get("layout") == card.get("layout") == "token"
        and old.get("legalities", {}).get("commander") == "not_legal"
        and card.get("legalities", {}).get("commander") == "not_legal"
        and all(
            card.get(key) and old.get(key) == card[key]
            for key in ("name", "set", "collector_number", "lang")
        )
    )


def store_printing(connection, card, *, allow_token_reassignment: bool = False):
    """Enrich printing metadata without replacing current Oracle data or freshness."""
    card_id = card.get("oracle_id") or card["id"]
    current = connection.execute(
        "SELECT name FROM cards WHERE card_id = ?", (card_id,)
    ).fetchone()
    if current is None or current["name"] != card.get("name"):
        raise ValueError(
            "Printing must match an existing exact catalog identity and name"
        )
    previous = connection.execute(
        "SELECT card_id FROM printings WHERE scryfall_id = ?", (card["id"],)
    ).fetchone()
    if (
        previous is not None
        and previous["card_id"] != card_id
        and not (
            allow_token_reassignment and _can_reassign_token_printing(connection, card)
        )
    ):
        raise ValueError("An existing printing cannot change Oracle identity")
    connection.execute(
        """
        INSERT INTO printings VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(scryfall_id) DO UPDATE SET
            card_id=excluded.card_id, set_code=excluded.set_code,
            collector_number=excluded.collector_number, language=excluded.language,
            image_uris=excluded.image_uris, raw_json=excluded.raw_json
    """,
        (
            card["id"],
            card_id,
            card.get("set"),
            card.get("collector_number"),
            card.get("lang"),
            json.dumps(card.get("image_uris", {})),
            json.dumps(card, ensure_ascii=False),
        ),
    )
    connection.execute("DELETE FROM card_faces WHERE scryfall_id = ?", (card["id"],))
    for index, face in enumerate(card.get("card_faces", [])):
        connection.execute(
            "INSERT INTO card_faces VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                card["id"],
                index,
                face["name"],
                face.get("oracle_id"),
                face.get("mana_cost"),
                face.get("type_line"),
                face.get("oracle_text"),
                face.get("power"),
                face.get("toughness"),
                json.dumps(face.get("image_uris", {})),
            ),
        )
    store_printing_aliases(connection, card)


def find_cards(connection, name):
    """Return exact case-insensitive matches, including individual face names."""
    return [
        dict(row)
        for row in connection.execute(
            """
        SELECT DISTINCT cards.* FROM cards
        LEFT JOIN printings USING (card_id)
        LEFT JOIN card_faces USING (scryfall_id)
        WHERE cards.name = ? COLLATE NOCASE OR card_faces.name = ? COLLATE NOCASE
        OR cards.card_id IN (
            SELECT printings.card_id FROM printing_aliases
            JOIN printings USING (scryfall_id)
            WHERE printing_aliases.name = ? COLLATE NOCASE
        )
        ORDER BY cards.card_id
    """,
            (name, name, name),
        )
    ]


def find_deck_candidates(connection, name: str) -> list[dict]:
    """Keep game-card candidates and exact-name priority; legality still requires validation."""
    matches = [
        card
        for card in find_cards(connection, name)
        if card["commander_legality"] in ("legal", "banned", "restricted")
    ]
    exact = [card for card in matches if card["name"].casefold() == name.casefold()]
    return exact or matches


def catalog_stats(connection):
    """Count catalog records, printings, faces, and completed imports."""
    return {
        table: connection.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        for table in ("cards", "printings", "card_faces", "imports")
    }


def get_card_details(connection: sqlite3.Connection, card_id: str) -> dict:
    """Read the latest authoritative card record or report missing catalog data."""
    row = connection.execute(
        "SELECT raw_json FROM card_details WHERE card_id = ?", (card_id,)
    ).fetchone()
    if row is None:
        raise ValueError(f"Missing catalog record: {card_id}")
    return json.loads(row["raw_json"])
