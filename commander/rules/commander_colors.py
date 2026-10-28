"""Explicit pregame commander colors used for construction and native export."""

import re

COLORS = frozenset("WUBRG")


def split_color_choice(name: str) -> tuple[str, str | None]:
    """Accept one bounded color annotation, never arbitrary native deck flags."""
    match = re.fullmatch(r"(.+?)\s+\[color=([^\]]*)\]", name, re.IGNORECASE)
    if match is None:
        return name, None
    choice = match[2].upper()
    if choice not in COLORS:
        raise ValueError("Commander color must be exactly one of W, U, B, R or G")
    return match[1].strip(), choice


def chooses_commander_color(card: dict) -> bool:
    """Recognize the exact current Oracle permission, not an arbitrary color phrase."""
    face = (card.get("card_faces") or [card])[0]
    name = re.escape(face["name"])
    return bool(
        re.search(
            rf"^If {name} is your commander, choose a color before the game begins\. "
            rf"{name} is the chosen color\.$",
            face.get("oracle_text", ""),
            re.MULTILINE,
        )
    )


def color_choice_issues(entries: list, cards: dict) -> tuple[list[str], list[str]]:
    """Missing choices require review; invalid/unauthorized choices are errors."""
    errors, blockers = [], []
    for entry in entries:
        card = cards[entry["card"]["card_id"]]
        permitted = entry["zone"] == "commander" and chooses_commander_color(card)
        if "commander_color" in entry:
            color = entry["commander_color"]
            if not permitted or not isinstance(color, str) or color not in COLORS:
                errors.append(
                    "Invalid or unauthorized commander color choice: " + card["name"]
                )
        elif permitted:
            blockers.append(
                "Select a commander color with [color=W/U/B/R/G]: " + card["name"]
            )
    return errors, blockers


def commander_identity(entries: list, cards: dict) -> set[str]:
    """Pending choices defer color errors but never allow a deck past the review gate."""
    identity = set()
    for entry in entries:
        if entry["zone"] != "commander":
            continue
        card = cards[entry["card"]["card_id"]]
        identity.update(card.get("color_identity", []))
        if chooses_commander_color(card):
            color = entry.get("commander_color")
            identity.update(
                [color] if isinstance(color, str) and color in COLORS else COLORS
            )
    return identity
