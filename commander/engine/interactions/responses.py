"""Fixed response cards and independent identity checks for their native resolution."""

RESPONSE_CARDS = {
    "assassin_protection_artifacts": "artifact_protection",
    "assassin_hexproof": "hexproof",
    "assassin_shroud": "shroud",
}


def response_evidence(case_id: str, states: dict) -> dict | None:
    """Bind a resolved response spell to the original token and pending reflexive trigger."""
    if case_id not in RESPONSE_CARDS:
        return None
    first, response = states["after_first"], states["after_response"]
    try:
        spell = response["response_spell"]
        original = (_single(first["token_ids"]), _single(first["token_timestamps"]))
        selected = (_single(spell["targets"]), _single(spell["target_timestamps"]))
        pending = _single(first["stack_ability_ids"])
        stack = spell["stack_ability_ids"]
        if not isinstance(stack, list) or len(stack) != 2:
            raise ValueError(
                "Two stack entries are required before response resolution"
            )
        spell_id, trigger_id = (_integer(identity) for identity in stack)
        current = (
            _single(response["token_ids"]),
            _single(response["token_timestamps"]),
        )
        zone = spell["zone"]
        if not isinstance(zone, str):
            raise ValueError("Response spell zone is required")
        expected_zone = "Battlefield" if case_id == "assassin_shroud" else "Graveyard"
        valid = (
            selected == original == current
            and selected[0] != first["equipment_id"]
            and spell_id != trigger_id
            and trigger_id == pending == _single(response["stack_ability_ids"])
            and zone == expected_zone
        )
    except (KeyError, TypeError, ValueError):
        return {
            "status": "not_exercised",
            "reason": "Missing or malformed response spell/target evidence",
        }
    if valid:
        return None
    return {
        "stage": "after_response",
        "field": "response_spell_identity",
        "expected": "Response resolves on the same token above the pending reflexive trigger",
        "observed": spell,
    }


def _integer(value) -> int:
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise ValueError("Native identity must be a nonnegative integer")
    return value


def _single(values) -> int:
    if not isinstance(values, list) or len(values) != 1:
        raise ValueError("Exactly one native identity is required")
    return _integer(values[0])
