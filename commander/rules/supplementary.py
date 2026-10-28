"""Constructed supplementary deck selections, separate from starting cards."""

from collections import Counter


def supplementary_issues(entries: list, cards: dict) -> list[str]:
    """Require unique, correctly typed selections and current format legality."""
    errors = []
    for zone in ("stickers", "attractions"):
        selected = [entry for entry in entries if entry["zone"] == zone]
        if not selected:
            continue
        names = Counter()
        for entry in selected:
            card = cards[entry["card"]["card_id"]]
            names[card["name"]] += entry["quantity"]
            types = card.get("type_line", "")
            correct_type = (
                types == "Stickers"
                if zone == "stickers"
                else (
                    "Artifact" in types.partition(" — ")[0].split()
                    and "Attraction" in types.partition(" — ")[2].split()
                )
            )
            if not correct_type:
                errors.append(f"Wrong object type in {zone}: {card['name']}")
            if card.get("legalities", {}).get("commander") != "legal":
                errors.append(f"Not Commander-legal in {zone}: {card['name']}")
        rule = "123.2a" if zone == "stickers" else "717.2a"
        if len(names) < 10 or any(quantity != 1 for quantity in names.values()):
            errors.append(
                f"{zone.title()} require at least ten unique selections, one each (CR {rule})"
            )
    return errors
