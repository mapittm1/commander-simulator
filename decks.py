"""Plain-text deck import and preliminary catalog checks.

Full Commander legality and gameplay are delegated to the future engine adapter.
"""

import json
import re
from collections import Counter
from pathlib import Path
from sqlite3 import Connection

from database import find_cards

MAX_DECK_BYTES = 1024 * 1024
MAX_DECK_LINES = 2000
MAX_CARD_NAME_LENGTH = 300
MAX_CARD_QUANTITY = 100

BRACKETS = {1: "Exhibition", 2: "Core", 3: "Upgraded", 4: "Optimized", 5: "cEDH"}


def read_decklist(path: Path) -> str:
    """Read at most one MiB; filenames and file content are untrusted input."""
    with path.open("rb") as stream:
        data = stream.read(MAX_DECK_BYTES + 1)
    if len(data) > MAX_DECK_BYTES:
        raise ValueError("Decklist exceeds the 1 MiB size limit")
    return data.decode("utf-8-sig")


def parse_decklist(text: str) -> tuple[Counter[str], Counter[str]]:
    """Accept quantity/name lists and Commander/Mainboard/Sideboard sections."""
    if len(text.encode("utf-8")) > MAX_DECK_BYTES:
        raise ValueError("Decklist exceeds the 1 MiB size limit")
    lines = text.splitlines()
    if len(lines) > MAX_DECK_LINES:
        raise ValueError(f"Decklist exceeds the {MAX_DECK_LINES}-line limit")
    cards = Counter()
    commanders = Counter()
    section = "mainboard"
    for number, raw in enumerate(lines, 1):
        line = raw.strip()
        if not line or line.startswith(("#", "//")):
            continue
        heading = line.strip("[]:").casefold()
        if heading in (
            "commander",
            "commanders",
            "mainboard",
            "deck",
            "sideboard",
            "maybeboard",
        ):
            section = heading
            continue
        if section in ("sideboard", "maybeboard"):
            continue
        match = re.fullmatch(r"(\d+)x?\s+(.+)", line)
        if not match or int(match[1]) < 1:
            raise ValueError(
                f"Line {number}: expected a positive quantity followed by a card name"
            )
        quantity = int(match[1])
        if quantity > MAX_CARD_QUANTITY:
            raise ValueError(
                f"Line {number}: card quantity exceeds {MAX_CARD_QUANTITY}"
            )
        name = match[2].strip()
        if len(name) > MAX_CARD_NAME_LENGTH or any(ord(char) < 32 for char in name):
            raise ValueError(f"Line {number}: invalid or excessively long card name")
        # Strip the common set/collector metadata from Arena-style text exports.
        name = re.sub(r"\s+\([A-Za-z0-9]+\)\s+\S+(?:\s+\*F\*)?$", "", name)
        target = commanders if section in ("commander", "commanders") else cards
        target[name] += quantity
        if target[name] > MAX_CARD_QUANTITY:
            raise ValueError(
                f"Line {number}: combined card quantity exceeds {MAX_CARD_QUANTITY}"
            )
    return cards, commanders


def _resolve(connection: Connection, name: str) -> dict:
    matches = [
        card
        for card in find_cards(connection, name)
        if card["commander_legality"] in ("legal", "banned", "restricted")
    ]
    exact = [card for card in matches if card["name"].casefold() == name.casefold()]
    matches = exact or matches
    if len(matches) != 1:
        raise ValueError(f"Unknown or ambiguous playable card: {name}")
    return matches[0]


def _assign_commanders(text: str, commander_names) -> tuple[Counter, Counter]:
    """Separate the command zone from a sectioned or flat deck export."""
    main, commanders = parse_decklist(text)
    if len(commander_names) > 2 or sum(commanders.values()) > 2:
        raise ValueError("At most two commander cards can be specified")
    if commander_names and commanders:
        raise ValueError("Specify commanders in the list or with --commander, not both")
    for name in commander_names:
        commanders[name] += 1
        # A flat 100-card export commonly includes the commander already.
        if main[name]:
            main[name] -= 1
            if not main[name]:
                del main[name]
    if not commanders:
        raise ValueError("A Commander section or --commander is required")
    return main, commanders


def _resolve_entries(
    connection: Connection, main: Counter, commanders: Counter
) -> list:
    """Merge aliases that resolve to the same identity within a zone."""
    resolved = {}
    for zone, entries in (("commander", commanders), ("mainboard", main)):
        for name, quantity in entries.items():
            card = _resolve(connection, name)
            key = (card["card_id"], zone)
            if key not in resolved:
                resolved[key] = {"card": card, "quantity": 0, "zone": zone}
            resolved[key]["quantity"] += quantity
    return list(resolved.values())


