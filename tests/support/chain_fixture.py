"""Hand-authored chain receipts for verifier tests, never native engine evidence."""

from copy import deepcopy

from tests.support.priority_fixture import (
    add_priority_states,
    synthetic_callbacks,
    synthetic_payment,
)


def add_chain_states(states, case_id):
    """Build two explicit examples without consulting production trace expectations."""
    add_priority_states(states, "priority_counter_response")
    before = states["before"]
    before["spells"].append(
        {
            "id": 12,
            "ability_id": 22,
            "name": "Disallow",
            "controller_id": 2,
            "zone": "Hand",
            "payment": [],
        }
    )
    before["lands"].append(
        [
            {
                "id": identity,
                "timestamp": 1,
                "controller_id": 2,
                "name": "Island",
                "zone": "Battlefield",
                "tapped": False,
            }
            for identity in range(40, 43 if case_id == "chain_counter_paid" else 42)
        ]
    )
    before.update(mana_pool=[0] * 4, stack_targets=[])
    steps = _steps(before, case_id)
    states["after_sequence"] = deepcopy(steps[-1])
    states["after_sequence"].update(steps=steps, callbacks=_callbacks(case_id))


def _steps(before, case_id):
    plan = [
        (1, [20], "SHH"),
        (2, [21, 20], "SSH"),
    ]
    if case_id == "chain_counter_paid":
        plan.extend(
            [
                (3, [22, 21, 20], "SSS"),
                (0, [22, 21, 20], "SSS"),
                (1, [22, 21, 20], "SSS"),
                (0, [20], "SGG"),
                (1, [20], "SGG"),
                (2, [20], "SGG"),
                (3, [20], "SGG"),
                (0, [], "BGG"),
            ]
        )
    else:
        plan.extend(
            [
                (3, [21, 20], "SSH"),
                (0, [21, 20], "SSH"),
                (0, [], "GGH"),
            ]
        )
    return [_step(before, seat, pending, zones) for seat, pending, zones in plan]


def _step(before, seat, pending, zones):
    state = deepcopy(before)
    state.update(
        priority_id=seat,
        stack=pending,
        stack_targets=[
            {"ability_id": identity, "targets": {20: [], 21: [20], 22: [21]}[identity]}
            for identity in pending
        ],
    )
    for index, symbol in enumerate(zones):
        spell = state["spells"][index]
        spell["zone"] = {
            "S": "Stack",
            "H": "Hand",
            "G": "Graveyard",
            "B": "Battlefield",
        }[symbol]
        if symbol != "H":
            synthetic_payment(state, index)
    return state


def _callbacks(case_id):
    paid = case_id == "chain_counter_paid"
    sequence = [
        (0, [], 20, [], True, True, True),
        (0, [20], None, [], False, False, False),
        (1, [20], 21, [20], True, True, True),
        (1, [21, 20], None, [], False, False, False),
        (2, [21, 20], 22, [21], True, paid, paid),
    ]
    if paid:
        sequence.extend(
            [
                (2, [22, 21, 20], None, [], False, False, False),
                (3, [22, 21, 20], None, [], False, False, False),
                (0, [22, 21, 20], None, [], False, False, False),
                (1, [22, 21, 20], None, [], False, False, False),
                (0, [20], None, [], False, False, False),
                (1, [20], None, [], False, False, False),
                (2, [20], None, [], False, False, False),
                (3, [20], None, [], False, False, False),
            ]
        )
    else:
        sequence.extend(
            [
                (3, [21, 20], None, [], False, False, False),
                (0, [21, 20], None, [], False, False, False),
            ]
        )
    return synthetic_callbacks(sequence)
