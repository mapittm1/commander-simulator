"""Fixed paid usage-history witnesses for the packaged native runtime."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Test-only checkout bootstrap; these fixture programs never come from uploads.
# pylint: disable=wrong-import-position
from commander.engine.interactions.fixtures import _card
from commander.engine.interactions.suite import write_bounded_json
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.starting_life_worker import _pod


def _add(java, name, owner, zone):
    """Create one fixed native fixture card."""
    return _card(java, {"engine_name": name}, owner, zone)


def _setup(java, lands):
    """Create a four-seat partial game with sufficient cards for draw witnesses."""
    game, owner = _pod(java, 40)
    zones = java.JClass("forge.game.zone.ZoneType")
    for _ in range(4):
        _add(java, "Forest", owner, zones.Library)
    for name in lands:
        _add(java, name, owner, zones.Battlefield)
    return game, owner, zones


def _find(game, identity):
    """Select a current native card without conflating same-name incarnations."""
    return next(card for card in game.getCardsInGame() if card.getId() == identity)


def _settle(game):
    """Admit waiting native triggers and resolve the resulting stack."""
    game.getAction().checkStateEffects(True)
    game.getTriggerHandler().runWaitingTriggers()
    game.getStack().addAllTriggeredAbilitiesToStack()
    while not game.getStack().isEmpty():
        game.getStack().resolveStack()
    game.getAction().checkStateEffects(True)


def _cast(java, game, name, owner, zones):
    """Use normal admission and payment, retaining the paid mana count."""
    card = _add(java, name, owner, zones.Hand)
    _pay(java, game, card.getFirstSpellAbility(), owner)
    paid = len(game.getStack().peekAbility().getPayingMana())
    game.getStack().resolveStack()
    game.getAction().checkStateEffects(True)
    return _find(game, card.getId()), paid


def _activated(card, api, owner):
    """Locate an activated native ability and bind its acting participant."""
    ability = next(
        action
        for action in card.getSpellAbilities()
        if action.isActivatedAbility() and str(action.getApi()) == api
    )
    ability.setActivatingPlayer(owner)
    return ability


def _usage(ability):
    """Observe native counters and admission rather than inferring from text."""
    return {
        "turn": int(ability.getActivationsThisTurn()),
        "game": int(ability.getActivationsThisGame()),
        "resolved": int(ability.getResolvedThisTurn()),
        "playable": bool(ability.canPlay()),
    }


def _copy(java, game):
    """Preserve any already-paid pending entries without casting them again."""
    return (
        java.JClass("forge.ai.simulation.GameCopier")(game).withStack(True).makeCopy()
    )


def _prepare_activation(java, lifetime):
    """Pay the source cast and activation, returning only the needed observations."""
    lands = ("Plains",) * 15 if lifetime else ("Plains", "Mountain", "Forest")
    game, owner, zones = _setup(java, lands)
    target_owner = game.getPlayers().get(java.JInt(1)) if lifetime else owner
    target = _add(java, "Grizzly Bears", target_owner, zones.Battlefield)
    name = "In the Trenches" if lifetime else "Invigorating Hot Spring"
    card, cast_paid = _cast(java, game, name, owner, zones)
    api = "ChangeZone" if lifetime else "PutCounter"
    ability = _activated(card, api, owner)
    ability.getTargets().add(target)
    _pay(java, game, ability, owner)
    return (
        game,
        owner,
        ability,
        {
            "cast_paid": cast_paid,
            "activation_paid": len(game.getStack().peekAbility().getPayingMana()),
        },
    )


def _activation(java, case, pending=False):
    """Exercise once-per-turn or lifetime limits with real payment."""
    game, owner, ability, state = _prepare_activation(java, case == "only_once")
    if not pending:
        game.getStack().resolveStack()
    state["case"] = case
    state["before"] = _usage(ability)
    copied = _copy(java, game)
    mapped = _activated(
        _find(copied, ability.getHostCard().getId()),
        str(ability.getApi()),
        copied.getPlayer(owner.getId()),
    )
    state["after"] = _usage(mapped)
    state["entries"] = int(copied.getStack().size())
    if pending:
        copied.getStack().resolveStack()
    state.update(
        resolved_after=_usage(mapped),
        source=_usage(ability),
        source_entries=int(game.getStack().size()),
        actor_world=bool(mapped.getActivatingPlayer().getGame() == copied),
        host_world=bool(mapped.getHostCard().getGame() == copied),
    )
    return state


def _vampire(java):
    """A second paid creature must not grant a second Vampire draw this turn."""
    game, owner, zones = _setup(java, ("Forest",) * 4)
    vampire = _add(java, "Welcoming Vampire", owner, zones.Battlefield)
    game.getAction().checkStateEffects(True)
    _, first_paid = _cast(java, game, "Grizzly Bears", owner, zones)
    _settle(game)
    before = len(owner.getCardsIn(zones.Hand))
    copied = _copy(java, game)
    mapped_owner = copied.getPlayer(owner.getId())
    _, second_paid = _cast(java, copied, "Grizzly Bears", mapped_owner, zones)
    _settle(copied)
    return {
        "case": "vampire",
        "paid": [first_paid, second_paid],
        "before": before,
        "after": len(mapped_owner.getCardsIn(zones.Hand)),
        "source": len(owner.getCardsIn(zones.Hand)),
        "host_world": bool(_find(copied, vampire.getId()).getGame() == copied),
    }


def _mode(card):
    """Find Galadriel's native charm override used as the mode-history key."""
    return next(
        trigger.getOverridingAbility()
        for trigger in card.getTriggers()
        if str(trigger.getParam("Execute")) == "TrigCharm"
    )


