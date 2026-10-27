"""Exercise both native replacement orders without altering damage or engine scripts."""

from .fixtures import _card, _deal_damage, _new_game, _resolve_one, _settle, _snapshot
from .native_bridge import CLASS_NAME
from .ordering import EFFECT_FIELDS, ORDER_CASES
from .suite import STAGES


def observe_ordering_case(java, cards: dict, case: str) -> dict:
    """Build one fresh partial state and let the engine apply the controller's choices."""
    game = _new_game(java)
    player, victim = (game.getPlayers().get(java.JInt(index)) for index in (0, 1))
    zone = java.JClass("forge.game.zone.ZoneType")
    source = _card(java, cards["source"], player, zone.Battlefield)
    equipment = _card(java, cards["equipment"], player, zone.Graveyard)
    effects = {
        "doubler": _card(java, cards["doubler"], player, zone.Battlefield),
        "damage_reducer": _card(
            java, cards["damage_reducer"], victim, zone.Battlefield
        ),
    }
    for participant in game.getPlayers():
        participant.setLife(40, None)
        _card(java, cards["basic"], participant, zone.Library)
    _settle(game)
    controller = _install_controller(java, game, victim, cards, case)
    game.getTriggerHandler().resetActiveTriggers()
    game.copyLastState()
    context = {
        "game": game,
        "tracked": (source, equipment),
        "effects": effects,
        "controller": controller,
    }
    states = {STAGES[0]: _ordering_snapshot(java, context, cards, case)}
    _deal_damage(java, game, source, victim, not case.startswith("noncombat_"))
    _settle(game)
    states[STAGES[1]] = _ordering_snapshot(java, context, cards, case)
    states[STAGES[2]] = _ordering_snapshot(java, context, cards, case)
    _resolve_one(game)
    states[STAGES[3]] = _ordering_snapshot(java, context, cards, case)
    states[STAGES[4]] = _ordering_snapshot(java, context, cards, case)
    _resolve_one(game)
    states[STAGES[5]] = _ordering_snapshot(java, context, cards, case)
    return {"id": case, "states": states}


def _install_controller(java, game, victim, cards, case):
    preferences = java.JClass("java.util.ArrayList")()
    for role in ORDER_CASES[case]:
        preferences.add(cards[role]["engine_name"])
    controller = java.JClass(CLASS_NAME)(
        game, victim, victim.getLobbyPlayer(), preferences
    )
    victim.dangerouslySetController(controller)
    return controller


def _effect_identity(card) -> dict:
    replacements = list(card.getReplacementEffects())
    if len(replacements) != 1:
        raise ValueError(
            "Reviewed permanent must expose exactly one native replacement effect"
        )
    return {
        "effect_id": int(replacements[0].getId()),
        "card_id": int(card.getId()),
        "timestamp": int(card.getGameTimestamp()),
        "name": str(card.getName()),
        "controller_id": int(card.getController().getId()),
        "zone": str(card.getZone().getZoneType()),
    }


def _recorded_choices(controller) -> list:
    choices = []
    for row in controller.getChoices():
        choices.append(
            {
                "decider_id": int(row.get("decider_id")),
                "chosen_id": int(row.get("chosen_id")),
                "candidates": [
                    {
                        "name": str(value.get("name")),
                        **{key: int(value.get(key)) for key in EFFECT_FIELDS},
                    }
                    for value in row.get("candidates")
                ],
            }
        )
    return choices


def _ordering_snapshot(java, context, cards, case) -> dict:
    game = context["game"]
    state = _snapshot(java, game, context["tracked"], cards, case)
    state["ordering"] = {
        "actor_id": int(game.getPlayers().get(java.JInt(0)).getId()),
        "affected_player_id": int(game.getPlayers().get(java.JInt(1)).getId()),
        "effects": {
            role: _effect_identity(card) for role, card in context["effects"].items()
        },
        "choices": _recorded_choices(context["controller"]),
    }
    state["choice_count"] = len(state["ordering"]["choices"])
    state["damage_reducer_active"] = (
        state["ordering"]["effects"]["damage_reducer"]["zone"] == "Battlefield"
    )
    return state
