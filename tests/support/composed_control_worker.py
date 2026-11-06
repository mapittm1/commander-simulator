"""Fixed composed-control choices, native payments and preserved cast provenance."""

import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted witnesses; deck uploads cannot select executable programs.
# pylint: disable=wrong-import-position
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.interactions.suite import write_bounded_json
from tests.support.decision_worker import _ability, _state
from tests.support.native_worker import initialize_test_engine
from tests.support.primary_control_worker import _world_fingerprint
from tests.support.starting_life_worker import _pod
from tests.support.turn_schedule_worker import _seat
from tests.support.usage_worker import _add, _find

SOURCES = {
    "polarity": "Invert Polarity",
    "substitution": "Sudden Substitution",
    "chimera": "Perplexing Chimera",
}
MODES = ("heuristic", "hybrid", "full")


@dataclass(frozen=True)
class _Board:
    java: Any
    game: Any
    actor: Any
    threat: Any
    host: Any
    offered: Any
    setup_paid: int


def _settle(game):
    """Admit triggers after every resolution, including the final spell's ETB."""
    for _ in range(40):
        game.getAction().checkStateEffects(True)
        game.getTriggerHandler().runWaitingTriggers()
        game.getStack().addAllTriggeredAbilitiesToStack()
        if game.isGameOver() or game.getStack().isEmpty():
            return
        game.getStack().resolveStack()
    raise AssertionError("Fixed composed-control stack did not settle")


def _paid(java, game, action, actor):
    if not java.JClass("forge.ai.ComputerUtil").playStack(action, actor, game):
        raise AssertionError("Fixed native spell payment failed")
    return int(game.getStack().peekAbility().getPayingMana().size())


def _mode(java, actor, mode):
    options = java.JClass("forge.ai.AIOption")
    simulation_modes = {
        "heuristic": None,
        "hybrid": options.USE_HYBRID_SIMULATION,
        "full": options.USE_FULL_SIMULATION,
    }
    actor.getController().getAi().setUseSimulation(simulation_modes[mode])


def _lands(java, game, zones):
    for player in game.getPlayers():
        for _ in range(8):
            for name, zone in (
                ("Island", zones.Battlefield),
                ("Mountain", zones.Battlefield),
                ("Forest", zones.Library),
            ):
                _add(java, name, player, zone)


def _prepare(java, kind, mode):
    game, actor = _pod(java, 40)
    zones = java.JClass("forge.game.zone.ZoneType")
    enemy = game.getPlayers().get(java.JInt(1))
    _lands(java, game, zones)
    _mode(java, actor, mode)
    actor.setLife(2, None)
    host = _add(java, SOURCES[kind], actor, zones.Hand)
    offered = (
        _add(java, "Ornithopter", actor, zones.Battlefield)
        if kind == "substitution"
        else None
    )
    setup_paid = 0
    if kind == "chimera":
        game.getPhaseHandler().devModeSet(
            java.JClass("forge.game.phase.PhaseType").MAIN1, actor, java.JInt(1)
        )
        action = host.getFirstSpellAbility()
        action.setActivatingPlayer(actor)
        setup_paid = _paid(java, game, action, actor)
        _settle(game)
        host = _find(game, host.getId())
    game.getPhaseHandler().devModeSet(
        java.JClass("forge.game.phase.PhaseType").MAIN2, enemy, java.JInt(1)
    )
    threat = _add(java, "Shock", enemy, zones.Hand).getFirstSpellAbility()
    threat.setActivatingPlayer(enemy)
    threat.getTargets().add(actor)
    if _paid(java, game, threat, enemy) != 1:
        raise AssertionError("Fixed opposing Shock must cost one mana")
    return _Board(
        java,
        game,
        actor,
        game.getStack().peekAbility(),
        host,
        offered,
        setup_paid,
    )


def _choose(board, kind):
    identities = [int(board.host.getId()), int(board.threat.getHostCard().getId())]
    if board.offered is not None:
        identities.append(int(board.offered.getId()))
    if kind == "chimera":
        board.game.getAction().checkStateEffects(True)
        board.game.getTriggerHandler().runWaitingTriggers()
        board.game.getStack().addAllTriggeredAbilitiesToStack()
        return {
            "decision_path": "native_optional_trigger",
            "offered_trigger": _ability(
                board.game, board.game.getStack().peekAbility()
            ),
        }, None
    before = _state(board.java, board.game, identities)
    fingerprint = _world_fingerprint(board.game)
    randoms = board.java.JClass("forge.util.MyRandom")
    provider = randoms.getRandom()
    choices = board.actor.getController().chooseSpellAbilityToPlay()
    row = {
        "decision_path": "spell_selection",
        "before": before,
        "after_choice": _state(board.java, board.game, identities),
        "world_unchanged": fingerprint == _world_fingerprint(board.game),
        "rng_restored": randoms.getRandom() == provider,
        "selected": None,
    }
    if choices is None or choices.isEmpty():
        return row, None
    action = choices.get(board.java.JInt(0))
    row["selected"] = _ability(board.game, action)
    row["sub_target_ids"] = (
        []
        if kind != "substitution"
        else [
            int(card.getId())
            for card in action.getSubAbility().getTargets().getTargetCards()
        ]
    )
    return row, action


def _resolve_top(board, seed):
    randoms = board.java.JClass("forge.util.MyRandom")
    provider = randoms.getRandom()
    try:
        if seed is not None:
            randoms.setRandom(
                board.java.JClass("java.util.Random")(board.java.JLong(seed))
            )
        board.game.getStack().resolveStack()
    finally:
        randoms.setRandom(provider)


