"""Fixed paid mass-counter choices and effects through the production AI cast path."""

import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted test cases; uploaded decks cannot select executable programs.
# pylint: disable=wrong-import-position
from tests.support.composed_control_worker import _mode, _settle
from tests.support.decision_worker import _ability, _state
from tests.support.native_receipts import write_test_receipts
from tests.support.native_worker import initialize_test_engine
from tests.support.primary_control_worker import _world_fingerprint
from tests.support.starting_life_worker import _pod
from tests.support.turn_schedule_worker import _seat
from tests.support.usage_worker import _add, _find

SOURCES = {
    "aether": "Aether Snap",
    "dust": "Dust of Moments",
    "stone": "Oblivion Stone",
    "haze": "Pestilent Haze",
}
MODES = ("heuristic", "hybrid", "full")


@dataclass(frozen=True)
class _Board:
    game: Any
    actor: Any
    cards: dict[str, Any]
    setup_paid: int


def _counters(java, card, counter, amount):
    # Fixed accumulated counters, not a claimed placement/replacement history.
    values = java.JClass("com.google.common.collect.HashMultiset").create()
    values.add(
        java.JClass("forge.game.card.CounterEnumType").valueOf(counter),
        java.JInt(amount),
    )
    card.setCounters(values)


def _cast_source(java, game, actor, cards):
    action = cards["source"].getFirstSpellAbility()
    action.setActivatingPlayer(actor)
    if not java.JClass("forge.ai.ComputerUtil").playStack(action, actor, game):
        raise AssertionError("Fixed Oblivion Stone setup payment failed")
    paid = int(game.getStack().peekAbility().getPayingMana().size())
    _settle(game)
    cards["source"] = _find(game, cards["source"].getId())
    return paid


def _prepare(java, kind, mode, control):
    game, actor = _pod(java, 40)
    zones = java.JClass("forge.game.zone.ZoneType")
    for player in game.getPlayers():
        for _ in range(8):
            for name, zone in (
                ("Swamp", zones.Battlefield),
                ("Plains", zones.Battlefield),
                ("Forest", zones.Library),
            ):
                _add(java, name, player, zone)
    game.getPhaseHandler().devModeSet(
        java.JClass("forge.game.phase.PhaseType").MAIN2, actor, java.JInt(1)
    )
    _mode(java, actor, mode)
    cards = {"source": _add(java, SOURCES[kind], actor, zones.Hand)}
    setup_paid = _cast_source(java, game, actor, cards) if kind == "stone" else 0
    enemy = game.getPlayers().get(java.JInt(1))
    if kind == "aether" and control != "blank":
        owner = actor if control == "own_harm" else enemy
        cards["bear"] = _add(java, "Grizzly Bears", owner, zones.Battlefield)
        cards["walker"] = _add(java, "Jace Beleren", owner, zones.Battlefield)
        _counters(java, cards["bear"], "P1P1", 5)
        _counters(java, cards["walker"], "LOYALTY", 8)
    elif kind == "dust":
        cards["victim"] = _add(java, "Calciderm", enemy, zones.Battlefield)
        _counters(java, cards["victim"], "TIME", 2)
    elif kind == "haze":
        cards["walker"] = _add(java, "Jace Beleren", enemy, zones.Battlefield)
        _counters(java, cards["walker"], "LOYALTY", 2)
    elif kind == "stone":
        cards["protected"] = _add(java, "Darksteel Ingot", actor, zones.Battlefield)
        _counters(java, cards["protected"], "FATE", 1)
        for index, name in enumerate(("Serra Angel", "Shivan Dragon", "Craw Wurm")):
            cards[f"enemy_{index}"] = _add(java, name, enemy, zones.Battlefield)
    return _Board(game, actor, cards, setup_paid)


def _snapshot(java, board):
    counters = java.JClass("forge.game.card.CounterEnumType")
    rows = {}
    for tag, original in board.cards.items():
        card = _find(board.game, original.getId())
        rows[tag] = {
            "id": int(card.getId()),
            "zone": str(card.getZone().getZoneType()),
            "owner": _seat(board.game, card.getOwner()),
            "controller": _seat(board.game, card.getController()),
            "counters": {
                name: int(card.getCounters(counters.valueOf(name)))
                for name in ("P1P1", "LOYALTY", "TIME", "FATE")
            },
        }
    return rows


def _observe(java, board):
    return {
        "game": _state(
            java, board.game, [int(card.getId()) for card in board.cards.values()]
        ),
        "cards": _snapshot(java, board),
    }


def _choose(java, board):
    """Observe a native decision while retaining evidence of preview isolation."""
    before = _observe(java, board)
    fingerprint = _world_fingerprint(board.game)
    randoms = java.JClass("forge.util.MyRandom")
    provider = randoms.getRandom()
    choices = board.actor.getController().chooseSpellAbilityToPlay()
    action = None if choices is None or choices.isEmpty() else choices.get(java.JInt(0))
    return action, {
        "before": before,
        "after_choice": _observe(java, board),
        "world_unchanged": fingerprint == _world_fingerprint(board.game),
        "rng_restored": randoms.getRandom() == provider,
        "selected": None if action is None else _ability(board.game, action),
    }


def _source_selected(board, action, control):
    """Decline controls never force admission of the scenario source."""
    return (
        control == "positive"
        and action is not None
        and action.getHostCard().getId() == board.cards["source"].getId()
    )


def _paid_cast(board, action, row):
    """Cast the selected action and record actual native payment."""
    board.actor.getController().playChosenSpellAbility(action)
    if board.game.getStack().isEmpty():
        raise AssertionError("Selected native action did not reach the stack")
    pending = board.game.getStack().peekAbility()
    row["paid_mana"] = int(pending.getPayingMana().size())
    row["paid_action"] = _ability(board.game, pending)
    return pending


def _finished(java, board, row):
    """Record card identity and participant state after native resolution."""
    row["final"] = _snapshot(java, board)
    row["cast_counts"] = [
        int(player.getSpellsCastThisTurn())
        for player in board.game.getRegisteredPlayers()
    ]
    row["life"] = [
        int(player.getLife()) for player in board.game.getRegisteredPlayers()
    ]
    return row


def _case(java, kind, mode, control="positive"):
    board = _prepare(java, kind, mode, control)
    action, row = _choose(java, board)
    row.update(
        kind=kind,
        mode=mode,
        control=control,
        source=SOURCES[kind],
        setup_paid=board.setup_paid,
    )
    if not _source_selected(board, action, control):
        return row
    pending = _paid_cast(board, action, row)
    row["mode_api"] = (
        None
        if pending.getSubAbility() is None
        else str(pending.getSubAbility().getApi())
    )
    _settle(board.game)
    return _finished(java, board, row)


def main() -> None:
    """Retain twelve three-mode outcomes and two source-decline controls."""
    java, engine = initialize_test_engine()
    parts = {
        f"mass-counters-{mode}.json": [_case(java, kind, mode) for kind in SOURCES]
        for mode in MODES
    }
    parts["mass-counters-controls.json"] = [
        _case(java, "aether", "heuristic", control) for control in ("blank", "own_harm")
    ]
    write_test_receipts(Path(sys.argv[1]), engine, "mass-counters.json", parts)


if __name__ == "__main__":
    main()
