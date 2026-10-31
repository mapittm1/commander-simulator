"""Paid turn/combat-control witnesses on fixed partial Commander boards."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Trusted fixed witnesses; uploaded decks never select executable programs.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _postcombat
from tests.support.duration_worker import _snapshot
from tests.support.keyword_expiration_worker import _resolve
from tests.support.native_worker import initialize_test_engine
from tests.support.turn_schedule_worker import _advance, _next_turn, _seat
from tests.support.usage_worker import _activated, _add, _cast, _settle, _setup

CASES = (
    "worst_pending",
    "worst_active",
    "mindslaver_pending",
    "mindslaver_active",
    "combat_pending",
    "combat_active",
    "overlap_pending",
    "overlap_active",
    "self",
    "expired",
    "departed",
    "emrakul_pending",
    "emrakul_active",
)


def _player(java, game, seat):
    return game.getRegisteredPlayers().get(java.JInt(seat))


def _pod(java):
    game, owner, zones = _setup(java, ())
    for player in game.getPlayers():
        for _ in range(20):
            _add(java, "Swamp", player, zones.Battlefield)
            _add(java, "Forest", player, zones.Library)
        for _ in range(8):
            _add(java, "Island", player, zones.Battlefield)
    _postcombat(java, game, owner)
    return game


def _state(game, identities):
    players = list(game.getRegisteredPlayers())
    observations = []
    for seat, player in enumerate(players):
        master = player.getControllingPlayer()
        view_master = player.getView().getMindSlaveMaster()
        agent = player.getController()
        observations.append(
            {
                "seat": seat,
                "active": bool(player.isInGame()),
                "controlled": bool(player.isControlled()),
                "master": None if master is None else _seat(game, master),
                "view_master": (
                    None
                    if view_master is None
                    else next(
                        index
                        for index, other in enumerate(players)
                        if other.getView() == view_master
                    )
                ),
                "decision_maker": next(
                    index
                    for index, other in enumerate(players)
                    if other.getOriginalLobbyPlayer() == agent.getLobbyPlayer()
                ),
                "in_world": bool(
                    player.getGame() == game
                    and agent.getGame() == game
                    and agent.getPlayer() == player
                    and (master is None or master.getGame() == game)
                ),
            }
        )
    cards = []
    for identity in identities:
        card = next(
            (
                candidate
                for candidate in game.getCardsInGame()
                if candidate.getId() == identity
            ),
            None,
        )
        cards.append(
            {
                "id": identity,
                "present": card is not None,
                "name": None if card is None else str(card.getName()),
                "zone": None if card is None else str(card.getZone().getZoneType()),
                "in_world": None if card is None else bool(card.getGame() == game),
            }
        )
    turn = game.getPhaseHandler().getPlayerTurn()
    return {
        "players": observations,
        "cards": cards,
        "turn": _seat(game, turn),
        "extra": bool(turn.isExtraTurn()),
        "phase": str(game.getPhaseHandler().getPhase().name()),
    }


def _schedule(java, game, kind, seat, target_seat):
    zones = java.JClass("forge.game.zone.ZoneType")
    owner = _player(java, game, seat)
    target = _player(java, game, target_seat)
    if kind == "mindslaver":
        card, cost = _cast(java, game, "Mindslaver", owner, zones)
        ability = _activated(card, "ControlPlayer", owner)
        paid = [cost, _resolve(java, game, ability, owner, target)]
    elif kind == "emrakul":
        card, cost = _cast(java, game, "Emrakul, the Promised End", owner, zones)
        _settle(game)
        paid = [cost]
    else:
        card = _add(
            java,
            "Secret of Bloodbending" if kind == "combat" else "Worst Fears",
            owner,
            zones.Hand,
        )
        ability = card.getFirstSpellAbility()
        ability.setActivatingPlayer(owner)
        if not ability.canTarget(target):
            raise ValueError("Fixed turn-control witness selected an illegal target")
        paid = [_resolve(java, game, ability, owner, target)]
    return int(card.getId()), paid


def _prepare(java, case):
    game = _pod(java)
    kind = case.split("_")[0]
    target = 3 if kind == "overlap" else 0 if case == "self" else 1
    identity, paid = _schedule(java, game, kind, 0, target)
    identities = [identity]
    if kind == "overlap":
        _next_turn(java, game)
        _next_turn(java, game)
        identity, costs = _schedule(java, game, "worst", 2, target)
        identities.append(identity)
        paid.extend(costs)
    if kind == "combat":
        _next_turn(java, game)
        if case.endswith("active"):
            _advance(java, game, "COMBAT_DECLARE_ATTACKERS")
    elif case.endswith("active"):
        _next_turn(java, game)
    elif case in {"self", "expired"}:
        for _ in range(4 if case == "self" else 2):
            _next_turn(java, game)
    elif case == "departed":
        _player(java, game, 0).concede()
        _settle(game)
    return game, identities, paid


def _continuation(java, game, identities, case):
    states = []
    if case.startswith("combat"):
        if case.endswith("pending"):
            _advance(java, game, "COMBAT_DECLARE_ATTACKERS")
            states.append(_state(game, identities))
        _advance(java, game, "MAIN2")
        states.append(_state(game, identities))
    else:
        for _ in range(2 if case.endswith("pending") else 1):
            _next_turn(java, game)
            states.append(_state(game, identities))
    return states


def _case(java, case):
    game, identities, paid = _prepare(java, case)
    observations = {"before": _state(game, identities)}
    copied, gaps = _snapshot(java, game)
    observations["copied_before"] = _state(copied, identities)
    observations["checkpoints"] = _continuation(java, copied, identities, case)
    observations["source"] = _state(game, identities)
    observations["source_checkpoints"] = _continuation(java, game, identities, case)
    return {"case": case, "paid": paid, **observations, "unmapped": gaps}


def main() -> None:
    """Record thirteen paid witnesses using the operator-selected pinned engine."""
    java, engine = initialize_test_engine()
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "control-turn.json",
        {"engine": engine, "cases": [_case(java, case) for case in CASES]},
    )


if __name__ == "__main__":
    main()