def _copy_issues(entries: list) -> list[str]:
    """Find ordinary singleton violations, leaving special copy caps for the engine."""
    copies = Counter()
    cards = {}
    for entry in entries:
        card = entry["card"]
        copies[card["card_id"]] += entry["quantity"]
        cards[card["card_id"]] = card
    issues = []
    for card_id, quantity in copies.items():
        card = cards[card_id]
        if quantity <= 1 or (card["type_line"] or "").startswith("Basic "):
            continue
        oracle_text = card["oracle_text"] or ""
        any_number = (
            "a deck can have any number of cards named" in oracle_text.casefold()
        )
        limited = re.search(
            r"a deck can have up to (\w+) cards named", oracle_text, re.I
        )
        if not any_number and not limited:
            issues.append(f"Singleton violation: {quantity} copies of {card['name']}")
    return issues


def _construction_issues(entries: list) -> list[str]:
    """Check deck size, commander count, legality, and color identity."""
    total = sum(entry["quantity"] for entry in entries)
    commanders = [entry for entry in entries if entry["zone"] == "commander"]
    issues = []
    if total != 100:
        issues.append(f"Expected 100 cards including commanders; found {total}")
    if not 1 <= len(commanders) <= 2 or any(
        entry["quantity"] != 1 for entry in commanders
    ):
        issues.append("Expected one or two distinct commanders, one copy each")
    identity = set()
    for entry in commanders:
        identity.update(json.loads(entry["card"]["color_identity"]))
    for entry in entries:
        card = entry["card"]
        if card["commander_legality"] != "legal":
            issues.append(f"Not Commander-legal: {card['name']}")
        if not set(json.loads(card["color_identity"])).issubset(identity):
            issues.append(f"Outside commander color identity: {card['name']}")
    return issues


def prepare_deck(connection: Connection, text: str, commander_names=()) -> dict:
    """Resolve cards and report basic issues; never claim full legality."""
    main, commanders = _assign_commanders(text, commander_names)
    entries = _resolve_entries(connection, main, commanders)
    return {
        "cards": entries,
        "total": sum(entry["quantity"] for entry in entries),
        "issues": _construction_issues(entries) + _copy_issues(entries),
        "warnings": [
            "Preliminary checks only: commander eligibility, pairing, copy-limit "
            "exceptions, and bracket compliance require engine/review validation."
        ],
    }


def ensure_deck_schema(connection):
    """Create local deck storage tables without altering the card catalog."""
    connection.executescript("""
        CREATE TABLE IF NOT EXISTS decks (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            declared_bracket INTEGER NOT NULL CHECK(declared_bracket BETWEEN 1 AND 5),
            source_url TEXT,
            imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            validation_json TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS deck_cards (
            deck_id INTEGER NOT NULL REFERENCES decks(id),
            card_id TEXT NOT NULL REFERENCES cards(card_id),
            zone TEXT NOT NULL CHECK(zone IN ('mainboard', 'commander')),
            quantity INTEGER NOT NULL CHECK(quantity > 0),
            PRIMARY KEY(deck_id, card_id, zone)
        );
    """)


def save_deck(connection, name, bracket, prepared, source_url=None):
    """Persist a preliminarily checked deck and its declared bracket atomically."""
    name = name.strip()
    if not name or len(name) > 200 or any(ord(char) < 32 for char in name):
        raise ValueError(
            "Deck name must be 1–200 characters without control characters"
        )
    if bracket not in BRACKETS:
        raise ValueError("Bracket must be between 1 and 5")
    if prepared["issues"]:
        raise ValueError("; ".join(prepared["issues"]))
    ensure_deck_schema(connection)
    with connection:
        cursor = connection.execute(
            "INSERT INTO decks(name, declared_bracket, source_url, validation_json) VALUES (?, ?, ?, ?)",
            (
                name,
                bracket,
                source_url,
                json.dumps({"status": "preliminary", "warnings": prepared["warnings"]}),
            ),
        )
        deck_id = cursor.lastrowid
        connection.executemany(
            "INSERT INTO deck_cards VALUES (?, ?, ?, ?)",
            [
                (deck_id, entry["card"]["card_id"], entry["zone"], entry["quantity"])
                for entry in prepared["cards"]
            ],
        )
    return deck_id


def list_decks(connection):
    """List saved decks with their bracket declarations and validation status."""
    ensure_deck_schema(connection)
    return [dict(row) for row in connection.execute("SELECT * FROM decks ORDER BY id")]
