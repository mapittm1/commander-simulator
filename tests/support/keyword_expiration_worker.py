"""Paid keyword restoration and end-of-combat power exchange witnesses."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted fixture programs and paths, independent of uploaded input.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _postcombat
from tests.support.delayed_worker import _settle, _setup
from tests.support.duration_worker import _snapshot
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.turn_schedule_worker import _advance, _next_turn
from tests.support.usage_worker import _activated, _add, _cast, _find


def _resolve(java, game, ability, owner, target=None):
    ability.setActivatingPlayer(owner)
    if target is not None:
        ability.getTargets().add(target)
        if not target.canBeTargetedBy(ability):
            raise ValueError("Fixed keyword witness selected an illegal target")
    _pay(java, game, ability, owner)
    stacked = game.getStack().peekAbility()
    if stacked.getActivatingPlayer().getId() != owner.getId():
        raise ValueError(
            "Fixed keyword witness changed its acting player during payment"
        )
    paid = len(stacked.getPayingMana())
    game.getStack().resolveStack()
    _settle(game)
    return paid


def _targeted(java, game, name, owner, target):
    zones = java.JClass("forge.game.zone.ZoneType")
    card = _add(java, name, owner, zones.Hand)
    return _resolve(java, game, card.getFirstSpellAbility(), owner, target)


def _simple(java, game, owner, zones, case):
    costs = {"activator": 0, "tapped": False, "sacrificed": False}
    if case == "claws":
        target = _add(java, "Serra Angel", owner, zones.Battlefield)
        keyword = "Flying"
        paid = [_targeted(java, game, "Canopy Claws", owner, target)]
    elif case == "landwalk":
        target = _add(java, "River Boa", owner, zones.Battlefield)
        keyword = "Landwalk:Island"
        land = _add(java, "Hammerheim", owner, zones.Battlefield)
        paid = [_resolve(java, game, _activated(land, "Debuff", owner), owner, target)]
        costs["tapped"] = bool(land.isTapped())
    elif case == "dragon":
        target, cast_paid = _cast(java, game, "Canopy Dragon", owner, zones)
        keyword = "Trample"
        paid = [
            cast_paid,
            _resolve(java, game, _activated(target, "Pump", owner), owner),
        ]
    else:
        target, cast_paid = _cast(java, game, "Zerapa Minotaur", owner, zones)
        keyword = "First Strike"
        opponent = game.getPlayers().get(java.JInt(1))
        for _ in range(2):
            _add(java, "Island", opponent, zones.Battlefield)
        paid = [
            cast_paid,
            _resolve(java, game, _activated(target, "Debuff", opponent), opponent),
        ]
        costs["activator"] = 1
    return target, keyword, None, paid, costs


def _snitch(java, game, owner, zones, case):
    names = {
        "snitch": "White Knight",
        "angel": "Iridescent Angel",
        "ward": "Grizzly Bears",
    }
    keywords = {
        "snitch": "Protection from black",
        "angel": "Protection from each color",
        "ward": "Protection:Card.nonColorless:each color:Aura",
    }
    target = _add(java, names[case], owner, zones.Battlefield)
    paid = []
    if case == "ward":
        paid.append(_targeted(java, game, "Spectra Ward", owner, target))
    snitch, mana = _cast(java, game, "Cephalid Snitch", owner, zones)
    paid.append(mana)
    if case != "snitch":
        # The blue Snitch must become colorless before it can target these creatures.
        paid.append(_targeted(java, game, "Moonlace", owner, snitch))
    paid.append(
        _resolve(java, game, _activated(snitch, "Debuff", owner), owner, target)
    )
    red = (
        None
        if case == "snitch"
        else (
            "Protection from red" if case == "angel" else "Protection:Card.Red:red:Aura"
        )
    )
    return (
        target,
        keywords[case],
        red,
        paid,
        {
            "activator": 0,
            "tapped": False,
            "sacrificed": str(snitch.getZone().getZoneType()) == "Graveyard",
        },
    )


def _state(card, keyword, red):
    return {
        "selected": bool(card.hasKeyword(keyword)),
        "flying": bool(card.hasKeyword("Flying")),
        "split_red": bool(red is not None and card.hasKeyword(red)),
        "zone": str(card.getZone().getZoneType()),
    }


def _removal(java, case):
    lands = ("Forest",) * 8 + ("Mountain",) * 6 + ("Plains",) * 5 + ("Island",) * 3
    game, owner, zones = _setup(java, lands)
    _postcombat(java, game, owner)
    setup = _snitch if case in {"snitch", "angel", "ward"} else _simple
    target, keyword, red, paid, costs = setup(java, game, owner, zones, case)
    observations = _observe_restoration(java, game, target, keyword, red)
    return {"case": case, "paid": paid, "costs": costs, **observations}


def _observe_restoration(java, game, target, keyword, red):
    before = _state(target, keyword, red)
    copied, gaps = _snapshot(java, game)
    mapped = _find(copied, target.getId())
    copied_before = _state(mapped, keyword, red)
    _next_turn(java, copied)
    after = _state(mapped, keyword, red)
    source = _state(target, keyword, red)
    _next_turn(java, game)
    return {
        "before": before,
        "copied_before": copied_before,
        "after": after,
        "source": source,
        "source_after": _state(target, keyword, red),
        "unmapped": gaps,
    }


def _exchange_setup(java):
    game, owner, zones = _setup(java, ("Plains",) * 2)
    _postcombat(java, game, owner)
    master, paid = _cast(java, game, "Serene Master", owner, zones)
    attacker = game.getPlayers().get(java.JInt(1))
    beast = _add(java, "Colossal Dreadmaw", attacker, zones.Battlefield)
    phases = java.JClass("forge.game.phase.PhaseType")
    game.getPhaseHandler().devModeSet(
        phases.COMBAT_DECLARE_ATTACKERS, attacker, java.JInt(2)
    )
    game.getPhaseHandler().setCombat(java.JClass("forge.game.combat.Combat")(attacker))
    game.getCombat().addAttacker(beast, owner)
    _advance(java, game, "COMBAT_DECLARE_BLOCKERS")
    return game, master, beast, paid


def _exchange(java):
    game, master, beast, paid = _exchange_setup(java)
    blocked = bool(game.getCombat().isBlocking(master, beast))
    before = [int(master.getNetPower()), int(beast.getNetPower())]
    copied, gaps = _snapshot(java, game)
    mapped = _find(copied, master.getId())
    copied_before = int(mapped.getNetPower())
    _advance(java, copied, "MAIN2")
    after = int(mapped.getNetPower())
    source = int(master.getNetPower())
    _advance(java, game, "MAIN2")
    return {
        "case": "exchange",
        "paid": [paid],
        "blocked": blocked,
        "before": before,
        "copied_before": copied_before,
        "after": after,
        "source": source,
        "source_after": int(master.getNetPower()),
        "unmapped": gaps,
    }


def main() -> None:
    """Pay real spell/activation costs and record independent native deadlines."""
    java, engine = initialize_test_engine()
    cases = [
        _removal(java, case)
        for case in (
            "claws",
            "landwalk",
            "dragon",
            "minotaur",
            "snitch",
            "angel",
            "ward",
        )
    ]
    cases.append(_exchange(java))
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "keyword-expiration.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
