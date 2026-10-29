"""Fixed packaged-runtime witnesses for damage provenance and paid cast controls."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed test-only checkout bootstrap; no uploaded modules.
# pylint: disable=wrong-import-position
from commander.engine.interactions.fixtures import _card, _new_game
from commander.engine.interactions.suite import write_bounded_json
from tests.support.native_worker import initialize_test_engine


def _game(java):
    game = _new_game(java)
    for player in game.getPlayers():
        player.setLife(40, None)
    return game


def _add(java, name, player, zone):
    return _card(java, {"engine_name": name}, player, zone)


def _control(card, player, zone):
    card.getController().getZone(zone.Battlefield).remove(card)
    player.getZone(zone.Battlefield).add(card)
    card.setController(player, card.getGame().getNextTimestamp())


def _damage_source(java, game, scenario):
    owner = game.getPlayers().get(java.JInt(0))
    opponent = game.getPlayers().get(java.JInt(1))
    zone = java.JClass("forge.game.zone.ZoneType")
    name = (
        "Grizzly Bears"
        if scenario in {"ordinary", "commander"}
        else "Changeling Outcast"
    )
    source = _add(java, name, owner, zone.Battlefield)
    if scenario == "commander":
        source.setCommander(True)
    if scenario == "stolenBefore":
        _control(source, opponent, zone)
    target = (
        _add(java, "Colossal Dreadmaw", opponent, zone.Battlefield)
        if scenario == "creatureTarget"
        else opponent
    )
    game.getAction().checkStaticAbilities(False)
    damage = java.JClass("forge.game.card.CardDamageTable")()
    damage.put(source, target, java.JInt(1))
    game.getAction().dealDamage(
        scenario != "noncombat",
        damage,
        java.JClass("forge.game.card.CardDamageTable")(),
        java.JClass("forge.game.GameEntityCounterTable")(),
        None,
    )
    if scenario == "stolenAfter":
        _control(source, opponent, zone)
    if scenario == "stolenBefore":
        _control(source, owner, zone)
    return owner


def _find(game, zone, identity):
    return next(card for card in game.getCardsIn(zone) if card.getId() == identity)


def _pay(java, ability, owner, game):
    if not ability.canPlay():
        raise ValueError("Fixed paid witness failed normal native cast admission")
    if not java.JClass("forge.ai.ComputerUtil").playStack(ability, owner, game):
        raise ValueError("Fixed paid witness failed actual native payment")


def _resolve(game):
    game.getStack().resolveStack()
    game.getAction().checkStateEffects(True)


def _freerunning(java, scenario):
    game = _game(java)
    owner = _damage_source(java, game, scenario)
    zone = java.JClass("forge.game.zone.ZoneType")
    original_window = bool(owner.hasFreerunning())
    for _ in range(2):
        _add(java, "Island", owner, zone.Battlefield)
    for _ in range(3):
        _add(java, "Forest", owner, zone.Library)
    vision = _add(java, "Eagle Vision", owner, zone.Hand)
    snapshot = java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()
    copied_owner = snapshot.getPlayer(owner.getId())
    copied = _find(snapshot, zone.Hand, vision.getId())
    ability = next(item for item in copied.getSpellAbilities() if item.isFreerunning())
    ability.setActivatingPlayer(copied_owner)
    allowed = bool(ability.canPlay())
    if allowed:
        _pay(java, ability, copied_owner, snapshot)
        _resolve(snapshot)
    return {
        "scenario": scenario,
        "original_window": original_window,
        "copied_window": bool(copied_owner.hasFreerunning()),
        "allowed": allowed,
        "mana_paid": len(ability.getPayingMana()),
        "copied_hand": len(copied_owner.getCardsIn(zone.Hand)),
        "spell_in_graveyard": any(
            card.getId() == vision.getId()
            for card in snapshot.getCardsIn(zone.Graveyard)
        ),
        "source_hand": len(owner.getCardsIn(zone.Hand)),
        "source_library": len(owner.getCardsIn(zone.Library)),
    }


def _register(java, source, target, amount):
    source.getDamageHistory().registerDamage(
        java.JInt(amount), True, source, target, java.JClass("java.util.HashMap")()
    )


def _amount(game, source, valid_source, valid_target):
    return sum(
        int(amount)
        for amount in game.getDamageDoneThisTurn(
            None,
            False,
            valid_source,
            valid_target,
            source,
            source.getController(),
            None,
        )
    )


def _damage_queries(java):
    game = _game(java)
    owner = game.getPlayers().get(java.JInt(0))
    target = game.getPlayers().get(java.JInt(1))
    zone = java.JClass("forge.game.zone.ZoneType")
    source = _add(java, "Grizzly Bears", owner, zone.Battlefield)
    _register(java, source, target, 3)
    _register(java, source, owner, 3)
    snapshot = java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()
    copied = _find(snapshot, zone.Battlefield, source.getId())
    packets = list(copied.getDamageHistory().getAllDmgInstances())
    records = [snapshot.getDamageLKI(packet) for packet in packets]
    result = {
        "opponent": _amount(snapshot, copied, "Creature", "Player.Opponent"),
        "self": _amount(snapshot, copied, "Creature", "Player.You"),
        "invalid_source": _amount(snapshot, copied, "Artifact", "Player"),
        "invalid_target": _amount(snapshot, copied, "Creature", "Creature"),
        "received_from_source": int(
            snapshot.getPlayer(target.getId()).getAssignedDamage(None, copied)
        ),
        "maximum": int(snapshot.getSingleMaxDamageDoneThisTurn()),
        "distinct_targets": len({int(record.getRight().getId()) for record in records}),
        "mapped_records": all(
            record.getLeft().getGame() == snapshot
            and record.getRight().getGame() == snapshot
            for record in records
        ),
    }
    _register(java, copied, snapshot.getPlayer(target.getId()), 2)
    result["after_copy_damage"] = _amount(
        snapshot, copied, "Creature", "Player.Opponent"
    )
    result["source_unchanged"] = _amount(game, source, "Creature", "Player.Opponent")
    _register(java, source, target, 4)
    result["after_source_damage"] = _amount(game, source, "Creature", "Player.Opponent")
    result["copy_unchanged"] = _amount(snapshot, copied, "Creature", "Player.Opponent")
    return result


def _last_turn(java):
    game = _game(java)
    owner = game.getPlayers().get(java.JInt(0))
    target = game.getPlayers().get(java.JInt(1))
    zone = java.JClass("forge.game.zone.ZoneType")
    source = _add(java, "Grizzly Bears", owner, zone.Battlefield)
    _register(java, source, target, 3)
    game.onCleanupPhase()
    snapshot = java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()
    copied = _find(snapshot, zone.Battlefield, source.getId())
    copied_target = snapshot.getPlayer(target.getId())
    result = {
        "previous": int(copied_target.getAssignedDamage(None, copied, True)),
        "current": int(copied_target.getAssignedDamage(None, copied)),
        "maximum": int(snapshot.getSingleMaxDamageDoneThisTurn()),
    }
    snapshot.onCleanupPhase()
    result["expired"] = int(copied_target.getAssignedDamage(None, copied, True))
    result["source_previous"] = int(target.getAssignedDamage(None, source, True))
    return result


def _bloodthirst(java, damage_opponent):
    game = _game(java)
    owner = game.getPlayers().get(java.JInt(0))
    opponent = game.getPlayers().get(java.JInt(1))
    zone = java.JClass("forge.game.zone.ZoneType")
    for _ in range(5):
        _add(java, "Mountain", owner, zone.Battlefield)
    bolt = _add(java, "Lightning Bolt", owner, zone.Hand).getFirstSpellAbility()
    bolt.setActivatingPlayer(owner)
    bolt.getTargets().add(opponent if damage_opponent else owner)
    _pay(java, bolt, owner, game)
    _resolve(game)
    minotaur = _add(java, "Gorehorn Minotaurs", owner, zone.Hand)
    ability = minotaur.getFirstSpellAbility()
    ability.setActivatingPlayer(owner)
    _pay(java, ability, owner, game)
    snapshot = (
        java.JClass("forge.ai.simulation.GameCopier")(game).withStack(True).makeCopy()
    )
    _resolve(snapshot)
    resolved = _find(snapshot, zone.Battlefield, minotaur.getId())
    return {
        "opponent_damaged": damage_opponent,
        "bolt_mana": len(bolt.getPayingMana()),
        "creature_mana": len(ability.getPayingMana()),
        "power": int(resolved.getNetPower()),
        "counters": int(
            resolved.getCounters(java.JClass("forge.game.card.CounterEnumType").P1P1)
        ),
        "source_stack": int(game.getStack().size()),
    }


def main():
    """Record fixed Commander-native witnesses; never accept uploaded definitions."""
    run = Path(sys.argv[1]).resolve()
    java, engine = initialize_test_engine()
    scenarios = (
        "assassin",
        "commander",
        "ordinary",
        "noncombat",
        "creatureTarget",
        "stolenAfter",
        "stolenBefore",
    )
    receipt = {
        "engine": engine,
        "freerunning": [_freerunning(java, case) for case in scenarios],
        "queries": _damage_queries(java),
        "previous_turn": _last_turn(java),
        "bloodthirst": [_bloodthirst(java, case) for case in (True, False)],
    }
    write_bounded_json(run / "damage-state.json", receipt)


if __name__ == "__main__":
    main()
