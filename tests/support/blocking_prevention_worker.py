"""Paid forced-block requirements and prevention-shield deadlines."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted native fixtures, never uploaded scenario programs.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _postcombat
from tests.support.delayed_worker import _settle, _setup
from tests.support.duration_worker import _snapshot
from tests.support.keyword_expiration_worker import _resolve, _targeted
from tests.support.native_worker import initialize_test_engine
from tests.support.turn_schedule_worker import _advance, _next_turn
from tests.support.usage_worker import _activated, _add, _cast, _find


def _blocking_setup(java, case):
    game, owner, zones = _setup(java, ("Forest",) * 7 + ("Island", "Mountain"))
    _postcombat(java, game, owner)
    defender = game.getPlayers().get(java.JInt(1))
    if case == "tusker":
        attacker, paid = _cast(java, game, "Avalanche Tusker", owner, zones)
        for _ in range(4):
            _next_turn(java, game)
        blocker = _add(java, "Wall of Stone", defender, zones.Battlefield)
        phases = java.JClass("forge.game.phase.PhaseType")
        game.getPhaseHandler().devModeSet(
            phases.COMBAT_DECLARE_ATTACKERS, owner, java.JInt(5)
        )
        game.getPhaseHandler().setCombat(java.JClass("forge.game.combat.Combat")(owner))
        game.getCombat().addAttacker(attacker, defender)
        java.JClass("forge.game.combat.CombatUtil").checkDeclaredAttacker(
            game, attacker, game.getCombat(), True
        )
        _settle(game)
        paid = [paid]
    elif case == "sled":
        attacker, mana = _cast(java, game, "Auriok Siege Sled", owner, zones)
        blocker = _add(java, "Bronze Sable", defender, zones.Battlefield)
        paid = [
            mana,
            _resolve(
                java, game, _activated(attacker, "MustBlock", owner), owner, blocker
            ),
        ]
    else:
        attacker = _add(java, "Grizzly Bears", owner, zones.Battlefield)
        blocker = _add(java, "Runeclaw Bear", defender, zones.Battlefield)
        card = _add(
            java, "Hunt Down" if case == "hunt" else "Monstrous Step", owner, zones.Hand
        )
        ability = card.getFirstSpellAbility()
        ability.getTargets().add(attacker)
        ability.getSubAbility().getTargets().add(blocker)
        paid = [_resolve(java, game, ability, owner)]
    return game, attacker, blocker, paid


def _requirements(game, blocker):
    return {
        "attackers": [int(card.getId()) for card in blocker.getMustBlockCards()],
        "mapped_world": all(
            card.getGame() == game for card in blocker.getMustBlockCards()
        ),
    }


def _blocking_deadline(java, game, case):
    if case == "tusker":
        _advance(java, game, "MAIN2")
    else:
        _next_turn(java, game)


def _blocking(java, case):
    game, attacker, blocker, paid = _blocking_setup(java, case)
    before = _requirements(game, blocker)
    copied, gaps = _snapshot(java, game)
    mapped = _find(copied, blocker.getId())
    copied_before = _requirements(copied, mapped)
    _blocking_deadline(java, copied, case)
    after = _requirements(copied, mapped)
    source = _requirements(game, blocker)
    _blocking_deadline(java, game, case)
    return {
        "case": case,
        "paid": paid,
        "attacker": int(attacker.getId()),
        "before": before,
        "copied_before": copied_before,
        "after": after,
        "source": source,
        "source_after": _requirements(game, blocker),
        "unmapped": gaps,
    }


def _shield_setup(java, case):
    game, owner, zones = _setup(java, ("Plains",) * 3 + ("Mountain",) * 4)
    _postcombat(java, game, owner)
    target = (
        _add(java, "Serra Angel", owner, zones.Battlefield)
        if case == "creature"
        else owner
    )
    paid = [_targeted(java, game, "Mending Hands", owner, target)]
    if case == "stacked":
        paid.append(_targeted(java, game, "Mending Hands", owner, target))
    if case == "partial":
        paid.append(_targeted(java, game, "Lightning Bolt", owner, target))
    return game, owner, target, paid


def _shield_state(java, target):
    creature = isinstance(target, java.JClass("forge.game.card.Card"))
    return {
        "shield": int(target.getPreventNextDamageTotalShields()),
        "life": None if creature else int(target.getLife()),
        "damage": int(target.getDamage()) if creature else None,
    }


def _observe_shield(java, game, owner, target):
    observations = {"before": _shield_state(java, target)}
    copied, gaps = _snapshot(java, game)
    copied_owner = copied.getPlayers().get(java.JInt(0))
    mapped = (
        _find(copied, target.getId())
        if isinstance(target, java.JClass("forge.game.card.Card"))
        else copied_owner
    )
    observations["copied_before"] = _shield_state(java, mapped)
    _next_turn(java, copied)
    observations["after"] = _shield_state(java, mapped)
    observations["source"] = _shield_state(java, target)
    costs = {
        "copy_bolt": _targeted(java, copied, "Lightning Bolt", copied_owner, mapped)
    }
    observations["copy_damage"] = _shield_state(java, mapped)
    costs["source_bolts"] = [_targeted(java, game, "Lightning Bolt", owner, target)]
    observations["source_damage"] = _shield_state(java, target)
    _next_turn(java, game)
    observations["source_expired"] = _shield_state(java, target)
    costs["source_bolts"].append(_targeted(java, game, "Lightning Bolt", owner, target))
    observations["source_final"] = _shield_state(java, target)
    return {**observations, "damage_costs": costs, "unmapped": gaps}


def _shield(java, case):
    game, owner, target, paid = _shield_setup(java, case)
    return {"case": case, "paid": paid, **_observe_shield(java, game, owner, target)}


def main() -> None:
    """Retain independent copied-world requirements, quotas and actual damage outcomes."""
    java, engine = initialize_test_engine()
    cases = [_blocking(java, case) for case in ("hunt", "step", "sled", "tusker")]
    cases.extend(
        _shield(java, case) for case in ("player", "creature", "partial", "stacked")
    )
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "blocking-prevention.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
