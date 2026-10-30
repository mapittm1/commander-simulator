"""Fixed paid witnesses for queued extra turns and combat phases."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Trusted fixed fixtures; no uploaded code, card programs or executable paths.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.delayed_worker import _settle, _setup
from tests.support.duration_worker import _snapshot
from tests.support.duration_worker import _targeted as _targeted_turn
from tests.support.native_worker import initialize_test_engine
from tests.support.usage_worker import _add, _cast


def _seat(game, player):
    """Retain registered seat indices even after elimination."""
    return next(
        index
        for index, registered in enumerate(game.getRegisteredPlayers())
        if registered.getId() == player.getId()
    )


def _next_turn(java, game):
    """Run real phase hooks through the next main phase without response priority."""
    resolver = java.JProxy("java.lang.Runnable", {"run": lambda: _settle(game)})
    if not game.getPhaseHandler().devAdvanceThroughTurnEnd(
        java.JClass("forge.game.phase.PhaseType").MAIN1, resolver
    ):
        raise ValueError("Fixed turn-schedule fixture could not reach the next main")
    _settle(game)


def _advance(java, game, phase):
    """Resolve the native phase-entry obligations at a fixed checkpoint."""
    resolver = java.JProxy("java.lang.Runnable", {"run": lambda: _settle(game)})
    if not game.getPhaseHandler().devAdvanceToPhase(
        java.JClass("forge.game.phase.PhaseType").valueOf(phase), resolver
    ):
        raise ValueError("Fixed turn-schedule fixture could not reach its checkpoint")
    _settle(game)


def _turn_state(game):
    player = game.getPhaseHandler().getPlayerTurn()
    return {
        "seat": _seat(game, player),
        "extra": bool(player.isExtraTurn()),
        "remaining": int(player.getExtraTurnCount()),
    }


def _turns(java, stretch=False, ordered=False):
    game, owner, _zones = _setup(java, ("Island",) * (10 if stretch or ordered else 5))
    target = owner if stretch else game.getPlayers().get(java.JInt(2))
    paid = []
    if ordered:
        paid.append(
            _targeted_turn(
                java,
                game,
                "Time Warp",
                owner,
                game.getPlayers().get(java.JInt(1)),
            )
        )
    paid.append(
        _targeted_turn(
            java, game, "Time Stretch" if stretch else "Time Warp", owner, target
        )
    )
    copied, gaps = _snapshot(java, game)
    before = int(copied.getPlayer(owner.getId()).getExtraTurnCount())
    turns = []
    for _ in range(3 if stretch or ordered else 2):
        _next_turn(java, copied)
        turns.append(_turn_state(copied))
    return {
        "case": "ordered" if ordered else "stretch" if stretch else "warp",
        "paid": paid,
        "before_remaining": before,
        "turns": turns,
        "source_next": _seat(game, game.getPhaseHandler().getNextTurn()),
        "source_turn": int(game.getPhaseHandler().getTurn()),
        "source_remaining": int(owner.getExtraTurnCount()),
        "unmapped": gaps,
    }


def _tapped(player, zones):
    return sum(bool(card.isTapped()) for card in player.getCardsIn(zones.Battlefield))


def _savor(java):
    game, owner, zones = _setup(java, ("Island",) * 3)
    _, paid = _cast(java, game, "Savor the Moment", owner, zones)
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    first = _turn_state(copied)
    tapped = _tapped(copied.getPlayer(owner.getId()), zones)
    for _ in range(4):
        _next_turn(java, copied)
    return {
        "case": "savor",
        "paid": paid,
        "first": first,
        "tapped_extra": tapped,
        "later": _turn_state(copied),
        "tapped_later": _tapped(copied.getPlayer(owner.getId()), zones),
        "source_tapped": _tapped(owner, zones),
        "unmapped": gaps,
    }


def _fortune(java):
    game, owner, zones = _setup(java, ("Mountain",) * 2)
    _, paid = _cast(java, game, "Final Fortune", owner, zones)
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    first = _turn_state(copied)
    _advance(java, copied, "END_OF_TURN")
    return {
        "case": "fortune",
        "paid": paid,
        "first": first,
        "owner_lost": bool(copied.getPlayer(owner.getId()).hasLost()),
        "active_seats": len(copied.getPlayers()),
        "source_lost": bool(owner.hasLost()),
        "unmapped": gaps,
    }


def _assault(java, multiple):
    game, owner, zones = _setup(java, ("Mountain",) * (8 if multiple else 4))
    paid = []
    for _ in range(2 if multiple else 1):
        _, mana = _cast(java, game, "Relentless Assault", owner, zones)
        paid.append(mana)
    copied, gaps = _snapshot(java, game)
    phases = java.JClass("forge.game.phase.PhaseType")
    scheduled = bool(
        copied.getPhaseHandler().hasExtraPhaseAfter(phases.MAIN1, phases.COMBAT_BEGIN)
    )
    _advance(java, copied, "END_OF_TURN")
    return {
        "case": "multiple_assaults" if multiple else "assault",
        "paid": paid,
        "scheduled": scheduled,
        "combats": int(copied.getPhaseHandler().getNumCombat()),
        "mains": int(copied.getPhaseHandler().getNumMain()),
        "source_combats": int(game.getPhaseHandler().getNumCombat()),
        "source_scheduled": bool(
            game.getPhaseHandler().hasExtraPhaseAfter(phases.MAIN1, phases.COMBAT_BEGIN)
        ),
        "unmapped": gaps,
    }


def _mid_turn(java):
    game, owner, zones = _setup(java, ("Island",) * 3)
    _, paid = _cast(java, game, "Savor the Moment", owner, zones)
    _next_turn(java, game)
    copied, gaps = _snapshot(java, game)
    return {
        "case": "mid_extra_turn",
        "paid": paid,
        "source": _turn_state(game),
        "copied": _turn_state(copied),
        "source_upkeeps": int(game.getPhaseHandler().getNumUpkeep()),
        "copied_upkeeps": int(copied.getPhaseHandler().getNumUpkeep()),
        "source_previous": _seat(game, game.getPhaseHandler().getPreviousPlayerTurn()),
        "copied_previous": (
            None
            if copied.getPhaseHandler().getPreviousPlayerTurn() is None
            else _seat(copied, copied.getPhaseHandler().getPreviousPlayerTurn())
        ),
        "unmapped": gaps,
    }


def _moraug(java):
    game, owner, zones = _setup(java, ("Mountain",) * 6)
    _, paid = _cast(java, game, "Moraug, Fury of Akoum", owner, zones)
    bear = _add(java, "Grizzly Bears", owner, zones.Battlefield)
    bear.setTapped(True)
    land = _add(java, "Mountain", owner, zones.Hand)
    play = next(
        action
        for action in land.getAllPossibleAbilities(owner, False)
        if action.isLandAbility()
    )
    play.setActivatingPlayer(owner)
    if not play.canPlay() or not java.JClass("forge.ai.ComputerUtil").playNoStack(
        owner, play, game, False
    ):
        raise ValueError("Fixed Moraug fixture could not play its land")
    _settle(game)
    copied, gaps = _snapshot(java, game)
    _advance(java, copied, "COMBAT_BEGIN")
    copied_bear = next(
        card for card in copied.getCardsInGame() if card.getId() == bear.getId()
    )
    untapped = not bool(copied_bear.isTapped())
    _advance(java, copied, "END_OF_TURN")
    return {
        "case": "moraug",
        "paid": paid,
        "untapped": untapped,
        "source_tapped": bool(bear.isTapped()),
        "combats": int(copied.getPhaseHandler().getNumCombat()),
        "unmapped": gaps,
    }


def main() -> None:
    """Keep before-fix observations as data, with assertions in the integration test."""
    java, engine = initialize_test_engine()
    cases = [_turns(java), _turns(java, stretch=True), _turns(java, ordered=True)]
    cases.extend((_savor(java), _fortune(java)))
    cases.extend(_assault(java, multiple) for multiple in (False, True))
    cases.extend((_mid_turn(java), _moraug(java)))
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "turn-schedule.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
