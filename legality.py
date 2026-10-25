"""Commander deck-construction gate against a versioned local card catalog.

References: Wizards Comprehensive Rules 903, 702.124, and card-specific Oracle text.
Unimplemented deck-construction choices block approval rather than guessing.
"""

import hashlib
import json
import re
from collections import Counter
from datetime import datetime, timedelta, timezone
from sqlite3 import Connection

RULES_VERSION = "2026-09-25"
RULES_SOURCE = "https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt"
VALIDATOR_VERSION = 1
MAX_CATALOG_AGE = timedelta(days=7)
LAND_COLORS = {
    "Plains": "W",
    "Island": "U",
    "Swamp": "B",
    "Mountain": "R",
    "Forest": "G",
}
NUMBER_WORDS = {
    "one": 1,
    "two": 2,
    "three": 3,
    "four": 4,
    "five": 5,
    "six": 6,
    "seven": 7,
    "eight": 8,
    "nine": 9,
    "ten": 10,
    "eleven": 11,
    "twelve": 12,
    "thirteen": 13,
    "fourteen": 14,
    "fifteen": 15,
    "sixteen": 16,
    "seventeen": 17,
    "eighteen": 18,
    "nineteen": 19,
    "twenty": 20,
}


def front_face(card: dict) -> dict:
    """Use front characteristics for commander eligibility, not the reverse face."""
    return (card.get("card_faces") or [card])[0]


def _types(card: dict) -> tuple[set[str], set[str]]:
    parts = (front_face(card).get("type_line") or "").partition(" — ")
    return set(parts[0].split()), set(parts[2].split())


def _oracle(card: dict) -> str:
    return front_face(card).get("oracle_text", "").replace("’", "'")


def _abilities(card: dict) -> set[str]:
    return {
        line.split(" (", 1)[0].strip().casefold() for line in _oracle(card).splitlines()
    }


def eligible_commander(card: dict) -> bool:
    """Check CR 903.3 and explicit card-specific commander permission."""
    types, subtypes = _types(card)
    face = front_face(card)
    standard = "Legendary" in types and (
        "Creature" in types
        or "Vehicle" in subtypes
        or (
            "Spacecraft" in subtypes
            and face.get("power") is not None
            and face.get("toughness") is not None
        )
    )
    permission = re.search(
        rf"^(?:{re.escape(face['name'])}|This card) can be your commander\.$",
        _oracle(card),
        re.MULTILINE,
    )
    # A characteristic-defining ability can make a card a creature in the deck.
    names = "|".join(
        re.escape(name) for name in (face["name"], face["name"].split(",")[0])
    )
    off_battlefield_creature = re.search(
        rf"^As long as (?:{names}) isn't on the battlefield, it's a [^\n.]+ "
        r"creature in addition to its other types\.$",
        _oracle(card),
        re.MULTILINE,
    )
    return (
        standard
        or bool(permission)
        or ("Legendary" in types and bool(off_battlefield_creature))
    )


def _background(card: dict) -> bool:
    types, subtypes = _types(card)
    return {"Legendary", "Enchantment"}.issubset(types) and "Background" in subtypes


def _named_partner(first: dict, second: dict) -> bool:
    def names(card):
        return {
            line.removeprefix("partner with ")
            for line in _abilities(card)
            if line.startswith("partner with ")
        }

    return front_face(second)["name"].casefold() in names(first) and front_face(first)[
        "name"
    ].casefold() in names(second)


def _doctor_pair(first: dict, second: dict) -> bool:
    types, subtypes = _types(second)
    companion_types, _ = _types(first)
    return (
        {"Legendary", "Creature"}.issubset(companion_types)
        and "doctor's companion" in _abilities(first)
        and {"Legendary", "Creature"}.issubset(types)
        and subtypes == {"Time", "Lord", "Doctor"}
    )


