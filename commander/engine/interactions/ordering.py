"""Independent replacement-order contracts and raw native choice verification."""

ORDER_CASES = {
    "replacement_double_then_prevent": ("doubler", "damage_reducer"),
    "replacement_prevent_then_double": ("damage_reducer", "doubler"),
    "noncombat_replacement_double_then_prevent": ("doubler", "damage_reducer"),
    "noncombat_replacement_prevent_then_double": ("damage_reducer", "doubler"),
}
EFFECT_NAMES = {"doubler": "Furnace of Rath", "damage_reducer": "Urza's Armor"}
EFFECT_FIELDS = ("effect_id", "card_id", "timestamp", "controller_id")


def ordering_evidence(case_id: str, states: dict) -> dict | None:
    """Require two real callback choices, bound to the correct affected player and effects."""
    if case_id not in ORDER_CASES:
        return None
    try:
        before = states["before"]["ordering"]
        actor, affected = _number(before["actor_id"]), _number(
            before["affected_player_id"]
        )
        effects = _effects(before["effects"])
        setup = (
            actor != affected
            and before["choices"] == []
            and effects["doubler"]["controller_id"] == actor
            and effects["damage_reducer"]["controller_id"] == affected
            and len({effect["effect_id"] for effect in effects.values()}) == 2
            and len({effect["card_id"] for effect in effects.values()}) == 2
        )
        if not setup:
            return _mismatch("before", "replacement_setup", before)
        for stage, state in states.items():
            if stage == "before":
                continue
            current = state["ordering"]
            if (
                _number(current["actor_id"]) != actor
                or _number(current["affected_player_id"]) != affected
                or _effects(current["effects"]) != effects
                or not _choices_match(
                    current["choices"], effects, ORDER_CASES[case_id], affected
                )
            ):
                return _mismatch(stage, "replacement_choice_binding", current)
    except (KeyError, TypeError, ValueError):
        return {
            "status": "not_exercised",
            "reason": "Missing or malformed native replacement choices",
        }
    return None


def _effects(value) -> dict:
    if not isinstance(value, dict) or set(value) != set(EFFECT_NAMES):
        raise ValueError("Exactly the reviewed replacement pair is required")
    result = {}
    for role, name in EFFECT_NAMES.items():
        effect = value[role]
        result[role] = _candidate(effect)
        if effect["name"] != name or effect["zone"] != "Battlefield":
            raise ValueError("Replacement permanent setup is absent")
    return result


def _candidate(value) -> dict:
    if not isinstance(value, dict) or not isinstance(value["name"], str):
        raise ValueError("Native effect identity is required")
    return {
        "name": value["name"],
        **{key: _number(value[key]) for key in EFFECT_FIELDS},
    }


def _choices_match(choices, effects, order, affected) -> bool:
    if not isinstance(choices, list) or len(choices) != 2:
        raise ValueError("Two native replacement callbacks are required")
    for index, row in enumerate(choices):
        if not isinstance(row, dict) or not isinstance(row["candidates"], list):
            raise ValueError("Native replacement candidate list is required")
        candidates = [_candidate(value) for value in row["candidates"]]
        required = [effects[role] for role in order[index:]]
        if (
            _number(row["decider_id"]) != affected
            or _number(row["chosen_id"]) != effects[order[index]]["effect_id"]
            or sorted(candidates, key=lambda value: value["effect_id"])
            != sorted(required, key=lambda value: value["effect_id"])
        ):
            return False
    return True


def _number(value) -> int:
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise ValueError("Native identity requires a nonnegative integer")
    return value


def _mismatch(stage, field, observed) -> dict:
    return {
        "stage": stage,
        "field": field,
        "expected": "Recorded native candidates, affected player, and selected order agree",
        "observed": observed,
    }
