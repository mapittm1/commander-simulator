"""Fixed paid-casting cases and independent admission, payment, and identity checks."""

CAST_CASES = (
    "cast_instant_paid",
    "cast_instant_unaffordable",
    "cast_instant_wrong_color",
    "cast_permanent_paid",
    "cast_permanent_combat_timing",
    "cast_instant_without_priority",
    "cast_permanent_nonempty_stack",
    "cast_instant_combat_paid",
)
CAST_STAGES = ("before", "after_attempt", "after_resolution")
ACCEPTED_CASTS = {
    "cast_instant_paid",
    "cast_permanent_paid",
    "cast_instant_combat_paid",
}


def casting_evidence(case_id: str, states: dict) -> dict | None:
    """Bind paid mana and stack entries to the proposed spell and original resources."""
    if case_id not in CAST_CASES:
        return None
    try:
        before = states["before"]
        for stage, state in states.items():
            _validate_raw(state)
            if not _summaries_match(state):
                return _mismatch(stage, state)
        if before["has_priority"] != (
            before["actor_id"] == before["priority_id"]
        ) or not _setup_matches(case_id, before):
            return _mismatch("before", before)
        for stage in CAST_STAGES[1:]:
            state = states[stage]
            valid = _same_proposal(before, state) and (
                _paid_cast(before, state, stage)
                if case_id in ACCEPTED_CASTS
                else _rejected_cast(before, state)
            )
            if not valid:
                return _mismatch(stage, state)
    except (KeyError, TypeError, ValueError):
        return {
            "status": "not_exercised",
            "reason": "Missing or malformed casting/payment identities",
        }
    return None


def _validate_raw(state) -> None:
    for field in (
        "actor_id",
        "priority_id",
        "active_player_id",
        "spell_id",
        "spell_timestamp",
        "ability_id",
        "target_id",
        "target_timestamp",
        "mana_pool",
    ):
        _integer(state[field])
    for field in (
        "stack_ability_ids",
        "stack_card_ids",
        "prepared_target_ids",
        "stack_target_ids",
    ):
        if not isinstance(state[field], list):
            raise ValueError("Native identity list is required")
        for value in state[field]:
            _integer(value)
    if not isinstance(state["sources"], list) or not isinstance(state["payment"], list):
        raise ValueError("Native mana sources and payment receipt are required")
    for source in state["sources"]:
        for field in ("id", "timestamp", "controller_id"):
            _integer(source[field])
        if not isinstance(source["tapped"], bool) or not isinstance(
            source["name"], str
        ):
            raise ValueError("Mana source state is required")
    for mana in state["payment"]:
        for field in ("source_id", "source_timestamp", "color"):
            _integer(mana[field])
        if not isinstance(mana["symbol"], str):
            raise ValueError("Paid mana symbol is required")


def _same_proposal(before, after) -> bool:
    stable = (
        "actor_id",
        "priority_id",
        "active_player_id",
        "phase",
        "spell_id",
        "ability_id",
        "target_id",
        "target_timestamp",
        "prepared_target_ids",
        "native_can_play",
        "native_can_pay",
        "has_priority",
    )
    return all(after[field] == before[field] for field in stable)


def _summaries_match(state) -> bool:
    return (
        len(state["stack_ability_ids"])
        == len(state["stack_card_ids"])
        == state["stack"]
        and len(state["sources"]) == state["source_count"]
        and sum(source["tapped"] for source in state["sources"])
        == state["tapped_sources"]
        and len(state["payment"]) == state["mana_paid"]
    )


def _setup_matches(case_id, state) -> bool:
    name = "Plains" if case_id == "cast_instant_wrong_color" else "Forest"
    identities = [source["id"] for source in state["sources"]]
    targets = [] if "permanent" in case_id else [state["target_id"]]
    return (
        state["active_player_id"] == state["actor_id"]
        and state["spell_id"] != state["target_id"]
        and len(identities) == len(set(identities))
        and all(
            source["name"] == name
            and source["zone"] == "Battlefield"
            and source["controller_id"] == state["actor_id"]
            and source["id"] not in {state["spell_id"], state["target_id"]}
            for source in state["sources"]
        )
        and state["prepared_target_ids"] == targets
    )


def _paid_cast(before, after, stage) -> bool:
    admitted = (
        before["has_priority"]
        and before["native_can_play"]
        and before["native_can_pay"]
    )
    if (
        not admitted
        or not after["cast_api_called"]
        or not after["cast_succeeded"]
        or len(after["payment"]) != 1
    ):
        return False
    mana = after["payment"][0]
    sources = {source["id"]: source for source in before["sources"]}
    if len(sources) != len(before["sources"]) or mana["source_id"] not in sources:
        return False
    source = sources[mana["source_id"]]
    if not _valid_payment_source(source, mana, before["actor_id"]):
        return False
    expected_sources = [{**source, "tapped": True} for source in before["sources"]]
    if after["sources"] != expected_sources:
        return False
    if stage == "after_attempt":
        return (
            after["stack_ability_ids"] == [before["ability_id"]]
            and after["stack_card_ids"] == [before["spell_id"]]
            and after["stack_target_ids"] == before["prepared_target_ids"]
        )
    return after["stack_ability_ids"] == after["stack_card_ids"] == []


def _valid_payment_source(source, mana, actor) -> bool:
    return (
        not source["tapped"]
        and source["controller_id"] == actor
        and source["name"] == "Forest"
        and mana["source_timestamp"] == source["timestamp"]
        and mana["color"] == 16
        and mana["symbol"] == "G"
    )


def _rejected_cast(before, after) -> bool:
    unchanged = (
        "spell_zone",
        "spell_timestamp",
        "sources",
        "stack_ability_ids",
        "stack_card_ids",
        "stack_target_ids",
        "mana_pool",
        "payment",
        "target_power",
        "target_toughness",
    )
    return (
        not after["cast_succeeded"]
        and after["cast_api_called"]
        == (before["has_priority"] and before["native_can_play"])
        and all(after[field] == before[field] for field in unchanged)
    )


def _integer(value) -> int:
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise ValueError("Native casting identity must be a nonnegative integer")
    return value


def _mismatch(stage, observed) -> dict:
    return {
        "stage": stage,
        "field": "casting_identity_and_payment",
        "expected": "Native proposal, priority, payment sources, and stack identities agree",
        "observed": observed,
    }
