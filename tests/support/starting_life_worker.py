"""Fixed native witnesses for starting-life baselines and real paid life-gain spells."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed test-only checkout bootstrap; no uploaded modules.
# pylint: disable=wrong-import-position
from commander.engine.interactions.fixtures import _card
from commander.engine.interactions.suite import write_bounded_json
from tests.support.native_worker import initialize_test_engine


def _pod(java, baseline):
    players = java.JClass("java.util.ArrayList")()
    for index in range(4):
        registered = java.JClass("forge.game.player.RegisteredPlayer").forCommander(
            java.JClass("forge.deck.Deck")(f"StartingLife{index}")
        )
        registered.setStartingLife(baseline)
        registered.setPlayer(
            java.JClass("forge.player.GamePlayerUtil").createAiPlayer(
                f"StartingLife{index}", index, ""
            )
        )
        players.add(registered)
    rules = java.JClass("forge.game.GameRules")(
        java.JClass("forge.game.GameType").Commander
    )
    game = java.JClass("forge.game.Match")(
        rules, players, "Starting-life fixture"
    ).createGame()
    game.setNoGUIUser()
    game.setAge(java.JClass("forge.game.GameStage").Play)
    owner = game.getPlayers().get(java.JInt(0))
    game.getPhaseHandler().devModeSet(
        java.JClass("forge.game.phase.PhaseType").MAIN1, owner, java.JInt(1)
    )
    zone = java.JClass("forge.game.zone.ZoneType")
    for player in game.getPlayers():
        _card(java, {"engine_name": "Forest"}, player, zone.Library)
    return game, owner


def _copy(java, game):
    return java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()


def _find(game, zone, identity):
    return next(card for card in game.getCardsIn(zone) if card.getId() == identity)


def _life(player):
    return {
        "starting": int(player.getStartingLife()),
        "registered": int(player.getRegisteredPlayer().getStartingLife()),
        "current": int(player.getLife()),
    }


def _baseline(java, baseline):
    game, owner = _pod(java, baseline)
    owner.setLife(baseline - 3, None)
    snapshot = _copy(java, game)
    return {
        "baseline": baseline,
        "before": [_life(player) for player in game.getRegisteredPlayers()],
        "after": [_life(player) for player in snapshot.getRegisteredPlayers()],
    }


def _threshold(java, baseline, life):
    game, owner = _pod(java, baseline)
    owner.setLife(life, None)
    zone = java.JClass("forge.game.zone.ZoneType")
    valkyrie = _card(
        java, {"engine_name": "Righteous Valkyrie"}, owner, zone.Battlefield
    )
    bear = _card(java, {"engine_name": "Grizzly Bears"}, owner, zone.Battlefield)
    game.getAction().checkStateEffects(True)
    before = [int(valkyrie.getNetPower()), int(bear.getNetPower())]
    snapshot = _copy(java, game)
    return {
        "baseline": baseline,
        "life": life,
        "before": before,
        "after": [
            int(_find(snapshot, zone.Battlefield, card.getId()).getNetPower())
            for card in (valkyrie, bear)
        ],
        "copied_life": _life(snapshot.getPlayer(owner.getId())),
    }


def _paid(java, name, mana):
    game, owner = _pod(java, 40)
    zone = java.JClass("forge.game.zone.ZoneType")
    valkyrie = _card(
        java, {"engine_name": "Righteous Valkyrie"}, owner, zone.Battlefield
    )
    bear = _card(java, {"engine_name": "Grizzly Bears"}, owner, zone.Battlefield)
    for _ in range(mana):
        _card(java, {"engine_name": "Plains"}, owner, zone.Battlefield)
    spell = _card(java, {"engine_name": name}, owner, zone.Hand)
    game.getAction().checkStateEffects(True)
    snapshot = _copy(java, game)
    mapped = snapshot.getPlayer(owner.getId())
    copied_valkyrie = _find(snapshot, zone.Battlefield, valkyrie.getId())
    before_power = int(copied_valkyrie.getNetPower())
    ability = _find(snapshot, zone.Hand, spell.getId()).getFirstSpellAbility()
    ability.setActivatingPlayer(mapped)
    if not ability.canPlay():
        raise ValueError("Fixed life-gain witness failed normal cast admission")
    if not java.JClass("forge.ai.ComputerUtil").playStack(ability, mapped, snapshot):
        raise ValueError("Fixed life-gain witness failed native payment")
    snapshot.getStack().resolveStack()
    snapshot.getAction().checkStateEffects(True)
    return {
        "spell": name,
        "mana_paid": len(ability.getPayingMana()),
        "before_power": before_power,
        "after_power": int(copied_valkyrie.getNetPower()),
        "bear_power": int(
            _find(snapshot, zone.Battlefield, bear.getId()).getNetPower()
        ),
        "life": _life(mapped),
        "in_graveyard": any(
            card.getId() == spell.getId()
            for card in snapshot.getCardsIn(zone.Graveyard)
        ),
        "source_life": _life(owner),
        "source_bear_power": int(bear.getNetPower()),
    }


def _eliminated(java):
    game, _ = _pod(java, 40)
    lost = game.getPlayers().get(java.JInt(1))
    lost.setLife(0, None)
    game.getAction().checkStateEffects(True)
    if not lost.hasLost():
        raise ValueError("Fixed source player did not lose at zero life")
    again = _copy(java, _copy(java, game))
    mapped = again.getPlayer(lost.getId())
    return {
        "life": _life(mapped),
        "lost": bool(mapped.hasLost()),
        "active": len(again.getPlayers()),
    }


def _override(java):
    game, owner = _pod(java, 40)
    owner.setStartingLife(30)
    owner.setLife(35, None)
    mapped = _copy(java, game).getPlayer(owner.getId())
    return {"before": _life(owner), "after": _life(mapped)}


def main():
    """Record baseline preservation and native card behavior across snapshots."""
    run = Path(sys.argv[1]).resolve()
    java, engine = initialize_test_engine()
    receipt = {
        "engine": engine,
        "baselines": [_baseline(java, baseline) for baseline in (20, 25, 30, 40, 50)],
        "thresholds": [
            _threshold(java, baseline, life)
            for baseline, life in (
                (20, 26),
                (20, 27),
                (40, 27),
                (40, 40),
                (40, 46),
                (40, 47),
                (40, 48),
            )
        ],
        "paid": [
            _paid(java, name, mana)
            for name, mana in (("Angel's Mercy", 4), ("Chaplain's Blessing", 1))
        ],
        "eliminated": _eliminated(java),
        "override": _override(java),
    }
    write_bounded_json(run / "starting-life.json", receipt)


if __name__ == "__main__":
    main()
