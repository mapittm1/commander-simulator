"""Paid temporary-effect cleanup and regeneration history witnesses."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted native programs; uploaded decks never select executable code.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _postcombat
from tests.support.duration_worker import _snapshot
from tests.support.keyword_expiration_worker import _resolve
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.turn_schedule_worker import _advance, _next_turn
from tests.support.usage_worker import _activated, _add, _cast, _find, _settle, _setup


def _pod(java):
    game, owner, zones = _setup(java, ())
    for player in game.getPlayers():
        for name in ("Forest", "Swamp", "Island", "Mountain", "Plains"):
            for _ in range(8):
                _add(java, name, player, zones.Battlefield)
        for _ in range(6):
            _add(java, "Forest", player, zones.Library)
    _postcombat(java, game, owner)
    return game, owner, zones


def _effects(java, game):
    zones = java.JClass("forge.game.zone.ZoneType")
    effect = java.JClass("forge.card.GamePieceType").EFFECT
    return sorted(
        str(card.getName())
        for card in game.getCardsIn(zones.Command)
        if card.getGamePieceType() == effect
    )


def _tokens(java, game):
    zones = java.JClass("forge.game.zone.ZoneType")
    return sum(card.isToken() for card in game.getCardsIn(zones.Battlefield))


def _state(java, game, identities):
    copier = java.JClass("forge.game.card.CardCopyService")
    rows = []
    for identity in identities:
        card = _find(game, identity)
        history = copier.getLKICopy(card)
        rows.append(
            {
                "id": int(card.getId()),
                "zone": str(card.getZone().getZoneType()),
                "regeneration_shields": int(card.getShieldCount()),
                "regenerated_this_turn": int(card.getRegeneratedThisTurn()),
                "lki_regeneration_shields": int(history.getShieldCount()),
                "lki_regenerated_this_turn": int(history.getRegeneratedThisTurn()),
                "tapped": bool(card.isTapped()),
                "damage": int(card.getDamage()),
                "haste": bool(card.hasKeyword("Haste")),
                "in_world": bool(card.getGame() == game and history.getGame() == game),
            }
        )
    return {
        "cards": rows,
        "effects": _effects(java, game),
        "tokens": _tokens(java, game),
        "life": [int(player.getLife()) for player in game.getPlayers()],
    }


def _spell(java, game, name, owner, target):
    zones = java.JClass("forge.game.zone.ZoneType")
    ability = _add(java, name, owner, zones.Hand).getFirstSpellAbility()
    ability.setActivatingPlayer(owner)
    if not ability.canTarget(target):
        raise ValueError("Fixed cleanup witness selected an illegal target")
    return _resolve(java, game, ability, owner, target)


def _prepare(java, kind):
    game, owner, zones = _pod(java)
    costs = []
    if kind.startswith("unearth"):
        card = _add(java, "Sedraxis Specter", owner, zones.Graveyard)
        keyword = java.JClass("forge.game.keyword.Keyword").UNEARTH
        ability = next(
            action
            for action in card.getSpellAbilities()
            if action.isActivatedAbility() and action.isKeyword(keyword)
        )
        costs.append(_resolve(java, game, ability, owner))
        return game, [int(card.getId())], costs
    if kind.startswith("dying"):
        card = _add(java, "Wall of Stone", owner, zones.Battlefield)
        costs.append(_spell(java, game, "Incendiary Flow", owner, card))
    elif kind == "starfish":
        card, paid = _cast(java, game, "Spiny Starfish", owner, zones)
        costs.extend(
            (paid, _resolve(java, game, _activated(card, "Regenerate", owner), owner))
        )
        costs.append(_spell(java, game, "Murder", owner, card))
    else:
        card = _add(java, "Grizzly Bears", owner, zones.Battlefield)
        if kind == "regen_partial":
            second = _add(java, "Runeclaw Bear", owner, zones.Battlefield)
            _, paid = _cast(java, game, "Wrap in Vigor", owner, zones)
            costs.extend((paid, _spell(java, game, "Murder", owner, card)))
            return game, [int(card.getId()), int(second.getId())], costs
        costs.append(_spell(java, game, "Regenerate", owner, card))
    return game, [int(card.getId())], costs


def _action(java, game, identities, kind):
    owner = game.getPlayers().get(java.JInt(0))
    if kind in {
        "regen_expiry",
        "regen_partial",
        "dying_expiry",
        "unearth_end",
        "starfish",
    }:
        _next_turn(java, game)
    if kind.startswith("regen") or kind.startswith("dying"):
        return _spell(java, game, "Murder", owner, _find(game, identities[-1]))
    if kind == "unearth_bounce":
        return _spell(java, game, "Unsummon", owner, _find(game, identities[0]))
    return 0


def _temporal(java, kind):
    game, identities, costs = _prepare(java, kind)
    observations = {"before": _state(java, game, identities)}
    copied, gaps = _snapshot(java, game)
    observations["copied_before"] = _state(java, copied, identities)
    costs.append(_action(java, copied, identities, kind))
    observations["after"] = _state(java, copied, identities)
    observations["source"] = _state(java, game, identities)
    costs.append(_action(java, game, identities, kind))
    observations["source_after"] = _state(java, game, identities)
    _next_turn(java, copied)
    observations["after_cleanup"] = _state(java, copied, identities)
    return {"case": kind, "paid": costs, **observations, "unmapped": gaps}


def _combat(java, game, identity):
    player = game.getPlayers().get(java.JInt(1))
    phase = java.JClass("forge.game.phase.PhaseType")
    game.getPhaseHandler().devModeSet(
        phase.COMBAT_DECLARE_ATTACKERS,
        player,
        java.JInt(game.getPhaseHandler().getTurn()),
    )
    game.getPhaseHandler().setCombat(java.JClass("forge.game.combat.Combat")(player))
    creature = _find(game, identity)
    game.getCombat().addAttacker(creature, game.getPlayers().get(java.JInt(0)))
    java.JClass("forge.game.combat.CombatUtil").checkDeclaredAttacker(
        game, creature, game.getCombat(), True
    )
    _settle(game)
    _advance(java, game, "MAIN2")


def _fog(java):
    game, owner, zones = _pod(java)
    _next_turn(java, game)
    creature, paid = _cast(
        java, game, "Grizzly Bears", game.getPlayers().get(java.JInt(1)), zones
    )
    for _ in range(3):
        _next_turn(java, game)
    _, fog_paid = _cast(java, game, "Fog", owner, zones)
    identities = [int(creature.getId())]
    observations = {"before": _state(java, game, identities)}
    copied, gaps = _snapshot(java, game)
    observations["copied_before"] = _state(java, copied, identities)
    _next_turn(java, copied)
    _combat(java, copied, identities[0])
    observations["after"] = _state(java, copied, identities)
    observations["source"] = _state(java, game, identities)
    _next_turn(java, game)
    _combat(java, game, identities[0])
    observations["source_after"] = _state(java, game, identities)
    return {"case": "fog", "paid": [paid, fog_paid], **observations, "unmapped": gaps}


def _army_state(java, game):
    zones = java.JClass("forge.game.zone.ZoneType")
    counter = java.JClass("forge.game.card.CounterEnumType").P1P1
    armies = [
        {
            "id": int(card.getId()),
            "orc": bool(card.getType().hasSubtype("Orc")),
            "zombie": bool(card.getType().hasSubtype("Zombie")),
            "counters": int(card.getCounters(counter)),
            "in_world": bool(card.getGame() == game),
        }
        for card in game.getCardsIn(zones.Battlefield)
        if card.getType().hasSubtype("Army")
    ]
    return {"armies": armies, "effects": _effects(java, game)}


def _amass(java):
    game, owner, zones = _pod(java)
    _, first = _cast(java, game, "Lazotep Reaver", owner, zones)
    _settle(game)
    _, second = _cast(java, game, "Dunland Crebain", owner, zones)
    _settle(game)
    observations = {"before": _army_state(java, game)}
    identity = observations["before"]["armies"][0]["id"]
    copied, gaps = _snapshot(java, game)
    observations["copied_before"] = _army_state(java, copied)
    paid = _spell(
        java,
        copied,
        "Unsummon",
        copied.getPlayers().get(java.JInt(0)),
        _find(copied, identity),
    )
    observations["after"] = _army_state(java, copied)
    observations["source"] = _army_state(java, game)
    original_paid = _spell(java, game, "Unsummon", owner, _find(game, identity))
    observations["source_after"] = _army_state(java, game)
    return {
        "case": "amass",
        "paid": [first, second, paid, original_paid],
        **observations,
        "unmapped": gaps,
    }


def _replay_setup(java):
    game, owner, zones = _pod(java)
    opt = _add(java, "Opt", owner, zones.Graveyard)
    hulk = _add(java, "Torrential Gearhulk", owner, zones.Hand)
    _pay(java, game, hulk.getFirstSpellAbility(), owner)
    paid = len(game.getStack().peekAbility().getPayingMana())
    game.getStack().resolveStack()
    game.getAction().checkStateEffects(True)
    game.getTriggerHandler().runWaitingTriggers()
    game.getStack().addAllTriggeredAbilitiesToStack()
    if str(game.getStack().peekAbility().getApi()) != "Play":
        raise ValueError("Fixed replay witness did not prepare its native play trigger")
    game.getStack().resolveStack()
    if (
        game.getStack().isEmpty()
        or game.getStack().peekAbility().getHostCard().getId() != opt.getId()
    ):
        raise ValueError("Fixed replay witness did not retain the replayed instant")
    return game, int(opt.getId()), paid


def _replay_action(java, game, counter):
    if not counter:
        game.getStack().resolveStack()
        _settle(game)
        return 0
    owner = game.getPlayers().get(java.JInt(0))
    zones = java.JClass("forge.game.zone.ZoneType")
    ability = _add(java, "Counterspell", owner, zones.Hand).getFirstSpellAbility()
    ability.setActivatingPlayer(owner)
    target = game.getStack().peekAbility()
    if not ability.canTarget(target):
        raise ValueError("Fixed replay witness selected an illegal spell target")
    return _resolve(java, game, ability, owner, target)


def _replay(java, counter):
    game, identity, paid = _replay_setup(java)
    observations = {"before": _state(java, game, [identity])}
    copied, gaps = _snapshot(java, game)
    observations["copied_before"] = _state(java, copied, [identity])
    library = len(
        copied.getPlayers()
        .get(java.JInt(0))
        .getCardsIn(java.JClass("forge.game.zone.ZoneType").Library)
    )
    copied_paid = _replay_action(java, copied, counter)
    observations["after"] = _state(java, copied, [identity])
    observations["source"] = _state(java, game, [identity])
    remaining = len(
        copied.getPlayers()
        .get(java.JInt(0))
        .getCardsIn(java.JClass("forge.game.zone.ZoneType").Library)
    )
    _replay_action(java, game, False)
    observations["source_after"] = _state(java, game, [identity])
    _next_turn(java, copied)
    observations["after_cleanup"] = _state(java, copied, [identity])
    return {
        "case": "replay_counter" if counter else "replay_resolve",
        "paid": [paid, copied_paid],
        "library_change": library - remaining,
        **observations,
        "unmapped": gaps,
    }


def main() -> None:
    """Retain twelve paid witnesses and source/copy outcomes."""
    java, engine = initialize_test_engine()
    cases = [_fog(java)]
    cases.extend(
        _temporal(java, kind)
        for kind in (
            "regen_expiry",
            "regen_partial",
            "regen_use",
            "dying_expiry",
            "dying_active",
            "unearth_bounce",
            "unearth_end",
            "starfish",
        )
    )
    cases.extend((_amass(java), _replay(java, False), _replay(java, True)))
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "effect-cleanup.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
