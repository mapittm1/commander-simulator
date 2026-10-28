"""Exact English printing names resolve through their retained Oracle identity."""

import json


def initialize_aliases(connection):
    """Build the indexed projection once, including existing retained printings."""
    connection.executescript("""
        CREATE TABLE IF NOT EXISTS printing_aliases (
            scryfall_id TEXT NOT NULL REFERENCES printings(scryfall_id),
            name TEXT NOT NULL COLLATE NOCASE,
            PRIMARY KEY (scryfall_id, name)
        );
        CREATE INDEX IF NOT EXISTS printing_alias_name ON printing_aliases(name);
        CREATE TABLE IF NOT EXISTS catalog_alias_state (
            version INTEGER PRIMARY KEY CHECK (version = 1)
        );
    """)
    if connection.execute("SELECT version FROM catalog_alias_state").fetchone():
        return
    with connection:
        for row in connection.execute("SELECT raw_json FROM printings"):
            store_printing_aliases(connection, json.loads(row["raw_json"]))
        connection.execute("INSERT INTO catalog_alias_state VALUES (1)")


def store_printing_aliases(connection, card):
    """Replace a printing's aliases; retain ambiguity instead of choosing a card."""
    connection.execute(
        "DELETE FROM printing_aliases WHERE scryfall_id = ?", (card["id"],)
    )
    if card.get("lang") != "en":
        return
    names = set()
    for face in (card, *card.get("card_faces", [])):
        for field in ("printed_name", "flavor_name"):
            name = face.get(field)
            if name is None:
                continue
            if (
                not isinstance(name, str)
                or not name.strip()
                or len(name) > 300
                or not name.isprintable()
            ):
                raise ValueError("Invalid authoritative printing alias")
            names.add(name)
    connection.executemany(
        "INSERT OR IGNORE INTO printing_aliases VALUES (?, ?)",
        ((card["id"], name) for name in sorted(names)),
    )
