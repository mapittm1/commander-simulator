"""Independently bind native priority callbacks, pass cycles, identities and resources."""

PRIORITY_CASES = (
    "priority_all_pass",
    "priority_counter_response",
    "priority_unaffordable_response",
)
PRIORITY_STAGES = ("before", "after_sequence")


def priority_evidence(case_id, states) -> dict | None:
    """Reject missing traces; distinguish invalid setup from witnessed rules mismatches."""
    if case_id not in PRIORITY_CASES:
        return None
    return evaluate_priority_states(states, case_id, _setup, _trace)


def evaluate_priority_states(states, case_id, setup, trace):
    """Classify incomplete setup separately from contradictory observed sequences."""
    try:
        before, after = (states[stage] for stage in PRIORITY_STAGES)
        setup(before, case_id)
    except (KeyError, TypeError, ValueError, IndexError):
        return {"status": "not_exercised", "reason": "Invalid priority fixture setup"}
    try:
        trace(before, after, case_id)
    except (KeyError, TypeError, ValueError, IndexError):
        return {
            "status": "not_exercised",
            "reason": "Missing or malformed native priority evidence",
        }
    except AssertionError:
        return {
            "stage": "after_sequence",
            "field": "native_priority_sequence",
            "expected": "Paid native casts, retained priority, consecutive passes and bound resolution",
            "observed": after,
        }
    return None


def _integer(value):
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise ValueError("Nonnegative native identity required")
    return value


def _setup(state, case_id):
    validate_setup(
        state,
        (
            ("Bonesplitter", "Forest", 1),
            ("Disallow", "Island", 2 if case_id == PRIORITY_CASES[2] else 3),
        ),
    )


def validate_setup(state, specifications):
    """Bind distinct proposed spells and native mana sources to their controlled seats."""
    _snapshot_shape(state)
    players = state["player_ids"]
    if len(players) != 4 or len(set(players)) != 4:
        raise ValueError("Four distinct players required")
    for player in players:
        _integer(player)
    if (
        state["priority_id"] != players[0]
        or state["active_player_id"] != players[0]
        or state["phase"] != "MAIN1"
        or state["stack"] != []
        or state["life"] != [40] * 4
    ):
        raise ValueError("Invalid initial priority state")
    spells = state["spells"]
    if len(spells) != len(specifications) or len(state["lands"]) != len(specifications):
        raise ValueError("Every fixed spell proposal and source group is required")
    identities = []
    for index, (name, land_name, count) in enumerate(specifications):
        spell = spells[index]
        identities.append(_integer(spell["id"]))
        _integer(spell["ability_id"])
        if (
            spell["name"] != name
            or spell["zone"] != "Hand"
            or spell["controller_id"] != players[index]
            or spell["payment"] != []
        ):
            raise ValueError("Invalid initial spell")
        sources = state["lands"][index]
        if len(sources) != count:
            raise ValueError("Invalid initial resources")
        for source in sources:
            identities.append(_integer(source["id"]))
            _integer(source["timestamp"])
            if (
                source["name"] != land_name
                or source["zone"] != "Battlefield"
                or source["tapped"] is not False
                or source["controller_id"] != players[index]
            ):
                raise ValueError("Invalid initial mana source")
    if len(set(identities)) != len(identities) or len(
        {spell["ability_id"] for spell in spells}
    ) != len(spells):
        raise ValueError("Duplicated object identity")


def _trace(before, after, case_id):
    players = before["player_ids"]
    root, counter = [spell["ability_id"] for spell in before["spells"]]
    responds = case_id == PRIORITY_CASES[1]
    _snapshot_shape(after)
    steps = after["steps"]
    if not isinstance(after["callbacks"], list) or not isinstance(steps, list):
        raise ValueError("Raw callback and native step lists required")
    _check_callbacks(after["callbacks"], _expected_callbacks(before, case_id))
    pending = [counter, root] if responds else [root]
    stacks = (
        [[root], pending, pending, pending, []] if responds else [[root]] * 3 + [[]]
    )
    seats = [1, 2, 3, 0, 0] if responds else [1, 2, 3, 0]
    _require(len(steps) == len(stacks))
    for index, (state, stack, seat) in enumerate(zip(steps, stacks, seats)):
        _snapshot_shape(state)
        _require(state["stack"] == stack and state["priority_id"] == players[seat])
        _state(before, state, responds, index == len(steps) - 1, index > 0)
    _require(
        {
            key: value
            for key, value in after.items()
            if key not in {"steps", "callbacks"}
        }
        == steps[-1]
    )


