"""Fixed paid witnesses for temporal state and explicitly mapped native callbacks."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed checkout bootstrap; no uploaded scenario code or executable paths.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.usage_worker import (
    _activated,
    _add,
    _cast,
    _find,
    _settle,
    _setup,
    _usage,
)


def _snapshot(java, game):
    """Return a new world and any explicitly reported unmapped callback kinds."""
    copier = java.JClass("forge.ai.simulation.GameCopier")(game).withStack(True)
    copied = copier.makeCopy()
    gaps = (
        [str(kind) for kind in copier.getUnmappedSnapshotCommands()]
        if hasattr(copier, "getUnmappedSnapshotCommands")
        else None
    )
    return copied, gaps


def _next_turn(java, game):
    """Run native phase hooks through the next main phase, without response priority."""
    before = int(game.getPhaseHandler().getTurn())
    resolver = java.JProxy("java.lang.Runnable", {"run": lambda: _settle(game)})
    if not game.getPhaseHandler().devAdvanceThroughTurnEnd(
        java.JClass("forge.game.phase.PhaseType").MAIN1, resolver
    ):
        raise ValueError("Fixed duration witness could not reach the next main phase")
    _settle(game)
    if int(game.getPhaseHandler().getTurn()) != before + 1:
        raise ValueError("Native duration witness did not advance one turn")


def _targeted(java, game, name, owner, target):
    """Pay for a fixed targeted spell and resolve its native rules."""
    zones = java.JClass("forge.game.zone.ZoneType")
    card = _add(java, name, owner, zones.Hand)
    ability = card.getFirstSpellAbility()
    ability.getTargets().add(target)
    _pay(java, game, ability, owner)
    paid = len(game.getStack().peekAbility().getPayingMana())
    game.getStack().resolveStack()
    _settle(game)
    return paid


def _shape(java, card):
    """Observe mutable characteristics and ownership independently of descriptions."""
    keywords = java.JClass("forge.game.keyword.Keyword")
    return {
        "power": int(card.getNetPower()),
        "toughness": int(card.getNetToughness()),
        "haste": bool(card.hasKeyword(keywords.HASTE)),
        "trample": bool(card.hasKeyword(keywords.TRAMPLE)),
        "controller": int(card.getController().getId()),
        "zone_player": int(card.getZone().getPlayer().getId()),
        "controller_world": bool(card.getController().getGame() == card.getGame()),
        "zone_world": bool(card.getZone().getPlayer().getGame() == card.getGame()),
    }


def _pump(java, mass):
    """A resolved self-pump or Overrun must expire only in the advanced world."""
    game, owner, zones = _setup(java, ("Forest",) * (5 if mass else 3))
    if mass:
        card = _add(java, "Grizzly Bears", owner, zones.Battlefield)
        _add(java, "Runeclaw Bear", owner, zones.Battlefield)
        _, paid = _cast(java, game, "Overrun", owner, zones)
    else:
        card, paid = _cast(java, game, "Basking Rootwalla", owner, zones)
        action = _activated(card, "Pump", owner)
        _pay(java, game, action, owner)
        paid += len(game.getStack().peekAbility().getPayingMana())
        game.getStack().resolveStack()
    before = _shape(java, card)
    copied, gaps = _snapshot(java, game)
    mapped = _find(copied, card.getId())
    pending = _shape(java, mapped)
    _next_turn(java, copied)
    return {
        "case": "overrun" if mass else "rootwalla",
        "paid": paid,
        "before": before,
        "copied": pending,
        "after": _shape(java, mapped),
        "source": _shape(java, card),
        "unmapped": gaps,
    }


def _theft(java):
    """Temporary control and haste must end at the copied cleanup boundary."""
    game, owner, zones = _setup(java, ("Mountain",) * 3)
    opponent = game.getPlayers().get(java.JInt(1))
    card = _add(java, "Grizzly Bears", opponent, zones.Battlefield)
    paid = _targeted(java, game, "Act of Treason", owner, card)
    before = _shape(java, card)
    copied, gaps = _snapshot(java, game)
    mapped = _find(copied, card.getId())
    pending = _shape(java, mapped)
    _next_turn(java, copied)
    return {
        "case": "theft",
        "paid": paid,
        "before": before,
        "copied": pending,
        "after": _shape(java, mapped),
        "source": _shape(java, card),
        "original_controller": int(opponent.getId()),
        "unmapped": gaps,
    }


def _sower(java):
    """A copied Sower death returns the stolen permanent without changing the source."""
    game, owner, zones = _setup(java, ("Island",) * 4)
    opponent = game.getPlayers().get(java.JInt(1))
    _add(java, "Mountain", opponent, zones.Battlefield)
    bear = _add(java, "Grizzly Bears", opponent, zones.Battlefield)
    sower, paid = _cast(java, game, "Sower of Temptation", owner, zones)
    _settle(game)
    before = _shape(java, bear)
    copied, gaps = _snapshot(java, game)
    response = _targeted(
        java,
        copied,
        "Lightning Bolt",
        copied.getPlayer(opponent.getId()),
        _find(copied, sower.getId()),
    )
    return {
        "case": "sower",
        "paid": [paid, response],
        "before": before,
        "after": _shape(java, _find(copied, bear.getId())),
        "source": _shape(java, bear),
        "original_controller": int(opponent.getId()),
        "copied_sower_zone": str(_find(copied, sower.getId()).getZone().getZoneType()),
        "source_sower_zone": str(sower.getZone().getZoneType()),
        "unmapped": gaps,
    }


def _shroud(java):
    """Player keyword layers exist before expiration and remain in the source."""
    game, owner, zones = _setup(java, ("Plains", "Forest"))
    _, paid = _cast(java, game, "Gilded Light", owner, zones)
    shroud = java.JClass("forge.game.keyword.Keyword").SHROUD
    before = bool(owner.hasKeyword(shroud))
    copied, gaps = _snapshot(java, game)
    mapped = copied.getPlayer(owner.getId())
    pending = bool(mapped.hasKeyword(shroud))
    _next_turn(java, copied)
    return {
        "case": "shroud",
        "paid": paid,
        "before": before,
        "copied": pending,
        "after": bool(mapped.hasKeyword(shroud)),
        "source": bool(owner.hasKeyword(shroud)),
        "unmapped": gaps,
    }


def _until_untap(java):
    """An unconditional paid untap ends Endoskeleton's sustained toughness bonus."""
    game, owner, zones = _setup(java, ("Forest",) * 5 + ("Island",))
    bear = _add(java, "Grizzly Bears", owner, zones.Battlefield)
    skeleton, cast_paid = _cast(java, game, "Endoskeleton", owner, zones)
    pump = _activated(skeleton, "Pump", owner)
    pump.getTargets().add(bear)
    _pay(java, game, pump, owner)
    pump_paid = len(game.getStack().peekAbility().getPayingMana())
    game.getStack().resolveStack()
    before = int(bear.getNetToughness())
    copied, gaps = _snapshot(java, game)
    _, reversal_paid = _cast(
        java, copied, "Dramatic Reversal", copied.getPlayer(owner.getId()), zones
    )
    return {
        "case": "untap",
        "paid": [cast_paid, pump_paid, reversal_paid],
        "before": before,
        "after": int(_find(copied, bear.getId()).getNetToughness()),
        "source": int(bear.getNetToughness()),
        "copied_tapped": bool(_find(copied, skeleton.getId()).isTapped()),
        "source_tapped": bool(skeleton.isTapped()),
        "unmapped": gaps,
    }


