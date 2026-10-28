"""Explicit companion construction checks, with bounded native condition evidence."""

from commander.engine.companion_admission import check_companion_condition


def companion_issues(entries, cards, allow_native):
    """A selected companion is outside 100, but subject to Commander admission rules."""
    selected = [entry for entry in entries if entry["zone"] == "companion"]
    if not selected:
        return {"errors": [], "blockers": [], "evidence": None}
    errors = _selection_errors(entries, cards, selected)
    report = {"errors": errors, "blockers": [], "evidence": None}
    if errors or not allow_native:
        return report
    starting = [
        entry for entry in entries if entry["zone"] in {"mainboard", "commander"}
    ]
    try:
        evidence = check_companion_condition(starting, selected[0], cards)
        report["evidence"] = evidence
        if not evidence["eligible"]:
            errors.append(
                "The starting deck, including the commander, does not satisfy the companion condition (CR 702.139)"
            )
    except (OSError, ValueError, RuntimeError) as error:
        report["blockers"].append(
            "Cannot verify companion condition: " + str(error)[:500]
        )
    return report


def _selection_errors(entries, cards, selected):
    if len(selected) != 1 or selected[0]["quantity"] != 1:
        return ["Select exactly one companion, one copy (CR 103.2b)"]
    companion = cards[selected[0]["card"]["card_id"]]
    errors = []
    if companion.get("legalities", {}).get("commander") != "legal":
        errors.append("Not Commander-legal as companion: " + companion["name"])
    if not any(
        line.startswith("Companion — ")
        for line in companion.get("oracle_text", "").splitlines()
    ):
        errors.append(
            "Selected card does not have a companion ability: " + companion["name"]
        )
    identity = set()
    for entry in entries:
        card = cards[entry["card"]["card_id"]]
        if entry["zone"] == "commander":
            identity.update(card.get("color_identity", []))
        if (
            entry["zone"] in {"mainboard", "commander"}
            and card["name"] == companion["name"]
        ):
            errors.append(
                "Companion duplicates a starting card (CR 903.11a): "
                + companion["name"]
            )
    if not set(companion.get("color_identity", [])).issubset(identity):
        errors.append(
            "Companion outside commander color identity (CR 903.11a): "
            + companion["name"]
        )
    return errors