def _outcome(board):
    host = _find(board.game, board.host.getId())
    row = {
        "controlled_stack": [
            dict(
                _ability(board.game, entry.getSpellAbility()),
                cards=[
                    int(card.getId())
                    for card in entry.getSpellAbility().getTargets().getTargetCards()
                ],
            )
            for entry in board.game.getStack()
        ],
        "host_owner": _seat(board.game, host.getOwner()),
        "host_controller": _seat(board.game, host.getController()),
        "cast_counts": [
            int(player.getSpellsCastThisTurn())
            for player in board.game.getRegisteredPlayers()
        ],
        "history": [
            _ability(board.game, action)
            for action in board.game.getStack().getSpellsCastThisTurn()
        ],
    }
    if board.offered is not None:
        offered = _find(board.game, board.offered.getId())
        row["offered_owner"] = _seat(board.game, offered.getOwner())
        row["offered_controller"] = _seat(board.game, offered.getController())
    _settle(board.game)
    row["life"] = [
        int(player.getLife()) for player in board.game.getRegisteredPlayers()
    ]
    if board.offered is not None:
        row["offered_final_zone"] = str(
            _find(board.game, board.offered.getId()).getZone().getZoneType()
        )
    row["damage_to_creatures"] = sum(
        int(amount)
        for amount in board.game.getDamageDoneThisTurn(
            None,
            False,
            "Card",
            "Creature",
            board.threat.getHostCard(),
            board.actor,
            None,
        )
    )
    row["actor_lost"] = bool(board.actor.hasLost())
    return row


def _case(java, kind, mode, seed):
    board = _prepare(java, kind, mode)
    row, action = _choose(board, kind)
    row.update(
        kind=kind,
        mode=mode,
        physical_seed=seed,
        source=SOURCES[kind],
        setup_paid=board.setup_paid,
        host_id=int(board.host.getId()),
        threat_id=int(board.threat.getHostCard().getId()),
        offered_id=None if board.offered is None else int(board.offered.getId()),
    )
    if kind != "chimera" and action is None:
        return row
    row["paid_mana"] = (
        0 if kind == "chimera" else _paid(java, board.game, action, board.actor)
    )
    _resolve_top(board, seed)
    row.update(_outcome(board))
    return row


def _history_board(java):
    game, actor = _pod(java, 40)
    zones = java.JClass("forge.game.zone.ZoneType")
    _lands(java, game, zones)
    for player in game.getPlayers():
        for _ in range(8):
            _add(java, "Forest", player, zones.Battlefield)
            _add(java, "Plains", player, zones.Battlefield)
    enemy = game.getPlayers().get(java.JInt(1))
    game.getPhaseHandler().devModeSet(
        java.JClass("forge.game.phase.PhaseType").MAIN2, enemy, java.JInt(1)
    )
    return game, actor, enemy, zones


def _zacama(java, stolen):
    game, actor, enemy, zones = _history_board(java)
    action = _add(
        java, "Zacama, Primal Calamity", enemy, zones.Hand
    ).getFirstSpellAbility()
    action.setActivatingPlayer(enemy)
    paid_source = _paid(java, game, action, enemy)
    identity = int(game.getStack().peekAbility().getHostCard().getId())
    paid_response = 0
    if stolen:
        action = _add(java, "Aethersnatch", actor, zones.Hand).getFirstSpellAbility()
        action.setActivatingPlayer(actor)
        action.getTargets().add(game.getStack().peekAbility())
        paid_response = _paid(java, game, action, actor)
        game.getStack().resolveStack()
    _settle(game)
    card = _find(game, identity)
    return {
        "kind": "zacama_stolen" if stolen else "zacama_ordinary",
        "paid_source": paid_source,
        "paid_response": paid_response,
        "owner": _seat(game, card.getOwner()),
        "controller": _seat(game, card.getController()),
        "cast_caster": _seat(game, card.getCastSA().getActivatingPlayer()),
        "actor_tapped": sum(bool(land.isTapped()) for land in actor.getLandsInPlay()),
        "caster_tapped": sum(bool(land.isTapped()) for land in enemy.getLandsInPlay()),
        "cast_counts": [
            int(player.getSpellsCastThisTurn())
            for player in game.getRegisteredPlayers()
        ],
        "life": [int(player.getLife()) for player in game.getRegisteredPlayers()],
    }


def main():
    """Record fourteen seeded/paid packaged cases in four bounded receipt parts."""
    java, engine = initialize_test_engine()
    run = Path(sys.argv[1])
    parts = []
    for mode in MODES:
        rows = [_case(java, "polarity", mode, seed) for seed in (0, 4096)]
        rows.extend(
            _case(java, kind, mode, None) for kind in ("substitution", "chimera")
        )
        name = "composed-control-" + mode + ".json"
        write_bounded_json(run / name, {"cases": rows})
        parts.append(
            {"name": name, "sha256": file_hash(run / name), "cases": len(rows)}
        )
    name = "composed-control-provenance.json"
    rows = [_zacama(java, stolen) for stolen in (False, True)]
    write_bounded_json(run / name, {"cases": rows})
    parts.append({"name": name, "sha256": file_hash(run / name), "cases": len(rows)})
    settings = read_settings()
    write_bounded_json(
        run / "composed-control.json",
        {
            "engine": engine,
            "parts": parts,
            "default_profile_sha256": file_hash(settings.home / "res/ai/Default.ai"),
        },
    )


if __name__ == "__main__":
    main()
