"""Rules expectations for a paid counter-counterspell and its affordability control."""

from .priority import (
    _check_callbacks,
    _check_payment,
    _ids,
    _integer,
    _require,
    _snapshot_shape,
    callback_rows,
    evaluate_priority_states,
    validate_setup,
)

CHAIN_CASES = ("chain_counter_paid", "chain_counter_unaffordable")


def chain_evidence(case_id, states) -> dict | None:
    """Require native target, pass and intermediate-resolution evidence for each chain."""
    if case_id not in CHAIN_CASES:
        return None
    return evaluate_priority_states(states, case_id, _setup, _trace)


def _setup(state, case_id):
    validate_setup(
        state,
        (
            ("Bonesplitter", "Forest", 1),
            ("Disallow", "Island", 3),
            ("Disallow", "Island", 3 if case_id == CHAIN_CASES[0] else 2),
        ),
    )
    _targets_shape(state)
    if state["stack_targets"] != [] or state["mana_pool"] != [0] * 4:
        raise ValueError("Chain must start without pending spells or floating mana")


def _trace(before, after, case_id):
    _snapshot_shape(after)
    _targets_shape(after)
    steps, callbacks = after["steps"], after["callbacks"]
    if not isinstance(steps, list) or not isinstance(callbacks, list):
        raise ValueError("Native loop and callback lists required")
    _check_callbacks(callbacks, _callbacks(before, case_id))
    plan = _step_plan(before, case_id)
    _require(len(steps) == len(plan))
    for offset, (state, expected) in enumerate(zip(steps, plan)):
        _snapshot_shape(state)
        _targets_shape(state)
        _state(before, state, expected)
        _resources(before, state, offset, case_id)
    _require(
        {
            key: value
            for key, value in after.items()
            if key not in {"callbacks", "steps"}
        }
        == steps[-1]
    )


def _callbacks(before, case_id):
    root, counter, defense = [spell["ability_id"] for spell in before["spells"]]
    paid = case_id == CHAIN_CASES[0]
    double = [counter, root]
    sequence = [
        (0, [], root, [], True, True, True),
        (0, [root], None, [], False, False, False),
        (1, [root], counter, [root], True, True, True),
        (1, double, None, [], False, False, False),
        (2, double, defense, [counter], True, paid, paid),
    ]
    if paid:
        triple = [defense, counter, root]
        sequence.extend(
            (seat, triple, None, [], False, False, False) for seat in (2, 3, 0, 1)
        )
        sequence.extend(
            (seat, [root], None, [], False, False, False) for seat in (0, 1, 2, 3)
        )
    else:
        sequence.extend(
            (seat, double, None, [], False, False, False) for seat in (3, 0)
        )
    return callback_rows(before["player_ids"], sequence)


def _step_plan(before, case_id):
    root, counter, defense = [spell["ability_id"] for spell in before["spells"]]
    double, triple = [counter, root], [defense, counter, root]
    starting = [
        (1, [root], ("Stack", "Hand", "Hand")),
        (2, double, ("Stack", "Stack", "Hand")),
    ]
    if case_id == CHAIN_CASES[1]:
        return starting + [
            (3, double, ("Stack", "Stack", "Hand")),
            (0, double, ("Stack", "Stack", "Hand")),
            (0, [], ("Graveyard", "Graveyard", "Hand")),
        ]
    return starting + [
        (3, triple, ("Stack", "Stack", "Stack")),
        (0, triple, ("Stack", "Stack", "Stack")),
        (1, triple, ("Stack", "Stack", "Stack")),
        (0, [root], ("Stack", "Graveyard", "Graveyard")),
        (1, [root], ("Stack", "Graveyard", "Graveyard")),
        (2, [root], ("Stack", "Graveyard", "Graveyard")),
        (3, [root], ("Stack", "Graveyard", "Graveyard")),
        (0, [], ("Battlefield", "Graveyard", "Graveyard")),
    ]


def _state(before, state, expected):
    seat, stack, zones = expected
    for key in ("player_ids", "life", "phase", "active_player_id"):
        _require(state[key] == before[key])
    _require(state["priority_id"] == before["player_ids"][seat])
    _require(state["stack"] == stack and state["mana_pool"] == [0] * 4)
    _require(len(state["spells"]) == 3)
    for index, spell in enumerate(state["spells"]):
        for key in ("id", "ability_id", "name", "controller_id"):
            _require(spell[key] == before["spells"][index][key])
        _require(spell["zone"] == zones[index])
    targets = {
        spell["ability_id"]: (
            [] if index == 0 else [before["spells"][index - 1]["ability_id"]]
        )
        for index, spell in enumerate(before["spells"])
    }
    _require(
        state["stack_targets"]
        == [{"ability_id": ability, "targets": targets[ability]} for ability in stack]
    )


def _resources(before, state, offset, case_id):
    _require(len(state["lands"]) == 3)
    for index, sources in enumerate(state["lands"]):
        cast = (
            index == 0
            or (index == 1 and offset >= 1)
            or (index == 2 and offset >= 2 and case_id == CHAIN_CASES[0])
        )
        _require(len(sources) == len(before["lands"][index]))
        for source, original in zip(sources, before["lands"][index]):
            _require(source == {**original, "tapped": cast})
        _check_payment(state["spells"][index], sources, cast, index)


def _targets_shape(state):
    _ids(state["mana_pool"])
    if not isinstance(state["stack_targets"], list):
        raise ValueError("Native pending-spell target list required")
    for row in state["stack_targets"]:
        _integer(row["ability_id"])
        _ids(row["targets"])
