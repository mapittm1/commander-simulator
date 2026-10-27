"""Synthetic verifier input; never evidence of native game behavior."""

import copy


def synthetic_receipt(suite: dict) -> dict:
    """Build verifier input only; this is not a native-engine conformance result."""
    cases = []
    for index, case in enumerate(suite["cases"]):
        states = copy.deepcopy(case["expected"])
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