def _state(before, state, responds, resolved, response_offered):
    for key in ("player_ids", "life", "phase", "active_player_id"):
        _require(state[key] == before[key])
    if len(state["spells"]) != 2 or len(state["lands"]) != 2:
        raise ValueError("Tracked spell/resource evidence required")
    for index, spell in enumerate(state["spells"]):
        for key in ("id", "ability_id", "name", "controller_id"):
            _require(spell[key] == before["spells"][index][key])
        cast = index == 0 or (responds and response_offered)
        expected_zone = "Stack" if cast else "Hand"
        if resolved and cast:
            expected_zone = "Graveyard" if responds else "Battlefield"
        _require(spell["zone"] == expected_zone)
        sources = state["lands"][index]
        _require(len(sources) == len(before["lands"][index]))
        for source, initial in zip(sources, before["lands"][index]):
            _require(source == {**initial, "tapped": cast})
        _check_payment(spell, sources, cast, index)


def _require(condition):
    if not condition:
        raise AssertionError("Native priority evidence disagrees with the rules")


def _ids(values):
    if not isinstance(values, list):
        raise ValueError("Native identity lists required")
    for value in values:
        _integer(value)


def _snapshot_shape(state):
    for key in ("player_ids", "life", "stack"):
        _ids(state[key])
    for key in ("priority_id", "active_player_id"):
        _integer(state[key])
    if not isinstance(state["spells"], list) or not isinstance(state["lands"], list):
        raise ValueError("Native spell and resource lists required")
    for spell in state["spells"]:
        for key in ("id", "ability_id", "controller_id"):
            _integer(spell[key])
        if not isinstance(spell["payment"], list):
            raise ValueError("Native mana payment list required")
        for mana in spell["payment"]:
            for key in ("source_id", "source_timestamp", "color"):
                _integer(mana[key])
    _resources_shape(state["lands"])


def _expected_callbacks(before, case_id):
    players = before["player_ids"]
    root, counter = [spell["ability_id"] for spell in before["spells"]]
    responds = case_id == PRIORITY_CASES[1]
    expected = [
        (0, [], root, [], True, True, True),
        (0, [root], None, [], False, False, False),
        (1, [root], counter, [root], True, case_id != PRIORITY_CASES[2], responds),
    ]
    pending = [counter, root] if responds else [root]
    if responds:
        expected.append((1, pending, None, [], False, False, False))
    expected.extend((seat, pending, None, [], False, False, False) for seat in (2, 3))
    if responds:
        expected.append((0, pending, None, [], False, False, False))
    return callback_rows(players, expected)


def _check_callbacks(callbacks, expected):
    _require(len(callbacks) == len(expected))
    for row, expected_row in zip(callbacks, expected):
        for key in ("can_play", "can_pay", "cast"):
            if not isinstance(row[key], bool):
                raise ValueError("Boolean callback evidence required")
        _ids(row["stack"])
        _ids(row["targets"])
        if row["proposal_id"] is not None:
            _integer(row["proposal_id"])
        for key in ("player_id", "priority_id"):
            _integer(row[key])
        _require(row == expected_row)


def _check_payment(spell, sources, cast, index):
    payment = spell["payment"]
    _require(len(payment) == (len(sources) if cast else 0))
    _require(len({mana["source_id"] for mana in payment}) == len(payment))
    for mana in payment:
        matching = [source for source in sources if source["id"] == mana["source_id"]]
        _require(len(matching) == 1)
        _require(mana["source_timestamp"] == matching[0]["timestamp"])
        _require(mana["symbol"] == ("G" if index == 0 else "U"))
        _require(mana["color"] == (16 if index == 0 else 2))


def _resources_shape(lands):
    for sources in lands:
        if not isinstance(sources, list):
            raise ValueError("Native resource lists required")
        for source in sources:
            for key in ("id", "timestamp", "controller_id"):
                _integer(source[key])
            if not isinstance(source["tapped"], bool):
                raise ValueError("Boolean land tapping evidence required")


def callback_rows(players, sequence):
    """Serialize fixed expected choices without sharing their rules or ordering."""
    return [
        {
            "player_id": players[seat],
            "priority_id": players[seat],
            "stack": stack,
            "proposal_id": proposal,
            "targets": targets,
            "can_play": can_play,
            "can_pay": can_pay,
            "cast": cast,
        }
        for seat, stack, proposal, targets, can_play, can_pay, cast in sequence
    ]
