"""Paid witnesses for condition-bound control and explicit effect expiration."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed native fixtures; no uploaded programs or executable paths are accepted.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.delayed_worker import _settle, _setup
from tests.support.duration_worker import _snapshot, _targeted
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.turn_schedule_worker import _next_turn, _seat
from tests.support.usage_worker import _activated, _add, _cast, _find


def _round(java, game):
    """Pass a full native turn cycle before creating opposing fixture creatures."""
    for _ in range(4):
        _next_turn(java, game)


def _steal(java, game, source, target):
    ability = _activated(source, "GainControl", source.getController())
    ability.getTargets().add(target)
    _pay(java, game, ability, source.getController())
    game.getStack().resolveStack()
    _settle(game)


def _control_setup(java, name, lands):
    game, owner, zones = _setup(java, lands)
    source, paid = _cast(java, game, name, owner, zones)
    _round(java, game)
    opponent = game.getPlayers().get(java.JInt(1))
    bear = _add(java, "Grizzly Bears", opponent, zones.Battlefield)
    return game, source, paid, opponent, bear


def _old_man(java, condition):
    game, source, paid, opponent, bear = _control_setup(
        java, "Old Man of the Sea", ("Island",) * 3
    )
    for land in ("Forest", "Swamp"):
        _add(java, land, opponent, java.JClass("forge.game.zone.ZoneType").Battlefield)
    _steal(java, game, source, bear)
    copied, gaps = _snapshot(java, game)
    copied_source = _find(copied, source.getId())
    copied_bear = _find(copied, bear.getId())
    copied_opponent = copied.getPlayer(opponent.getId())
    watchers = len(copied_source.getStaticCommandList())
    response = 0
    if condition == "grow":
        response = _targeted(java, copied, "Giant Growth", copied_opponent, copied_bear)
    elif condition == "shrink":
        response = _targeted(java, copied, "Disfigure", copied_opponent, copied_source)
    else:
        if not copied_source.untap():
            raise ValueError("Fixed Old Man fixture could not untap its source")
        _settle(copied)
    return {
        "case": "old_man_" + condition,
        "paid": [paid, response],
        "copied_watchers": watchers,
        "after_controller": _seat(copied, copied_bear.getController()),
        "zone_player": _seat(copied, copied_bear.getZone().getPlayer()),
        "target_power": int(copied_bear.getNetPower()),
        "source_power": int(copied_source.getNetPower()),
        "source_controller": _seat(game, bear.getController()),
        "source_tapped": bool(source.isTapped()),
        "unmapped": gaps,
    }


def _matriarch(java, removal):
    game, source, paid, _opponent, bear = _control_setup(
        java, "Rootwater Matriarch", ("Island",) * 4 + ("Forest", "Plains", "Plains")
    )
    owner = source.getController()
    aura_paid = _targeted(java, game, "Rancor", owner, bear)
    aura = next(
        card
        for card in owner.getCardsIn(
            java.JClass("forge.game.zone.ZoneType").Battlefield
        )
        if card.getName() == "Rancor"
    )
    _steal(java, game, source, bear)
    copied, gaps = _snapshot(java, game)
    target = _find(copied, aura.getId() if removal == "Disenchant" else bear.getId())
    response = _targeted(java, copied, removal, copied.getPlayer(owner.getId()), target)
    copied_bear = _find(copied, bear.getId())
    return {
        "case": "matriarch_" + removal.lower(),
        "paid": [paid, aura_paid, response],
        "after_controller": _seat(copied, copied_bear.getController()),
        "after_enchanted": bool(copied_bear.isEnchanted()),
        "source_controller": _seat(game, bear.getController()),
        "source_enchanted": bool(bear.isEnchanted()),
        "unmapped": gaps,
    }


def _bolt(java, owner, target):
    zones = java.JClass("forge.game.zone.ZoneType")
    ability = _add(java, "Lightning Bolt", owner, zones.Hand).getFirstSpellAbility()
    ability.setActivatingPlayer(owner)
    ability.getTargets().add(target)
    return ability


def _peace(java, after_first_end):
    game, owner, zones = _setup(java, ("Plains",) * 2)
    _, paid = _cast(java, game, "Peace Talks", owner, zones)
    if after_first_end:
        _next_turn(java, game)
    copied, gaps = _snapshot(java, game)
    copied_owner = copied.getPlayer(owner.getId())
    probe = _bolt(java, copied.getPlayers().get(java.JInt(2)), copied_owner)
    immediate = bool(probe.canTarget(copied_owner))
    if not after_first_end:
        _next_turn(java, copied)
    middle = bool(probe.canTarget(copied_owner))
    _next_turn(java, copied)
    source_probe = _bolt(java, game.getPlayers().get(java.JInt(2)), owner)
    return {
        "case": "peace_registered" if after_first_end else "peace_deferred",
        "paid": paid,
        "can_target_before": immediate,
        "can_target_middle": middle,
        "can_target_after": bool(probe.canTarget(copied_owner)),
        "source_can_target": bool(source_probe.canTarget(owner)),
        "unmapped": gaps,
    }


def _attempt_cast(java, game, ability):
    return bool(
        java.JClass("forge.ai.ComputerUtil").playStack(
            ability, ability.getActivatingPlayer(), game
        )
    )


def _silence(java):
    game, owner, zones = _setup(java, ("Plains",))
    opponent = game.getPlayers().get(java.JInt(1))
    _add(java, "Mountain", opponent, zones.Battlefield)
    _, paid = _cast(java, game, "Silence", owner, zones)
    copied, gaps = _snapshot(java, game)
    copied_owner = copied.getPlayer(owner.getId())
    copied_opponent = copied.getPlayer(opponent.getId())
    probe = _bolt(java, copied_opponent, copied_owner)
    before_restricted = not bool(probe.checkRestrictions(copied_opponent))
    before_denied = not _attempt_cast(java, copied, probe)
    _next_turn(java, copied)
    after_allowed = _attempt_cast(java, copied, probe)
    bolt_paid = (
        len(copied.getStack().peekAbility().getPayingMana()) if after_allowed else 0
    )
    _settle(copied)
    return {
        "case": "silence",
        "paid": [paid, bolt_paid],
        "before_restricted": before_restricted,
        "before_denied": before_denied,
        "after_allowed": after_allowed,
        "copied_life": int(copied_owner.getLife()),
        "source_life": int(owner.getLife()),
        "source_restricted": not bool(
            _bolt(java, opponent, owner).checkRestrictions(opponent)
        ),
        "unmapped": gaps,
    }


def main() -> None:
    """Record nine observations; integration tests assert the reviewed outcomes."""
    java, engine = initialize_test_engine()
    cases = [_old_man(java, condition) for condition in ("grow", "shrink", "untap")]
    cases.extend(
        _matriarch(java, name) for name in ("Disenchant", "Ephemerate", "Cloudshift")
    )
    cases.extend(_peace(java, registered) for registered in (False, True))
    cases.append(_silence(java))
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "conditional-state.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
