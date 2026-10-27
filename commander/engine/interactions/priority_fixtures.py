"""Observe bounded Forge priority loops with fixed action choices in partial games."""

from .casting_fixtures import _mana_source, _payment
from .fixtures import _card, _new_game, _settle
from .native_bridge import PRIORITY_CLASS_NAME


def observe_priority_case(java, cards: dict, case: str) -> dict:
    """Initialize once, then let mainLoopStep rotate priority and resolve the stack."""
    context, journal = _setup(java, cards, case)
    game = context[0]
    phase = game.getPhaseHandler()
    before = _snapshot(java, context)
    steps = []
    for _ in range(16):
        phase.mainLoopStep()
        steps.append(_snapshot(java, context))
        if game.getStack().isEmpty():
            break
    else:
        raise ValueError("Native priority loop exceeded its step limit")
    after = _snapshot(java, context)
    after["steps"] = steps
    after["callbacks"] = [
        {
            str(key): (list(value) if str(key) in {"stack", "targets"} else value)
            for key, value in row.entrySet()
        }
        for row in journal
    ]
    # JPype boxed scalars are explicitly converted before bounded JSON encoding.
    for row in after["callbacks"]:
        for key in ("player_id", "priority_id", "proposal_id"):
            row[key] = None if row[key] is None else int(row[key])
        for key in ("can_play", "can_pay", "cast"):
            row[key] = bool(row[key])
        for key in ("stack", "targets"):
            row[key] = [int(value) for value in row[key]]
    return {"id": case, "states": {"before": before, "after_sequence": after}}


def _snapshot(java, context):
    game, players, root, counter, lands = context
    phase = game.getPhaseHandler()
    abilities = [root.getFirstSpellAbility(), counter.getFirstSpellAbility()]
    return {
        "player_ids": [int(player.getId()) for player in players],
        "life": [int(player.getLife()) for player in players],
        "priority_id": int(phase.getPriorityPlayer().getId()),
        "active_player_id": int(phase.getPlayerTurn().getId()),
        "phase": str(phase.getPhase().name()),
        "stack": [int(entry.getSpellAbility().getId()) for entry in game.getStack()],
        "spells": [
            {
                "id": int(card.getId()),
                "ability_id": int(ability.getId()),
                "name": str(card.getName()),
                "controller_id": int(card.getController().getId()),
                "zone": str(game.getCardState(card).getZone().getZoneType()),
                "payment": _payment(java, ability),
            }
            for card, ability in zip((root, counter), abilities)
        ],
        "lands": [[_mana_source(card) for card in sources] for sources in lands],
    }


def _setup(java, cards, case):
    """Build only the fixed partial main-phase state and install scripted controllers."""
    game = _new_game(java)
    players = list(game.getPlayers())
    zone = java.JClass("forge.game.zone.ZoneType")
    for player in players:
        player.setLife(40, None)
        _card(java, cards["basic"], player, zone.Library)
    root = _card(java, cards["equipment"], players[0], zone.Hand)
    counter = _card(java, cards["counter"], players[1], zone.Hand)
    lands = [
        [_card(java, cards["basic"], players[0], zone.Battlefield)],
        [
            _card(java, cards["blue_basic"], players[1], zone.Battlefield)
            for _ in range(2 if case == "priority_unaffordable_response" else 3)
        ],
    ]
    journal = java.JClass("java.util.ArrayList")()
    controller = java.JClass(PRIORITY_CLASS_NAME)
    for index, player in enumerate(players):
        player.dangerouslySetController(
            controller(
                game,
                player,
                (root if index == 0 else counter if index == 1 else None),
                root,
                case != "priority_all_pass",
                journal,
            )
        )
    _settle(game)
    phase = game.getPhaseHandler()
    phase.setPriority(players[0])
    # Partial-state bootstrap only. Never called to progress a pending spell.
    phase.onStackResolved()
    return (game, players, root, counter, lands), journal