def compatible_commanders(first: dict, second: dict) -> bool:
    """Require the same partner variant or a specific Background/Doctor pairing."""
    first_abilities, second_abilities = _abilities(first), _abilities(second)
    shared = first_abilities & second_abilities
    partner_variants = {
        "partner",
        "friends forever",
        "partner—friends forever",
        "partner—character select",
        "partner—father & son",
        "partner—survivors",
    }
    if shared & partner_variants:
        return eligible_commander(first) and eligible_commander(second)
    if _named_partner(first, second):
        return eligible_commander(first) and eligible_commander(second)
    for primary, secondary in ((first, second), (second, first)):
        if eligible_commander(primary) and (
            ("choose a background" in _abilities(primary) and _background(secondary))
            or _doctor_pair(secondary, primary)
        ):
            return True
    return False


def _commander_errors(entries: list, cards: dict) -> list[str]:
    commanders = [entry for entry in entries if entry["zone"] == "commander"]
    if not 1 <= len(commanders) <= 2 or any(
        entry["quantity"] != 1 for entry in commanders
    ):
        return ["Expected one or two distinct commanders, one copy each (CR 903.3)"]
    chosen = [cards[entry["card"]["card_id"]] for entry in commanders]
    if len(chosen) == 2:
        return (
            []
            if compatible_commanders(*chosen)
            else ["The selected commanders do not form a legal pair (CR 702.124)"]
        )
    return (
        []
        if eligible_commander(chosen[0])
        else [f"Not eligible to be a commander: {chosen[0]['name']} (CR 903.3)"]
    )


def _copy_limit(card: dict) -> int | None:
    """Return a finite cap, unlimited copies, or raise for an unknown cap."""
    types, _ = _types(card)
    if {"Basic", "Land"}.issubset(types):
        return None
    text = _oracle(card)
    name = re.escape(card["name"])
    if re.search(rf"A deck can have any number of cards named {name}\.", text):
        return None
    match = re.search(rf"A deck can have up to (\w+) cards named {name}\.", text)
    if match:
        word = match[1].casefold()
        if word.isdigit():
            return int(word)
        if word in NUMBER_WORDS:
            return NUMBER_WORDS[word]
        raise ValueError(f"Unrecognized copy limit: {card['name']}")
    return 1


def _card_errors(entries: list, cards: dict) -> list[str]:
    identity = set()
    for entry in entries:
        if entry["zone"] == "commander":
            identity.update(cards[entry["card"]["card_id"]].get("color_identity", []))
    errors = []
    copies = Counter()
    for entry in entries:
        card = cards[entry["card"]["card_id"]]
        copies[entry["card"]["card_id"]] += entry["quantity"]
        if card.get("legalities", {}).get("commander") != "legal":
            errors.append(f"Not Commander-legal: {card['name']}")
        if not set(card.get("color_identity", [])).issubset(identity):
            errors.append(f"Outside commander color identity: {card['name']}")
        _, subtypes = _types(card)
        required = {color for land, color in LAND_COLORS.items() if land in subtypes}
        if set(card.get("color_identity", [])).issubset(
            identity
        ) and not required.issubset(identity):
            errors.append(
                f"Basic land type outside commander colors: {card['name']} (CR 903.5d)"
            )
    for card_id, quantity in copies.items():
        limit = _copy_limit(cards[card_id])
        if limit is not None and quantity > limit:
            errors.append(
                f"Copy limit exceeded: {cards[card_id]['name']} ({quantity}, allowed {limit})"
            )
    return errors


def _load_cards(connection: Connection, entries: list) -> dict:
    """Read authoritative catalog records rather than trusting submitted card fields."""
    cards = {}
    for entry in entries:
        card_id = entry["card"]["card_id"]
        row = connection.execute(
            "SELECT raw_json FROM card_details WHERE card_id = ?",
            (card_id,),
        ).fetchone()
        if row is None:
            raise ValueError(f"Missing catalog record: {card_id}")
        cards[card_id] = json.loads(row[0])
    return cards


