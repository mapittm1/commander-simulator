"""Fixed paid spell-control choices against the verified packaged runtime."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed operator-owned witnesses; uploaded decks cannot select executable code.
# pylint: disable=wrong-import-position
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.interactions.suite import write_bounded_json
from tests.support.decision_worker import _ability, _state
from tests.support.native_worker import initialize_test_engine
from tests.support.primary_control_worker import _world_fingerprint
from tests.support.starting_life_worker import _pod
from tests.support.usage_worker import _add, _settle

SOURCES = ("Aethersnatch", "Commandeer", "Chef's Kiss")
MODES = ("heuristic", "hybrid", "full")


def _prepare(java, source, alternate):
    """Create a partial four-player pod with a real paid opposing threat."""
    game, actor = _pod(java, 40)
    zones = java.JClass("forge.game.zone.ZoneType")
    enemy = game.getPlayers().get(java.JInt(1))
    game.getPhaseHandler().devModeSet(
        java.JClass("forge.game.phase.PhaseType").MAIN2, enemy, java.JInt(1)
    )
    for player in game.getPlayers():
        for _ in range(8):
            _add(java, "Island", player, zones.Battlefield)
            _add(java, "Mountain", player, zones.Battlefield)
            _add(java, "Forest", player, zones.Library)
    actor.setLife(2, None)
    incoming = _add(java, "Shock", enemy, zones.Hand).getFirstSpellAbility()
    incoming.setActivatingPlayer(enemy)
    incoming.getTargets().add(actor)
    computer = java.JClass("forge.ai.ComputerUtil")
    if not computer.playStack(incoming, enemy, game):
        raise AssertionError("Fixed opposing Shock was not paid")
    incoming = game.getStack().peekAbility()
    incoming_id = int(incoming.getHostCard().getId())
    fodder = []
    if alternate:
        for land in actor.getLandsInPlay():
            land.setTapped(True)
        fodder = [_add(java, "Divination", actor, zones.Hand) for _ in range(2)]
    host = _add(java, source, actor, zones.Hand)
    return game, actor, incoming_id, fodder, host


def _choose(java, board, mode):
    """Observe a decision without allowing planning to mutate the live world."""
    game, actor, incoming_id, fodder, host = board
    identities = [
        incoming_id,
        int(host.getId()),
        *(int(card.getId()) for card in fodder),
    ]
    actor.getController().getAi().setUseSimulation(
        None
        if mode == "heuristic"
        else (
            java.JClass("forge.ai.AIOption").USE_HYBRID_SIMULATION
            if mode == "hybrid"
            else java.JClass("forge.ai.AIOption").USE_FULL_SIMULATION
        )
    )
    before = _state(java, game, identities)
    fingerprint = _world_fingerprint(game)
    random = java.JClass("forge.util.MyRandom")
    provider = random.getRandom()
    choices = actor.getController().chooseSpellAbilityToPlay()
    row = {
        "host_id": int(host.getId()),
        "incoming_id": incoming_id,
        "before": before,
        "after_choice": _state(java, game, identities),
        "world_unchanged": fingerprint == _world_fingerprint(game),
        "rng_restored": random.getRandom() == provider,
        "selected": None,
    }
    if choices is None or choices.isEmpty():
        return row, None
    return row, choices.get(java.JInt(0))


def _case(java, source, mode, alternate):
    game, actor, incoming_id, fodder, host = _prepare(java, source, alternate)
    row, response = _choose(java, (game, actor, incoming_id, fodder, host), mode)
    row.update(source=source, mode=mode, alternate=alternate)
    if response is None:
        return row
    computer = java.JClass("forge.ai.ComputerUtil")
    row["selected"] = _ability(game, response)
    row["paid_successfully"] = bool(computer.playStack(response, actor, game))
    if not row["paid_successfully"]:
        return row
    row["paid_mana"] = int(game.getStack().peekAbility().getPayingMana().size())
    row["fodder_zones"] = [str(card.getZone().getZoneType()) for card in fodder]
    game.getStack().resolveStack()
    row["controlled_stack"] = [
        _ability(game, entry.getSpellAbility()) for entry in game.getStack()
    ]
    _settle(game)
    row["life"] = [int(player.getLife()) for player in game.getRegisteredPlayers()]
    row["actor_lost"] = bool(actor.hasLost())
    return row


def main():
    """Write bounded receipts for twelve real choices and native outcomes."""
    java, engine = initialize_test_engine()
    run = Path(sys.argv[1])
    parts = []
    for mode in MODES:
        name = "spell-control-" + mode + ".json"
        rows = [_case(java, source, mode, False) for source in SOURCES]
        rows.append(_case(java, "Commandeer", mode, True))
        write_bounded_json(run / name, {"cases": rows})
        parts.append(
            {"name": name, "sha256": file_hash(run / name), "cases": len(rows)}
        )
    settings = read_settings()
    write_bounded_json(
        run / "spell-control.json",
        {
            "engine": engine,
            "default_profile_sha256": file_hash(settings.home / "res/ai/Default.ai"),
            "parts": parts,
        },
    )


if __name__ == "__main__":
    main()
