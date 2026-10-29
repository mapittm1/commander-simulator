"""Exact Attraction printings: source metadata is data, never native command text."""

import hashlib
import json
import re
from uuid import UUID

PRINTING_FIELDS = {
    "id",
    "oracle_id",
    "name",
    "set",
    "collector_number",
    "lang",
    "attraction_lights",
}


def split_printing_choice(name):
    """Recognize the existing Arena set/collector syntax without guessing a printing."""
    match = re.fullmatch(
        r"(.+?)\s+\(([A-Za-z0-9]{2,8})\)\s+([0-9]{1,6}[A-Za-z]?)(?:\s+\*F\*)?", name
    )
    return (match[1], (match[2].lower(), match[3].lower())) if match else (name, None)


def resolve_printing_choice(connection, card, selector):
    """Require one retained English printing of this canonical Oracle identity."""
    rows = connection.execute(
        "SELECT raw_json FROM printings WHERE card_id=? AND set_code=? COLLATE NOCASE "
        "AND collector_number=? COLLATE NOCASE AND language='en'",
        (card["card_id"], *selector),
    ).fetchall()
    if len(rows) != 1:
        raise ValueError(
            "Unknown or ambiguous Attraction printing; import its English Scryfall printing metadata"
        )
    source = json.loads(rows[0]["raw_json"])
    if not isinstance(source, dict):
        raise ValueError("Malformed retained Attraction printing metadata")
    types, legality = source.get("type_line"), source.get("legalities")
    if (
        not isinstance(types, str)
        or "Attraction" not in types.partition(" — ")[2].split()
    ):
        raise ValueError("Selected source printing is not an Attraction")
    if not isinstance(legality, dict) or legality.get("commander") != "legal":
        raise ValueError("Selected source Attraction printing is not Commander-legal")
    projection = {key: source.get(key) for key in PRINTING_FIELDS}
    require_printing_choice(projection, card["card_id"], card["name"])
    return projection


def require_printing_choice(choice, card_id, name):
    """Bound generated native selectors and bind a choice to its exact card identity."""
    if not isinstance(choice, dict) or set(choice) != PRINTING_FIELDS:
        raise ValueError("Malformed Attraction printing choice")
    for field in ("id", "oracle_id"):
        if not isinstance(choice[field], str):
            raise ValueError("Attraction printing identities must be UUID strings")
        UUID(choice[field])
    if (
        choice["oracle_id"] != card_id
        or choice["name"] != name
        or choice["lang"] != "en"
    ):
        raise ValueError(
            "Attraction printing does not match its English Oracle identity"
        )
    if not isinstance(name, str) or not 1 <= len(name) <= 300 or not name.isprintable():
        raise ValueError("Invalid Attraction printing name")
    if not isinstance(choice["set"], str) or not re.fullmatch(
        r"[a-z0-9]{2,8}", choice["set"]
    ):
        raise ValueError("Invalid Attraction printing set")
    if not isinstance(choice["collector_number"], str) or not re.fullmatch(
        r"[0-9]{1,6}[a-z]?", choice["collector_number"]
    ):
        raise ValueError("Invalid Attraction printing collector number")
    lights = choice["attraction_lights"]
    if (
        not isinstance(lights, list)
        or not 1 <= len(lights) <= 6
        or any(
            (not isinstance(light, int) or isinstance(light, bool))
            or not 1 <= light <= 6
            for light in lights
        )
        or lights != sorted(set(lights))
    ):
        raise ValueError("Attraction printing requires distinct ordered die lights")


def printing_choice_errors(connection, entries, cards):
    """Recheck selected printing facts without replacing current Oracle rules or legality."""
    errors = []
    for entry in entries:
        if "printing" not in entry:
            continue
        try:
            card = cards[entry["card"]["card_id"]]
            if (
                entry["zone"] != "attractions"
                or "Attraction"
                not in card.get("type_line", "").partition(" — ")[2].split()
            ):
                raise ValueError(
                    "Explicit printing choices are supported only in Attractions"
                )
            choice = entry["printing"]
            require_printing_choice(choice, entry["card"]["card_id"], card["name"])
            current = resolve_printing_choice(
                connection,
                {"card_id": entry["card"]["card_id"], "name": card["name"]},
                (choice["set"], choice["collector_number"]),
            )
            if current != choice:
                raise ValueError(
                    "Attraction printing metadata changed; import and review a new deck revision"
                )
        except (KeyError, TypeError, ValueError) as error:
            errors.append(str(error))
    return errors


def printing_hash_suffix(entry):
    """Keep legacy semantic deck hashes unchanged when no explicit printing was selected."""
    if "printing" not in entry:
        return ()
    raw = json.dumps(entry["printing"], sort_keys=True, separators=(",", ":"))
    return ("printing:" + hashlib.sha256(raw.encode()).hexdigest(),)
