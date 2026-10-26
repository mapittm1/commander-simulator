"""Plain-text deck import, Commander legality checks, and validated storage."""

import re
from collections import Counter
from pathlib import Path
from sqlite3 import Connection

from database import find_deck_candidates
from deck_store import ensure_deck_schema, list_decks, save_deck
from legality import validate_entries

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
    matches = find_deck_candidates(connection, name)
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


def prepare_deck(connection: Connection, text: str, commander_names=()) -> dict:
    """Resolve the list, then run the versioned Commander legality validator."""
    main, commanders = _assign_commanders(text, commander_names)
    entries = _resolve_entries(connection, main, commanders)
    report = validate_entries(connection, entries)
    return {
        "cards": entries,
        "total": sum(entry["quantity"] for entry in entries),
        "issues": report["errors"],
        "legality": report,
        "warnings": report["blockers"]
        + ["Bracket compliance is evaluated separately."],
    }


# Retain the original import surface while storage has its own module.
__all__ = [
    "BRACKETS",
    "MAX_DECK_BYTES",
    "ensure_deck_schema",
    "list_decks",
    "save_deck",
    "parse_decklist",
    "prepare_deck",
    "read_decklist",
]
