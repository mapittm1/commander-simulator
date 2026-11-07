"""Fixed creature-exchange and random-pile witnesses using native paid AI actions."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted programs; deck uploads cannot select executable scenarios.
# pylint: disable=wrong-import-position
from tests.support.composed_control_worker import _mode, _settle
from tests.support.mass_counter_worker import (
    _Board,
    _choose,
    _finished,
    _paid_cast,
    _source_selected,
)
from tests.support.native_receipts import write_test_receipts
from tests.support.native_worker import initialize_test_engine
from tests.support.starting_life_worker import _pod
from tests.support.usage_worker import _add

KINDS = ("simple", "selective", "controlled", "whims")
MODES = ("heuristic", "hybrid", "full")
THREATS = (
    "Craw Wurm",
    "Colossal Dreadmaw",
    "Mammoth Spider",
    "Vastwood Gorger",
    "Gigantosaurus",
    "Ancient Brontodon",
    "Terra Stomper",
    "Impervious Greatwurm",
)


def _provision(java, game, actor, zones):
    for player in game.getPlayers():
        for _ in range(8):
            _add(java, "Forest", player, zones.Library)
    for _ in range(8):
        for name in ("Island", "Mountain"):
            _add(java, name, actor, zones.Battlefield)


def _creatures(java, actor, kind, control, zones):
    game = actor.getGame()
    cards = {}
    if kind == "whims":
        if control == "positive":
            for seat in range(1, 4):
                enemy = game.getPlayers().get(java.JInt(seat))
                for index, name in enumerate(THREATS):
                    cards[f"enemy_{seat}_{index}"] = _add(
                        java, name, enemy, zones.Battlefield
                    )
        return cards
    giver = game.getPlayers().get(java.JInt(2)) if kind == "controlled" else actor
    enemy = game.getPlayers().get(java.JInt(1))
    if control != "empty":
        cards["weak"] = _add(
            java,
            "Darksteel Colossus" if control == "harmful" else "Ornithopter",
            giver,
            zones.Battlefield,
        )
    cards["best"] = _add(
        java,
        "Ornithopter" if control == "harmful" else "Serra Angel",
        enemy,
        zones.Battlefield,
    )
    if kind in ("selective", "controlled"):
        cards["keep"] = _add(java, "Darksteel Colossus", giver, zones.Battlefield)
        cards["other"] = _add(java, "Grizzly Bears", enemy, zones.Battlefield)
    if kind == "controlled":
        # Explicit controller context, not a claimed paid player-control spell.
        actor.addController(game.getNextTimestamp(), giver)
    return cards


def _prepare(java, kind, mode, control):
    game, actor = _pod(java, 40)
    zones = java.JClass("forge.game.zone.ZoneType")
    _provision(java, game, actor, zones)
    cards = _creatures(java, actor, kind, control, zones)
    name = "Whims of the Fates" if kind == "whims" else "Cultural Exchange"
    cards["source"] = _add(java, name, actor, zones.Hand)
    game.getPhaseHandler().devModeSet(
        java.JClass("forge.game.phase.PhaseType").MAIN2, actor, java.JInt(1)
    )
    _mode(java, actor, mode)
    return _Board(game, actor, cards, 0)


def _counts(java, game):
    zone = java.JClass("forge.game.zone.ZoneType").Battlefield
    return [len(player.getCardsIn(zone)) for player in game.getRegisteredPlayers()]


def _resolve(java, board, kind, row):
    if kind != "whims":
        _settle(board.game)
        return
    randoms = java.JClass("forge.util.MyRandom")
    provider = randoms.getRandom()
    seed = 0
    predicted = java.JClass("java.util.Random")(java.JLong(seed))
    row["physical_seed"] = seed
    row["rolls_in_separation_order"] = [
        int(predicted.nextInt(java.JInt(3))) for _ in range(4)
    ]
    try:
        randoms.setRandom(java.JClass("java.util.Random")(java.JLong(seed)))
        board.game.getStack().resolveStack()
    finally:
        randoms.setRandom(provider)
    row["physical_rng_restored"] = randoms.getRandom() == provider
    _settle(board.game)


def _choose_piles(java, board):
    """Use the native witness seed without advancing the enclosing RNG stream."""
    randoms = java.JClass("forge.util.MyRandom")
    provider = randoms.getRandom()
    calls = int(randoms.callCount())
    outcome = {}

    def choose():
        outcome["selection"] = _choose(java, board)

    randoms.withRandom(
        java.JClass("java.util.Random")(java.JLong(0)),
        java.JProxy("java.util.function.Supplier", dict={"get": choose}),
    )
    if "selection" not in outcome:
        raise AssertionError("Seeded native decision did not execute")
    action, row = outcome["selection"]
    row["decision_seed"] = 0
    row["decision_rng_restored"] = randoms.getRandom() == provider
    row["decision_rng_calls_unchanged"] = int(randoms.callCount()) == calls
    return action, row


def _case(java, kind, mode, control="positive"):
    board = _prepare(java, kind, mode, control)
    action, row = (
        _choose_piles(java, board) if kind == "whims" else _choose(java, board)
    )
    row.update(
        {
            "kind": kind,
            "mode": mode,
            "control": control,
            "before_permanents": _counts(java, board.game),
        }
    )
    if not _source_selected(board, action, control):
        return row
    _paid_cast(board, action, row)
    _resolve(java, board, kind, row)
    row["after_permanents"] = _counts(java, board.game)
    return _finished(java, board, row)


def main() -> None:
    """Retain twelve paid decisions and three source-decline controls."""
    java, engine = initialize_test_engine()
    parts = {
        f"exchange-piles-{mode}.json": [_case(java, kind, mode) for kind in KINDS]
        for mode in MODES
    }
    parts["exchange-piles-controls.json"] = [
        _case(java, "simple", "heuristic", "harmful"),
        _case(java, "simple", "heuristic", "empty"),
        _case(java, "whims", "heuristic", "own_risk"),
    ]
    write_test_receipts(Path(sys.argv[1]), engine, "exchange-piles.json", parts)


if __name__ == "__main__":
    main()
