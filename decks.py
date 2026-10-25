"""Plain-text deck import and preliminary catalog checks.

Full Commander legality and gameplay are delegated to the future engine adapter.
"""

import json
import re
from collections import Counter

from database import find_cards

BRACKETS = {1: "Exhibition", 2: "Core", 3: "Upgraded", 4: "Optimized", 5: "cEDH"}


def parse_decklist(text):
    """Accept quantity/name lists and Commander/Mainboard/Sideboard sections."""
    cards = Counter()
    commanders = Counter()
    section = "mainboard"
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith(("#", "//")):
            continue
        heading = line.strip("[]:").casefold()
        if heading in ("commander", "commanders", "mainboard", "deck", "sideboard", "maybeboard"):
            section = heading
            continue
        if section in ("sideboard", "maybeboard"):
            continue
        match = re.fullmatch(r"(\d+)x?\s+(.+)", line)
        if not match or int(match[1]) < 1:
            raise ValueError(f"Line {number}: expected a positive quantity followed by a card name")
        name = match[2].strip()
        # Strip the common set/collector metadata from Arena-style text exports.
        name = re.sub(r"\s+\([A-Za-z0-9]+\)\s+\S+(?:\s+\*F\*)?$", "", name)
        target = commanders if section in ("commander", "commanders") else cards
        target[name] += int(match[1])
    return cards, commanders


def _resolve(connection, name):
    matches = [card for card in find_cards(connection, name)
               if card["commander_legality"] in ("legal", "banned", "restricted")]
    exact = [card for card in matches if card["name"].casefold() == name.casefold()]
    matches = exact or matches
    if len(matches) != 1:
        raise ValueError(f"Unknown or ambiguous playable card: {name}")
    return matches[0]


def prepare_deck(connection, text, commander_names=()):
    """Resolve cards and report basic issues; never claim full legality."""
    main, listed_commanders = parse_decklist(text)
    if commander_names and listed_commanders:
        raise ValueError("Specify commanders in the list or with --commander, not both")
    if commander_names:
        for name in commander_names:
            listed_commanders[name] += 1
            # A flat 100-card export commonly includes the commander already.
            if main[name]:
                main[name] -= 1
                if not main[name]:
                    del main[name]
    if not listed_commanders:
        raise ValueError("A Commander section or --commander is required")
    resolved = {}
    commander_ids = []
    for zone, entries in (("commander", listed_commanders), ("mainboard", main)):
        for name, quantity in entries.items():
            card = _resolve(connection, name)
            key = (card["card_id"], zone)
            if key not in resolved:
                resolved[key] = {"card": card, "quantity": 0, "zone": zone}
            resolved[key]["quantity"] += quantity
            if zone == "commander":
                commander_ids.extend([card["card_id"]] * quantity)
    issues = []
    total = sum(entry["quantity"] for entry in resolved.values())
    if total != 100:
        issues.append(f"Expected 100 cards including commanders; found {total}")
    if not 1 <= len(commander_ids) <= 2 or len(set(commander_ids)) != len(commander_ids):
        issues.append("Expected one or two distinct commanders, one copy each")
    identity = set()
    for entry in resolved.values():
        if entry["zone"] == "commander":
            identity.update(json.loads(entry["card"]["color_identity"]))
    copies = Counter()
    for entry in resolved.values():
        card = entry["card"]
        copies[card["card_id"]] += entry["quantity"]
        if card["commander_legality"] != "legal":
            issues.append(f"Not Commander-legal: {card['name']}")
        if not set(json.loads(card["color_identity"])).issubset(identity):
            issues.append(f"Outside commander color identity: {card['name']}")
    for card_id, quantity in copies.items():
        card = next(entry["card"] for entry in resolved.values() if entry["card"]["card_id"] == card_id)
        text = card["oracle_text"] or ""
        if quantity > 1 and not (card["type_line"] or "").startswith("Basic "):
            any_number = "a deck can have any number of cards named" in text.casefold()
            limited = re.search(r"a deck can have up to (\w+) cards named", text, re.I)
            if not any_number and not limited:
                issues.append(f"Singleton violation: {quantity} copies of {card['name']}")
    warnings = ["Preliminary checks only: commander eligibility, pairing, copy-limit exceptions, and bracket compliance require engine/review validation."]
    return {"cards": list(resolved.values()), "total": total, "issues": issues, "warnings": warnings}


def ensure_deck_schema(connection):
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
    if bracket not in BRACKETS:
        raise ValueError("Bracket must be between 1 and 5")
    if prepared["issues"]:
        raise ValueError("; ".join(prepared["issues"]))
    ensure_deck_schema(connection)
    with connection:
        cursor = connection.execute(
            "INSERT INTO decks(name, declared_bracket, source_url, validation_json) VALUES (?, ?, ?, ?)",
            (name, bracket, source_url, json.dumps({"status": "preliminary", "warnings": prepared["warnings"]})))
        deck_id = cursor.lastrowid
        connection.executemany("INSERT INTO deck_cards VALUES (?, ?, ?, ?)",
                               [(deck_id, entry["card"]["card_id"], entry["zone"], entry["quantity"])
                                for entry in prepared["cards"]])
    return deck_id


def list_decks(connection):
    ensure_deck_schema(connection)
    return [dict(row) for row in connection.execute("SELECT * FROM decks ORDER BY id")]
