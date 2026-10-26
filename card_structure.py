"""Starting-deck distinctions that format legality fields alone do not establish."""


def meld_role(card: dict) -> str | None:
    """Resolve a meld card's own role from its exact related-card printing identifier."""
    if card.get("layout") != "meld":
        return None
    own_id = card.get("id")
    parts = card.get("all_parts")
    if not isinstance(own_id, str) or not own_id or not isinstance(parts, list):
        return "unknown"
    roles = {
        part.get("component")
        for part in parts
        if isinstance(part, dict) and part.get("id") == own_id
    }
    if roles == {"meld_result"}:
        return "meld_result"
    if roles == {"meld_part"}:
        return "meld_part"
    return "unknown"


def starting_card_issues(cards: dict) -> tuple[list[str], list[str]]:
    """Exclude combined meld faces and block unverifiable meld roles (CR 712.4, 712.8a)."""
    errors, blockers = [], []
    for card in cards.values():
        role = meld_role(card)
        if role == "meld_result":
            errors.append(
                f"Meld result cannot be a starting card or commander: {card['name']} (CR 712.4, 712.8a)"
            )
        elif role == "unknown":
            blockers.append(f"Cannot verify meld starting-card role: {card['name']}")
    return errors, blockers
