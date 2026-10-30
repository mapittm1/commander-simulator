"""Fixed paid witnesses for delayed-trigger obligations and future registrations."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed checkout bootstrap; all scenario programs and card names below are trusted fixtures.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.duration_worker import _snapshot, _targeted
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.usage_worker import _activated, _add, _cast, _find
from tests.support.usage_worker import _setup as _partial_setup


def _settle(game):
    """Drain each resolution's new triggers with a fixed fixture safety bound."""
    for _ in range(40):
        game.getAction().checkStateEffects(True)
        game.getTriggerHandler().runWaitingTriggers()
        game.getStack().addAllTriggeredAbilitiesToStack()
        if game.getStack().isEmpty():
            return
        game.getStack().resolveStack()
    raise ValueError("Fixed delayed-trigger fixture exceeded its resolution bound")


def _setup(java, lands):
    """Give every seat enough library cards for multiple bounded turn cycles."""
    game, owner, zones = _partial_setup(java, lands)
    for player in game.getPlayers():
        if player != owner:
            for _ in range(4):
                _add(java, "Forest", player, zones.Library)
    return game, owner, zones


def _advance(java, game):
    """Run native phase hooks into the next upkeep without response priority."""
    resolver = java.JProxy("java.lang.Runnable", {"run": lambda: _settle(game)})
    if not game.getPhaseHandler().devAdvanceThroughTurnEnd(
        java.JClass("forge.game.phase.PhaseType").UPKEEP, resolver
    ):
        raise ValueError(
            "Fixed delayed-trigger fixture could not reach the next upkeep"
        )
    _settle(game)


def _end_step(java, game):
    """Reach and resolve the current turn's beginning-of-end-step events."""
    resolver = java.JProxy("java.lang.Runnable", {"run": lambda: _settle(game)})
    if not game.getPhaseHandler().devAdvanceToPhase(
        java.JClass("forge.game.phase.PhaseType").END_OF_TURN, resolver
    ):
        raise ValueError("Fixed delayed-trigger fixture could not reach the end step")
    _settle(game)


def _zone(game, identity):
    """Observe the current incarnation's zone, not a convenient same-name card."""
    return str(_find(game, identity).getZone().getZoneType())


def _present(game, identity, zone):
    return any(card.getId() == identity for card in game.getCardsIn(zone))


def _kiki(java, remove_source):
    """Create a real token and optionally kill Kiki before its delayed sacrifice."""
    game, owner, zones = _setup(java, ("Mountain",) * 5)
    opponent = game.getPlayers().get(java.JInt(1))
    _add(java, "Mountain", opponent, zones.Battlefield)
    bear = _add(java, "Grizzly Bears", owner, zones.Battlefield)
    kiki, paid = _cast(java, game, "Kiki-Jiki, Mirror Breaker", owner, zones)
    action = _activated(kiki, "CopyPermanent", owner)
    action.getTargets().add(bear)
    _pay(java, game, action, owner)
    game.getStack().resolveStack()
    _settle(game)
    token = next(card for card in owner.getCardsIn(zones.Battlefield) if card.isToken())
    response = (
        _targeted(java, game, "Lightning Bolt", opponent, kiki) if remove_source else 0
    )
    copied, gaps = _snapshot(java, game)
    _end_step(java, copied)
    return {
        "case": "kiki_removed" if remove_source else "kiki",
        "paid": [paid, response],
        "token_present": _present(copied, token.getId(), zones.Battlefield),
        "source_token_present": _present(game, token.getId(), zones.Battlefield),
        "source_kiki_zone": _zone(game, kiki.getId()),
        "unmapped": gaps,
    }


def _ghostway(java):
    """Return both real exiled creature identities in only the copied world."""
    game, owner, zones = _setup(java, ("Plains",) * 3)
    cards = [
        _add(java, name, owner, zones.Battlefield)
        for name in ("Grizzly Bears", "Runeclaw Bear")
    ]
    _, paid = _cast(java, game, "Ghostway", owner, zones)
    _settle(game)
    before = [_zone(game, card.getId()) for card in cards]
    copied, gaps = _snapshot(java, game)
    _end_step(java, copied)
    return {
        "case": "ghostway",
        "paid": paid,
        "before": before,
        "after": [_zone(copied, card.getId()) for card in cards],
        "source": [_zone(game, card.getId()) for card in cards],
        "controllers": [
            bool(
                _find(copied, card.getId()).getController()
                == copied.getPlayer(owner.getId())
            )
            for card in cards
        ],
        "unmapped": gaps,
    }


def _tapped_lands(player, zones):
    return sum(
        card.isLand() and card.isTapped()
        for card in player.getCardsIn(zones.Battlefield)
    )


