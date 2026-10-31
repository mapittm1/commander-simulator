"""Fixed paid animation and Licid reversal witnesses for the native runtime."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Trusted card programs and native paths; uploaded data cannot define these fixtures.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.conditional_worker import _round
from tests.support.delayed_worker import _settle, _setup
from tests.support.duration_worker import _snapshot, _targeted
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.turn_schedule_worker import _next_turn
from tests.support.usage_worker import _activated, _add, _cast, _find

LICIDS = (
    "Calming Licid",
    "Convulsing Licid",
    "Corrupting Licid",
    "Dominating Licid",
    "Enraging Licid",
    "Flanking Licid",
    "Gliding Licid",
    "Leeching Licid",
    "Nurturing Licid",
    "Quickening Licid",
    "Stinging Licid",
    "Tempting Licid",
    "Transmogrifying Licid",
)


def _form(java, card):
    """Read live native characteristics, including characteristics from layered effects."""
    keywords = java.JClass("forge.game.keyword.Keyword")
    return {
        "name": str(card.getName()),
        "creature": bool(card.isCreature()),
        "aura": bool(card.isAura()),
        "land": bool(card.isLand()),
        "artifact": bool(card.isArtifact()),
        "power": int(card.getNetPower()),
        "toughness": int(card.getNetToughness()),
        "flying": bool(card.hasKeyword(keywords.FLYING)),
        "haste": bool(card.hasKeyword(keywords.HASTE)),
    }


def _postcombat(java, game, owner):
    """Start a controlled postcombat-main fixture so automatic combat cannot remove lands."""
    game.getPhaseHandler().devModeSet(
        java.JClass("forge.game.phase.PhaseType").MAIN2, owner, java.JInt(1)
    )


def _animate(java, game, source, target=None):
    ability = _activated(source, "Animate", source.getController())
    if target is not None:
        ability.getSubAbility().getTargets().add(target)
    _pay(java, game, ability, source.getController())
    paid = len(game.getStack().peekAbility().getPayingMana())
    game.getStack().resolveStack()
    _settle(game)
    return paid


def _revert(java, game, source):
    owner = source.getController()
    static_ability = java.JClass("forge.game.spellability.AbilityStatic")
    revert = next(
        ability
        for ability in source.getSpellAbilities()
        if isinstance(ability, static_ability)
        and "End Effect" in str(ability.getDescription())
    )
    revert.setActivatingPlayer(owner)
    if not revert.canPlay() or not java.JClass("forge.ai.ComputerUtil").playNoStack(
        owner, revert, game, False
    ):
        raise ValueError("Fixed Licid fixture could not pay to end its animation")
    revert_paid = len(revert.getPayingMana())
    _settle(game)
    return revert_paid


def _licid(java, name):
    lands = tuple(
        land
        for land in ("Plains", "Island", "Swamp", "Mountain", "Forest")
        for _ in range(4)
    )
    game, owner, zones = _setup(java, lands)
    source, cast_paid = _cast(java, game, name, owner, zones)
    _round(java, game)
    bear = _add(java, "Grizzly Bears", owner, zones.Battlefield)
    activate_paid = _animate(java, game, source, bear)
    before = _form(java, source)
    copied, gaps = _snapshot(java, game)
    copied_source = _find(copied, source.getId())
    revert_paid = _revert(java, copied, copied_source)
    return {
        "case": "licid",
        "name": name,
        "paid": [cast_paid, activate_paid, revert_paid],
        "before": before,
        "copied_after": _form(java, copied_source),
        "source_after": _form(java, source),
        "copied_target": _form(java, _find(copied, bear.getId())),
        "source_target": _form(java, bear),
        "unmapped": gaps,
    }


def _factory(java, permanent=False):
    game, owner, zones = _setup(java, ("Forest",) * (6 if permanent else 1))
    _postcombat(java, game, owner)
    factory = _add(java, "Mishra's Factory", owner, zones.Battlefield)
    paid = []
    if permanent:
        paid.append(_targeted(java, game, "Awakening of Vitu-Ghazi", owner, factory))
    paid.append(_animate(java, game, factory))
    before = _form(java, factory)
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    return {
        "case": "permanent_layer" if permanent else "factory",
        "paid": paid,
        "before": before,
        "after": _form(java, _find(copied, factory.getId())),
        "source": _form(java, factory),
        "unmapped": gaps,
    }


def _land(java):
    game, owner, zones = _setup(java, ("Forest",))
    _postcombat(java, game, owner)
    land = _add(
        java, "Mountain", game.getPlayers().get(java.JInt(1)), zones.Battlefield
    )
    paid = _targeted(java, game, "Animate Land", owner, land)
    before = _form(java, land)
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    return {
        "case": "animate_land",
        "paid": paid,
        "before": before,
        "after": _form(java, _find(copied, land.getId())),
        "source": _form(java, land),
        "unmapped": gaps,
    }


def _spoiling(java):
    game, owner, zones = _setup(java, ("Swamp",) * 3)
    _postcombat(java, game, owner)
    opponent = game.getPlayers().get(java.JInt(1))
    angel = _add(java, "Serra Angel", opponent, zones.Battlefield)
    paid = _targeted(java, game, "Sudden Spoiling", owner, opponent)
    before = _form(java, angel)
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    return {
        "case": "spoiling",
        "paid": paid,
        "before": before,
        "after": _form(java, _find(copied, angel.getId())),
        "source": _form(java, angel),
        "unmapped": gaps,
    }


def _creature_count(game, zones):
    return sum(bool(card.isCreature()) for card in game.getCardsIn(zones.Battlefield))


def _affinity(java):
    game, owner, zones = _setup(java, ("Forest",) * 3)
    _postcombat(java, game, owner)
    _add(java, "Mountain", game.getPlayers().get(java.JInt(1)), zones.Battlefield)
    _, paid = _cast(java, game, "Natural Affinity", owner, zones)
    before = _creature_count(game, zones)
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    return {
        "case": "affinity",
        "paid": paid,
        "before": before,
        "after": _creature_count(copied, zones),
        "source": _creature_count(game, zones),
        "lands_after": len(copied.getCardsIn(zones.Battlefield)),
        "unmapped": gaps,
    }


def main() -> None:
    """Record all 13 Licids and five other fixed animation outcomes without hiding failures."""
    java, engine = initialize_test_engine()
    cases = [_licid(java, name) for name in LICIDS]
    cases.extend((_factory(java), _land(java), _spoiling(java), _affinity(java)))
    cases.append(_factory(java, permanent=True))
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "animation-state.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
