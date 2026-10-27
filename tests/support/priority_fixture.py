"""Synthetic priority traces for verifier tests; never native conformance evidence."""

from copy import deepcopy


def add_priority_states(states, case_id):
    """Construct explicit four-player spell/pass examples with distinct raw identities."""
    before = states["before"]
    before.update(
        player_ids=[0, 1, 2, 3],
        priority_id=0,
        active_player_id=0,
        spells=[
            {
                "id": 10,
                "ability_id": 20,
                "name": "Bonesplitter",
                "controller_id": 0,
                "zone": "Hand",
                "payment": [],
            },
            {
                "id": 11,
                "ability_id": 21,
                "name": "Disallow",
                "controller_id": 1,
                "zone": "Hand",
                "payment": [],
            },
        ],
        lands=[
            [
                {
                    "id": 30,
                    "timestamp": 1,
                    "controller_id": 0,
                    "name": "Forest",
                    "zone": "Battlefield",
                    "tapped": False,
                }
            ],
            [
                {
                    "id": identity,
                    "timestamp": 1,
                    "controller_id": 1,
                    "name": "Island",
                    "zone": "Battlefield",
                    "tapped": False,
                }
                for identity in range(
                    31, 33 if case_id == "priority_unaffordable_response" else 34
                )
            ],
        ],
    )
    response = case_id == "priority_counter_response"
    sequence = [
        (0, [], 20, [], True, True, True),
        (0, [20], None, [], False, False, False),
        (
            1,
            [20],
            21,
            [20],
            True,
            case_id != "priority_unaffordable_response",
            response,
        ),
    ]
    if response:
        sequence.extend(
            [
                (1, [21, 20], None, [], False, False, False),
                (2, [21, 20], None, [], False, False, False),
                (3, [21, 20], None, [], False, False, False),
                (0, [21, 20], None, [], False, False, False),
            ]
        )
        stacks, seats = [[20], [21, 20], [21, 20], [21, 20], []], [1, 2, 3, 0, 0]
    else:
        sequence.extend(
            [
                (2, [20], None, [], False, False, False),
                (3, [20], None, [], False, False, False),
            ]
        )
        stacks, seats = [[20], [20], [20], []], [1, 2, 3, 0]
    steps = _steps(before, stacks, seats, response)
    states["after_sequence"] = deepcopy(steps[-1])
    states["after_sequence"].update(
        steps=steps,
        callbacks=[
            dict(
                zip(
                    (
                        "player_id",
                        "stack",
                        "proposal_id",
                        "targets",
                        "can_play",
                        "can_pay",
                        "cast",
                    ),
                    row,
                ),
                priority_id=row[0],
            )
            for row in sequence
        ],
    )


def _steps(before, stacks, seats, response):
    """Represent native loop boundaries independently from callback serialization."""
    steps = []
    for offset, (stack, seat) in enumerate(zip(stacks, seats)):
        state = deepcopy(before)
        state.update(stack=stack, priority_id=seat)
        for index, spell in enumerate(state["spells"]):
            cast = index == 0 or (response and offset > 0)
            if cast:
                spell["zone"] = "Stack"
                if offset == len(stacks) - 1:
                    spell["zone"] = "Graveyard" if response else "Battlefield"
                for source in state["lands"][index]:
                    source["tapped"] = True
                    spell["payment"].append(
                        {
                            "source_id": source["id"],
                            "source_timestamp": 1,
                            "symbol": "G" if index == 0 else "U",
                            "color": 16 if index == 0 else 2,
                        }
                    )
        steps.append(state)
    return steps