def _chosen(card):
    """Copy native strings into a bounded JSON-friendly observation."""
    modes = card.getChosenModes(_mode(card), "ThisTurn")
    return [str(mode) for mode in modes] if modes is not None else []


def _modes(java):
    """Observe three actual alliance choices across two successive snapshots."""
    game, owner, zones = _setup(java, ("Plains", "Island") + ("Forest",) * 11)
    card, cast_paid = _cast(java, game, "Galadriel, Light of Valinor", owner, zones)
    _, first_paid = _cast(java, game, "Grizzly Bears", owner, zones)
    _settle(game)
    first = _chosen(card)
    copied = _copy(java, game)
    mapped = _find(copied, card.getId())
    retained = _chosen(mapped)
    _, second_paid = _cast(
        java, copied, "Grizzly Bears", copied.getPlayer(owner.getId()), zones
    )
    _settle(copied)
    second = _chosen(mapped)
    third = _copy(java, copied)
    _, third_paid = _cast(
        java, third, "Grizzly Bears", third.getPlayer(owner.getId()), zones
    )
    _settle(third)
    return {
        "case": "modes",
        "paid": [cast_paid, first_paid, second_paid, third_paid],
        "first": first,
        "retained": retained,
        "second": second,
        "third": _chosen(_find(third, card.getId())),
        "source": _chosen(card),
        "middle_source": _chosen(mapped),
    }


def _saddle(java):
    """Pay two native saddle tap costs and preserve count and member provenance."""
    game, owner, zones = _setup(java, ("Forest",) * 3)
    _add(java, "Grizzly Bears", owner, zones.Battlefield).setSickness(False)
    mount, cast_paid = _cast(java, game, "Ornery Tumblewagg", owner, zones)
    saddle = next(
        ability
        for ability in mount.getSpellAbilities()
        if ability.isActivatedAbility()
        and ability.isKeyword(java.JClass("forge.game.keyword.Keyword").SADDLE)
    )
    for index in range(2):
        if index:
            _add(java, "Grizzly Bears", owner, zones.Battlefield).setSickness(False)
        _pay(java, game, saddle, owner)
        game.getStack().resolveStack()
    copied = _copy(java, game)
    mapped = _find(copied, mount.getId())
    return {
        "case": "saddle",
        "cast_paid": cast_paid,
        "before": int(mount.getTimesSaddledThisTurn()),
        "after": int(mapped.getTimesSaddledThisTurn()),
        "source": int(mount.getTimesSaddledThisTurn()),
        "members": len(mapped.getSaddledByThisTurn()),
        "source_members": len(mount.getSaddledByThisTurn()),
        "member_worlds": all(
            member.getGame() == copied for member in mapped.getSaddledByThisTurn()
        ),
    }


def main() -> None:
    """Write fixed paid witnesses bound to the operator-selected runtime."""
    java, engine = initialize_test_engine()
    rows = [
        _activation(java, "once_per_turn"),
        _activation(java, "pending", pending=True),
        _activation(java, "only_once"),
        _vampire(java),
        _modes(java),
        _saddle(java),
    ]
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "usage-state.json",
        {"engine": engine, "cases": rows},
    )


if __name__ == "__main__":
    main()
