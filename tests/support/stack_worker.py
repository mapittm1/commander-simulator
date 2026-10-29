"""Fixed paid stack witnesses; no uploaded code or arbitrary card programs."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Test-only checkout bootstrap; native fixture names are fixed below.
# pylint: disable=wrong-import-position
from commander.engine.interactions.fixtures import _card
from commander.engine.interactions.suite import write_bounded_json
from tests.support.native_worker import initialize_test_engine
from tests.support.starting_life_worker import _pod


def _add(java, name, owner, zone):
    """Add a named fixture card through the shared native factory."""
    return _card(java, {"engine_name": name}, owner, zone)


def _pay(java, game, ability, owner):
    """Require ordinary casting admission and native cost payment."""
    ability.setActivatingPlayer(owner)
    if not ability.canPlay():
        raise ValueError("Fixed stack witness failed normal admission")
    if not java.JClass("forge.ai.ComputerUtil").playStack(ability, owner, game):
        raise ValueError("Fixed stack witness failed payment")


def _observe(java, game, owner, target):
    """Resolve only the copied stack and retain source independence evidence."""
    snapshot = (
        java.JClass("forge.ai.simulation.GameCopier")(game).withStack(True).makeCopy()
    )
    stack = snapshot.getStack()
    entry = stack.peekAbility() if not stack.isEmpty() else None
    state = {
        "entries": stack.size(),
        "paid": len(entry.getPayingMana()) if entry else None,
        "x": (
            int(entry.getXManaCostPaid())
            if entry and entry.getXManaCostPaid() is not None
            else None
        ),
        "kicked": bool(entry.isKicked()) if entry else None,
        "caster_world": (
            bool(entry.getActivatingPlayer().getGame() == snapshot) if entry else None
        ),
        "mana_world": (
            all(
                mana.getSourceCard().getGame() == snapshot
                and mana.getPlayer().getGame() == snapshot
                for mana in entry.getPayingMana()
            )
            if entry
            else None
        ),
    }
    while not stack.isEmpty():
        stack.resolveStack()
    state.update(
        owner_life=int(snapshot.getPlayer(owner.getId()).getLife()),
        target_life=int(snapshot.getPlayer(target.getId()).getLife()),
        source_owner_life=int(owner.getLife()),
        source_target_life=int(target.getLife()),
        source_entries=game.getStack().size(),
    )
    return state


def _spell(java, name, mana, *, x=None, kicker=False):
    """Pay real mana for a damage spell before copying its pending stack entry."""
    game, owner = _pod(java, 40)
    target = game.getPlayers().get(java.JInt(1))
    zone = java.JClass("forge.game.zone.ZoneType")
    for _ in range(mana):
        _add(java, "Mountain", owner, zone.Battlefield)
    ability = _add(java, name, owner, zone.Hand).getFirstSpellAbility()
    ability.setActivatingPlayer(owner)
    if x is not None:
        ability.setXManaCostPaid(java.JInt(x))
    if kicker:
        utility = java.JClass("forge.game.GameActionUtil")
        costs = java.JClass("java.util.ArrayList")()
        for cost in utility.getOptionalCostValues(ability):
            if str(cost.getType()) == "Kicker1":
                costs.add(cost)
        if len(costs) != 1:
            raise ValueError("Expected one native kicker option")
        ability = utility.addOptionalCosts(ability, costs)
    ability.getTargets().add(target)
    _pay(java, game, ability, owner)
    return _observe(java, game, owner, target)


def _activated(java):
    """Preserve an actual tap-paid activated ability."""
    game, owner = _pod(java, 40)
    target = game.getPlayers().get(java.JInt(1))
    zone = java.JClass("forge.game.zone.ZoneType")
    pinger = _add(java, "Prodigal Pyromancer", owner, zone.Battlefield)
    pinger.setSickness(False)
    game.getAction().checkStateEffects(True)
    ability = next(
        ability
        for ability in pinger.getSpellAbilities()
        if ability.isActivatedAbility() and str(ability.getApi()) == "DealDamage"
    )
    ability.getTargets().add(target)
    _pay(java, game, ability, owner)
    state = _observe(java, game, owner, target)
    state["tap_paid"] = bool(pinger.isTapped())
    return state


def _trigger(java):
    """Copy an admitted Soul Warden trigger after a mana-paid creature resolves."""
    game, owner = _pod(java, 40)
    target = game.getPlayers().get(java.JInt(1))
    zone = java.JClass("forge.game.zone.ZoneType")
    _add(java, "Soul Warden", owner, zone.Battlefield)
    for _ in range(2):
        _add(java, "Forest", owner, zone.Battlefield)
    game.getAction().checkStateEffects(True)
    ability = _add(java, "Grizzly Bears", owner, zone.Hand).getFirstSpellAbility()
    _pay(java, game, ability, owner)
    game.getStack().resolveStack()
    game.getAction().checkStateEffects(True)
    game.getTriggerHandler().runWaitingTriggers()
    game.getStack().addAllTriggeredAbilitiesToStack()
    if game.getStack().isEmpty() or not game.getStack().peekAbility().isTrigger():
        raise ValueError("Expected admitted Soul Warden trigger")
    return _observe(java, game, owner, target)


def main() -> None:
    """Write bounded packaged-runtime receipts for five independent paid cases."""
    run = Path(sys.argv[1]).resolve()
    java, engine = initialize_test_engine()
    write_bounded_json(
        run / "stack-state.json",
        {
            "engine": engine,
            "cases": {
                "bolt": _spell(java, "Lightning Bolt", 1),
                "x": _spell(java, "Fireball", 4, x=3),
                "kicker": _spell(java, "Burst Lightning", 5, kicker=True),
                "activated": _activated(java),
                "trigger": _trigger(java),
            },
        },
    )


if __name__ == "__main__":
    main()
