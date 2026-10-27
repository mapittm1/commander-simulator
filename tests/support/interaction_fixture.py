"""Synthetic verifier input; never evidence of native game behavior."""

import copy

from commander.engine.interactions.casting import CAST_CASES
from commander.engine.interactions.chains import CHAIN_CASES
from commander.engine.interactions.ordering import EFFECT_NAMES, ORDER_CASES
from commander.engine.interactions.priority import PRIORITY_CASES
from tests.support.casting_fixture import add_casting_states
from tests.support.chain_fixture import add_chain_states
from tests.support.priority_fixture import add_priority_states


def synthetic_receipt(suite: dict) -> dict:
    """Build verifier input only; this is not a native-engine conformance result."""
    cases = []
    for index, case in enumerate(suite["cases"]):
        states = copy.deepcopy(case["expected"])
        if case["id"] in CHAIN_CASES:
            add_chain_states(states, case["id"])
            cases.append({"id": case["id"], "states": states})
            continue
        if case["id"] in PRIORITY_CASES:
            add_priority_states(states, case["id"])
            cases.append({"id": case["id"], "states": states})
            continue
        if case["id"] in CAST_CASES:
            add_casting_states(states, case["id"], index)
            cases.append({"id": case["id"], "states": states})
            continue
        if case["id"] in ORDER_CASES:
            _ordering_states(states, case["id"], index)
        initial, reflexive, counter = 100 + index * 3, 101 + index * 3, 102 + index * 3
        for stage, state in states.items():
            pending = (
                initial if stage in {"after_damage", "before_first"} else reflexive
            )
            if state["counter_on_stack"]:
                state["stack_ability_ids"] = [counter, pending]
                state["spell_target_ids"] = [pending]
            else:
                state["stack_ability_ids"] = [pending] if state["stack"] else []
                state["spell_target_ids"] = []
            state["equipment_id"] = 1000 + index
            state["equipment_timestamp"] = 10
            state["targets"] = [1000 + index] if state.get("equipment_targeted") else []
            state["target_timestamps"] = [10] if state.get("equipment_targeted") else []
            state["token_ids"] = [2000 + index] if state["tokens"] else []
            state["token_timestamps"] = [20] if state["tokens"] else []
            state["equipment_attached_to_id"] = (
                2000 + index if state["attached"] else None
            )
        if case["id"] == "protection_detaches_equipment":
            states["after_detachment"]["response_spell"] = {
                "targets": [2000 + index],
                "target_timestamps": [20],
                "stack_ability_ids": [counter],
                "zone": "Graveyard",
            }
        if case["id"] in {
            "assassin_protection_artifacts",
            "assassin_hexproof",
            "assassin_shroud",
        }:
            states["after_response"]["response_spell"] = {
                "targets": [2000 + index],
                "target_timestamps": [20],
                "stack_ability_ids": [counter, reflexive],
                "zone": (
                    "Battlefield" if case["id"] == "assassin_shroud" else "Graveyard"
                ),
            }
        if case["id"] == "target_zone_roundtrip":
            for stage in ("after_response", "after_second"):
                states[stage]["equipment_timestamp"] = 12
            states["after_response"]["equipment_transitions"] = [
                {"id": 1000 + index, "timestamp": 11, "zone": "Exile"},
                {"id": 1000 + index, "timestamp": 12, "zone": "Graveyard"},
            ]
        cases.append({"id": case["id"], "states": states})
    return {"cases": cases}


def _ordering_states(states, case_id, index):
    effects = {
        role: {
            "effect_id": 10000 + index * 2 + offset,
            "card_id": 3000 + index * 2 + offset,
            "timestamp": 10,
            "controller_id": offset,
            "name": name,
            "zone": "Battlefield",
        }
        for offset, (role, name) in enumerate(EFFECT_NAMES.items())
    }
    choices = []
    for offset, role in enumerate(ORDER_CASES[case_id]):
        choices.append(
            {
                "decider_id": 1,
                "chosen_id": effects[role]["effect_id"],
                "candidates": [
                    {
                        key: value
                        for key, value in effects[candidate].items()
                        if key != "zone"
                    }
                    for candidate in ORDER_CASES[case_id][offset:]
                ],
            }
        )
    for stage, state in states.items():
        state["ordering"] = {
            "actor_id": 0,
            "affected_player_id": 1,
            "effects": copy.deepcopy(effects),
            "choices": [] if stage == "before" else copy.deepcopy(choices),
        }