def _number(card):
    """Preserve a numeric choice separately from its visible card-view string."""
    number = card.getChosenNumber()
    return {
        "number": int(number) if number is not None else None,
        "visible": str(card.getView().getChosenNumber()),
    }


def _prelate(java):
    """Observe the legal native AI number choice; native Java tests control choice two."""
    game, owner, zones = _setup(java, ("Plains",) * 3)
    card, paid = _cast(java, game, "Sanctum Prelate", owner, zones)
    before = _number(card)
    copied, gaps = _snapshot(java, game)
    return {
        "case": "number",
        "paid": paid,
        "before": before,
        "copied": _number(_find(copied, card.getId())),
        "source": _number(card),
        "unmapped": gaps,
    }


def _round(java):
    """Reuse a paid per-turn activation after all four normal turn transitions."""
    game, owner, zones = _setup(java, ("Plains", "Mountain", "Forest"))
    bear = _add(java, "Grizzly Bears", owner, zones.Battlefield)
    spring, paid = _cast(java, game, "Invigorating Hot Spring", owner, zones)
    action = _activated(spring, "PutCounter", owner)
    action.getTargets().add(bear)
    _pay(java, game, action, owner)
    game.getStack().resolveStack()
    copied, gaps = _snapshot(java, game)
    for _ in range(4):
        _next_turn(java, copied)
    mapped_owner = copied.getPlayer(owner.getId())
    restored = _activated(_find(copied, spring.getId()), "PutCounter", mapped_owner)
    before_reuse = _usage(restored)
    restored.getTargets().clear()
    restored.getTargets().add(_find(copied, bear.getId()))
    _pay(java, copied, restored, mapped_owner)
    copied.getStack().resolveStack()
    return {
        "case": "round",
        "paid": paid,
        "before_reuse": before_reuse,
        "after_reuse": _usage(restored),
        "source": _usage(action),
        "active_seat": int(copied.getPhaseHandler().getPlayerTurn().getId()),
        "owner": int(owner.getId()),
        "drawn_hand": len(mapped_owner.getCardsIn(zones.Hand)),
        "unmapped": gaps,
    }


def main() -> None:
    """Write eight fixed runtime observations without asserting the candidate fixes."""
    java, engine = initialize_test_engine()
    cases = [_pump(java, False), _pump(java, True)]
    cases.extend(
        observe(java)
        for observe in (_theft, _sower, _shroud, _until_untap, _prelate, _round)
    )
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "duration-state.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
