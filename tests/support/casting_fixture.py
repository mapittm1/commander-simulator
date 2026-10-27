"""Synthetic casting evidence for verifier tests; never native gameplay evidence."""

import copy

from commander.engine.interactions.casting import ACCEPTED_CASTS


def add_casting_states(states: dict, case_id: str, index: int) -> None:
    """Populate raw identities independently of any native execution."""
    target, spell, ability = 2000 + index, 1000 + index, 3000 + index
    pending = case_id == "cast_permanent_nonempty_stack"
    sources = [
        {
            "id": 4000 + index * 2 + offset,
            "timestamp": 10,
            "controller_id": 0,
            "name": "Plains" if case_id == "cast_instant_wrong_color" else "Forest",
            "zone": "Battlefield",
            "tapped": pending and offset == 0,
        }
        for offset in range(states["before"]["source_count"])
    ]
    for stage, state in states.items():
        paid = stage != "before" and case_id in ACCEPTED_CASTS
        targeted = "permanent" not in case_id
        on_stack = paid and stage == "after_attempt"
        state.update(
            actor_id=0,
            priority_id=1 if case_id == "cast_instant_without_priority" else 0,
            active_player_id=0,
            spell_id=spell,
            spell_timestamp=10,
            ability_id=ability,
            target_id=target,
            target_timestamp=10,
            sources=copy.deepcopy(sources),
            prepared_target_ids=[target] if targeted else [],
            stack_ability_ids=(
                [8000 + index] if pending else ([ability] if on_stack else [])
            ),
            stack_card_ids=[9000 + index] if pending else ([spell] if on_stack else []),
            stack_target_ids=[target] if pending or on_stack and targeted else [],
            payment=[],
        )
        if paid:
            state["sources"][0]["tapped"] = True
            state["payment"] = [
                {
                    "source_id": sources[0]["id"],
                    "source_timestamp": 10,
                    "color": 16,
                    "symbol": "G",
                }
            ]
