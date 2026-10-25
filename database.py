"""Local card catalog; rules text is data, not executable game behavior."""

import json
import sqlite3
from pathlib import Path

DEFAULT_DATABASE = Path(__file__).resolve().parent / "data" / "cards.sqlite3"


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
    return connection


def store_card(connection, card):
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
        ORDER BY cards.card_id
    """,
            (name, name),
        )
    ]


def catalog_stats(connection):
    """Count catalog records, printings, faces, and completed imports."""
    return {
        table: connection.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        for table in ("cards", "printings", "card_faces", "imports")
    }
