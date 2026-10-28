"""Starting-deck distinctions that format legality fields alone do not establish."""

from commander.rules.meld_part_reviews import meld_part_review
from commander.rules.meld_reviews import reviewed_meld_result


def meld_role(card: dict) -> str | None:
    """Resolve exact self-reference first, then only a reviewed result exclusion."""
    role = _direct_meld_role(card)
    if role != "unknown":
        return role
    if meld_part_review(card) is not None:
        return "meld_part"
    return reviewed_meld_result(card)


def _direct_meld_role(card: dict) -> str | None:
    """Resolve a meld card's own role from its exact related-card printing identifier."""
    if card.get("layout") != "meld":
        return None
    own_id = card.get("id")
    parts = card.get("all_parts")
    if not isinstance(own_id, str) or not own_id or not isinstance(parts, list):
        return "unknown"
    roles = [
        part.get("component")
        for part in parts
        if isinstance(part, dict) and part.get("id") == own_id
    ]
    if not all(isinstance(role, str) for role in roles):
        return "unknown"
    if set(roles) == {"meld_result"}:
        return "meld_result"
    if set(roles) == {"meld_part"}:
        return "meld_part"
    return "unknown"


def starting_card_issues(cards: dict) -> tuple[list[str], list[str]]:
    """Exclude supplementary objects and meld results; block unverifiable meld roles."""
    errors, blockers = [], []
    for card in cards.values():
        if card.get("type_line") == "Stickers":
            errors.append(
                f"Sticker sheet cannot occupy a starting-deck slot: {card['name']} (CR 123)"
            )
        if "Attraction" in card.get("type_line", "").partition(" — ")[2].split():
            errors.append(
                f"Attraction cannot occupy a starting-deck slot: {card['name']} (CR 717.2)"
            )
        role = meld_role(card)
        if role == "meld_result":
            errors.append(
                f"Meld result cannot be a starting card or commander: {card['name']} (CR 712.4, 712.8a)"
            )
        elif role == "unknown":
            blockers.append(f"Cannot verify meld starting-card role: {card['name']}")
    return errors, blockers