def _pact(java, can_pay):
    """Counter a paid creature and observe the caster's next upkeep debt."""
    game, owner, zones = _setup(java, ("Island",) * 5 if can_pay else ())
    opponent = game.getPlayers().get(java.JInt(1))
    game.getPhaseHandler().devModeSet(
        java.JClass("forge.game.phase.PhaseType").MAIN1, opponent, java.JInt(1)
    )
    for _ in range(2):
        _add(java, "Forest", opponent, zones.Battlefield)
    bear = _add(java, "Grizzly Bears", opponent, zones.Hand)
    _pay(java, game, bear.getFirstSpellAbility(), opponent)
    counter = _add(java, "Pact of Negation", owner, zones.Hand).getFirstSpellAbility()
    counter.getTargets().add(game.getStack().peekAbility())
    _pay(java, game, counter, owner)
    game.getStack().resolveStack()
    _settle(game)
    copied, gaps = _snapshot(java, game)
    mapped = copied.getPlayer(owner.getId())
    for _ in range(3):
        _advance(java, copied)
    state = {
        "case": "pact_paid" if can_pay else "pact_unpaid",
        "countered_zone": _zone(game, bear.getId()),
        "owner_lost": bool(mapped.hasLost()),
        "active_seats": len(copied.getPlayers()),
        "tapped_after_debt": _tapped_lands(mapped, zones),
        "source_lost": bool(owner.hasLost()),
        "source_tapped": _tapped_lands(owner, zones),
        "unmapped": gaps,
    }
    if can_pay:
        for _ in range(4):
            _advance(java, copied)
        state["tapped_next_upkeep"] = _tapped_lands(mapped, zones)
        state["lost_next_upkeep"] = bool(mapped.hasLost())
    return state


def _infuse(java):
    """A paid slowtrip registers at cleanup and draws at the following upkeep."""
    game, owner, zones = _setup(java, ("Island",) * 3)
    bear = _add(java, "Grizzly Bears", owner, zones.Battlefield)
    bear.setTapped(True)
    paid = _targeted(java, game, "Infuse", owner, bear)
    copied, gaps = _snapshot(java, game)
    mapped = copied.getPlayer(owner.getId())
    before = len(mapped.getCardsIn(zones.Hand))
    _advance(java, copied)
    first = len(mapped.getCardsIn(zones.Hand))
    _advance(java, copied)
    return {
        "case": "infuse",
        "paid": paid,
        "target_untapped": not bear.isTapped(),
        "before": before,
        "first_upkeep": first,
        "second_upkeep": len(mapped.getCardsIn(zones.Hand)),
        "source": len(owner.getCardsIn(zones.Hand)),
        "unmapped": gaps,
    }


def _rebound(java):
    """A rebound cast blinks Visionary again, draws, and returns to the graveyard."""
    game, owner, zones = _setup(java, ("Forest", "Forest", "Plains"))
    visionary, paid = _cast(java, game, "Elvish Visionary", owner, zones)
    _settle(game)
    response = _targeted(java, game, "Ephemerate", owner, visionary)
    ephemerate = next(
        card
        for card in owner.getCardsIn(zones.Exile)
        if str(card.getName()) == "Ephemerate"
    )
    before = len(owner.getCardsIn(zones.Hand))
    copied, gaps = _snapshot(java, game)
    for _ in range(4):
        _advance(java, copied)
    mapped = copied.getPlayer(owner.getId())
    return {
        "case": "rebound",
        "paid": [paid, response],
        "before": before,
        "after": len(mapped.getCardsIn(zones.Hand)),
        "source": len(owner.getCardsIn(zones.Hand)),
        "copied_zone": _zone(copied, ephemerate.getId()),
        "source_zone": _zone(game, ephemerate.getId()),
        "lands_tapped": _tapped_lands(mapped, zones),
        "unmapped": gaps,
    }


def _suppress(java, registered):
    """Restore a target-bound delayed return before or after its registration turn."""
    game, owner, zones = _setup(java, ("Swamp",) * 3)
    opponent = game.getPlayers().get(java.JInt(1))
    cards = [
        _add(java, name, opponent, zones.Hand) for name in ("Grizzly Bears", "Island")
    ]
    action = _add(java, "Suppress", owner, zones.Hand).getFirstSpellAbility()
    action.getTargets().add(opponent)
    _pay(java, game, action, owner)
    game.getStack().resolveStack()
    _settle(game)
    if registered:
        _advance(java, game)
    copied, gaps = _snapshot(java, game)
    if not registered:
        _advance(java, copied)
    _end_step(java, copied)
    return {
        "case": "suppress_registered" if registered else "suppress_deferred",
        "after": [_zone(copied, card.getId()) for card in cards],
        "source": [_zone(game, card.getId()) for card in cards],
        "owners": [
            bool(
                _find(copied, card.getId()).getOwner()
                == copied.getPlayer(opponent.getId())
            )
            for card in cards
        ],
        "source_hand": len(opponent.getCardsIn(zones.Hand)),
        "unmapped": gaps,
    }


def main() -> None:
    """Write nine fixed observations, including before-fix behavior without assertions."""
    java, engine = initialize_test_engine()
    cases = [_kiki(java, False), _kiki(java, True), _ghostway(java)]
    cases.extend(_pact(java, can_pay) for can_pay in (False, True))
    cases.extend((_infuse(java), _rebound(java)))
    cases.extend(_suppress(java, registered) for registered in (False, True))
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "delayed-state.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