def _catalog_evidence(connection: Connection, now: datetime) -> tuple[dict, list[str]]:
    row = connection.execute(
        "SELECT * FROM imports ORDER BY id DESC LIMIT 1"
    ).fetchone()
    if row is None:
        return {}, ["No versioned card import; run sync before validating"]
    evidence = dict(row)
    if evidence["source"] not in {"oracle_cards", "default_cards", "all_cards"}:
        return evidence, ["Catalog source is unverified; run sync before validating"]
    try:
        updated = datetime.fromisoformat(evidence["source_updated_at"])
        if updated.tzinfo is None:
            raise ValueError("Missing timezone")
        if now - updated > MAX_CATALOG_AGE or updated - now > timedelta(minutes=5):
            return evidence, ["Catalog timestamp is stale or in the future; run sync"]
    except (TypeError, ValueError):
        return evidence, ["Catalog timestamp is missing or invalid; run sync"]
    return evidence, []


def validate_entries(connection: Connection, entries: list, now=None) -> dict:
    """Return legal/illegal/needs_review; only legal can pass the execution gate."""
    now = now or datetime.now(timezone.utc)
    catalog, blockers = _catalog_evidence(connection, now)
    errors = []
    invalid_entries = any(
        entry["zone"] not in {"mainboard", "commander"}
        or not isinstance(entry["quantity"], int)
        or isinstance(entry["quantity"], bool)
        or not 1 <= entry["quantity"] <= 100
        for entry in entries
    )
    if invalid_entries:
        errors.append("Invalid deck zone or card quantity")
    snapshot = sorted(
        (entry["card"]["card_id"], entry["zone"], entry["quantity"])
        for entry in entries
    )
    try:
        cards = _load_cards(connection, entries)
        total = sum(entry["quantity"] for entry in entries)
        if total != 100:
            errors.append(f"Expected 100 cards including commanders; found {total}")
        errors.extend(_commander_errors(entries, cards))
        errors.extend(_card_errors(entries, cards))
        for entry in entries:
            card = cards[entry["card"]["card_id"]]
            if (
                entry["zone"] == "commander"
                and "choose a color before the game begins" in _oracle(card).casefold()
            ):
                blockers.append(
                    f"Commander color choice is not implemented: {card['name']}"
                )
    except (KeyError, TypeError, ValueError) as error:
        blockers.append(f"Cannot verify deck: {error}")
    return {
        "status": "illegal" if errors else "needs_review" if blockers else "legal",
        "errors": list(dict.fromkeys(errors)),
        "blockers": list(dict.fromkeys(blockers)),
        "rules_version": RULES_VERSION,
        "rules_source": RULES_SOURCE,
        "validator_version": VALIDATOR_VERSION,
        "catalog": catalog,
        "deck_hash": hashlib.sha256(json.dumps(snapshot).encode()).hexdigest(),
        "checked_at": now.isoformat(),
        "bracket_checked": False,
    }


def require_legal_entries(connection: Connection, entries: list) -> dict:
    """Revalidate immediately before persistence, matchmaking, or simulation."""
    report = validate_entries(connection, entries)
    if report["status"] != "legal":
        reasons = report["errors"] + report["blockers"]
        raise ValueError("Commander legality gate blocked: " + "; ".join(reasons))
    return report


def require_legal_deck(connection: Connection, deck_id: int) -> dict:
    """Load saved deck contents and recheck them; a stored status is not trusted."""
    if (
        connection.execute("SELECT 1 FROM decks WHERE id = ?", (deck_id,)).fetchone()
        is None
    ):
        raise ValueError(f"Unknown deck: {deck_id}")
    entries = [
        {
            "card": {"card_id": row["card_id"]},
            "zone": row["zone"],
            "quantity": row["quantity"],
        }
        for row in connection.execute(
            "SELECT * FROM deck_cards WHERE deck_id = ?", (deck_id,)
        )
    ]
    return require_legal_entries(connection, entries)
