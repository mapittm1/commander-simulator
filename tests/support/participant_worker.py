"""Fixed packaged-runtime witnesses for eliminated Commander participants."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed test-only checkout bootstrap; no uploaded modules.
# pylint: disable=wrong-import-position
from commander.engine.interactions.fixtures import _card, _new_game
from commander.engine.interactions.suite import write_bounded_json
from tests.support.native_worker import initialize_test_engine


def _pod(java):
    game = _new_game(java)
    zone = java.JClass("forge.game.zone.ZoneType")
    for player in game.getPlayers():
        player.setLife(40, None)
        _card(java, {"engine_name": "Forest"}, player, zone.Library)
    return game


def _lose(game, player, reason):
    if reason == "concession":
        player.concede()
    elif reason == "life":
        player.setLife(0, None)
    elif reason == "poison":
        player.setPoisonCounters(10, next(iter(game.getPlayers())))
    else:
        raise ValueError("Unknown fixed loss scenario")
    game.getAction().checkStateEffects(True)
    if not player.hasLost() or game.getPlayers().contains(player):
        raise ValueError("Source fixture did not eliminate its selected participant")


def _state(game):
    return {
        "active": [int(player.getId()) for player in game.getPlayers()],
        "lost": [int(player.getId()) for player in game.getLostPlayers()],
        "registered": len(game.getRegisteredPlayers()),
        "lost_life": [int(player.getLife()) for player in game.getLostPlayers()],
        "lost_poison": [
            int(player.getPoisonCounters()) for player in game.getLostPlayers()
        ],
        "lost_outcomes": [
            str(player.getOutcome().lossState) for player in game.getLostPlayers()
        ],
    }


def _seat(java, seat, reason):
    game = _pod(java)
    player = game.getPlayers().get(java.JInt(seat))
    _lose(game, player, reason)
    before = _state(game)
    snapshot = java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()
    mapped = snapshot.getPlayer(player.getId())
    return {
        "seat": seat,
        "reason": reason,
        "before": before,
        "after": _state(snapshot),
        "mapped_lost": bool(mapped.hasLost()),
        "mapped_game": bool(mapped.getGame() == snapshot),
        "not_active": not snapshot.getPlayers().contains(mapped),
    }


def _history(java):
    game = _pod(java)
    owner = game.getPlayers().get(java.JInt(0))
    lost = game.getPlayers().get(java.JInt(1))
    zone = java.JClass("forge.game.zone.ZoneType")
    bear = _card(java, {"engine_name": "Grizzly Bears"}, owner, zone.Battlefield)
    bear.addRemembered(lost)
    bear.getDamageHistory().registerDamage(
        3, True, bear, lost, java.JClass("java.util.HashMap")()
    )
    _lose(game, lost, "concession")
    snapshot = java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()
    copied = next(
        card
        for card in snapshot.getCardsIn(zone.Battlefield)
        if card.getId() == bear.getId()
    )
    mapped = snapshot.getPlayer(lost.getId())
    remembered = list(copied.getRemembered())
    return {
        "received": int(mapped.getAssignedDamage(None, copied)),
        "remembered_canonical": len(remembered) == 1 and remembered[0] == mapped,
        "mapped_game": bool(mapped.getGame() == snapshot),
        "active": len(snapshot.getPlayers()),
    }


def _independent(java):
    game = _pod(java)
    first = game.getPlayers().get(java.JInt(1))
    second = game.getPlayers().get(java.JInt(2))
    _lose(game, first, "concession")
    snapshot = java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()
    _lose(snapshot, snapshot.getPlayer(second.getId()), "concession")
    again = java.JClass("forge.ai.simulation.GameCopier")(snapshot).makeCopy()
    return {
        "original_active": len(game.getPlayers()),
        "original_lost": len(game.getLostPlayers()),
        "original_second_lost": bool(second.hasLost()),
        "copy_active": len(snapshot.getPlayers()),
        "copy_lost": len(snapshot.getLostPlayers()),
        "recopy_equal": _state(snapshot) == _state(again),
        "lost_order": [int(player.getId()) for player in again.getLostPlayers()]
        == [int(first.getId()), int(second.getId())],
    }


def main():
    """Record source and copied participation from authentic native loss actions."""
    run = Path(sys.argv[1]).resolve()
    java, engine = initialize_test_engine()
    receipt = {
        "engine": engine,
        "seats": [
            _seat(java, seat, reason)
            for reason in ("concession", "life", "poison")
            for seat in (1, 2, 3)
        ],
        "history": _history(java),
        "independent": _independent(java),
    }
    write_bounded_json(run / "participant-state.json", receipt)


if __name__ == "__main__":
    main()
