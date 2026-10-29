"""Fixed paid cast-form witnesses for the reviewed native runtime."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Test-only checkout bootstrap; all scenarios below are fixed native fixtures.
# pylint: disable=wrong-import-position
from commander.engine.interactions.fixtures import _card
from commander.engine.interactions.suite import write_bounded_json
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.starting_life_worker import _pod

SCENARIOS = (
    ("adventure", "Brazen Borrower", ("Island",) * 2, "Secondary"),
    (
        "modal",
        "Esika, God of the Tree",
        ("Plains", "Island", "Swamp", "Mountain", "Forest"),
        "Backside",
    ),
    ("split", "Fire // Ice", ("Island",) * 2, "RightSplit"),
    ("morph", "Willbender", ("Island",) * 3, None),
    ("prototype", "Phyrexian Fleshgorger", ("Swamp",) * 3, None),
    ("bestow", "Boon Satyr", ("Forest",) * 5, None),
)


def _add(java, name, owner, zone):
    """Build a fixed fixture card through the native card factory."""
    return _card(java, {"engine_name": name}, owner, zone)


def _ability(java, card, owner, case, face):
    """Select a printed face or a native alternate-cost ability."""
    if face is not None:
        state = java.JClass("forge.card.CardStateName").valueOf(face)
        choices = card.getState(state).getSpellAbilities()
    else:
        choices = card.getAllPossibleAbilities(owner, False)
    for ability in choices:
        if not ability.isSpell():
            continue
        if case == "morph" and not ability.isCastFaceDown():
            continue
        if case == "prototype" and not ability.hasParam("Prototype"):
            continue
        if case == "bestow" and not ability.isBestow():
            continue
        ability.setActivatingPlayer(owner)
        return ability
    raise ValueError("Fixed native cast-form ability was unavailable")


def _form(card):
    """Observe pending/resolved characteristics without relying on script presence."""
    return {
        "name": str(card.getName()),
        "face": str(card.getCurrentStateName()),
        "backside": bool(card.isBackSide()),
        "facedown": bool(card.isFaceDown()),
        "bestowed": bool(card.isBestowed()),
        "bestow_timestamp": int(card.getBestowTimestamp()),
        "prototype_timestamp": int(card.getPrototypeTimestamp()),
        "mana_value": int(card.getCMC()),
        "black": bool(card.getColor().hasBlack()),
        "colorless": bool(card.getColor().isColorless()),
        "mana_cost": str(card.getCurrentState().getManaCost()),
        "types": str(card.getType()),
        "creature": bool(card.isCreature()),
        "aura": bool(card.isAura()),
        "power": int(card.getNetPower()) if card.isCreature() else None,
        "toughness": int(card.getNetToughness()) if card.isCreature() else None,
    }


def _target(java, case, owner, opponent, zone):
    """Give targeted fixtures legal permanents and leave other casts untargeted."""
    if case == "bestow":
        return _add(java, "Grizzly Bears", owner, zone.Battlefield)
    if case == "adventure":
        return _add(java, "Grizzly Bears", opponent, zone.Battlefield)
    if case == "split":
        return _add(java, "Forest", opponent, zone.Battlefield)
    return None


def _setup(java, scenario):
    """Admit and pay fixed actions in controlled native partial states."""
    case, name, lands, face = scenario
    game, owner = _pod(java, 40)
    opponent = game.getPlayers().get(java.JInt(1))
    zone = java.JClass("forge.game.zone.ZoneType")
    for land in lands:
        _add(java, land, owner, zone.Battlefield)
    target = _target(java, case, owner, opponent, zone)
    ability = _ability(java, _add(java, name, owner, zone.Hand), owner, case, face)
    if target is not None:
        ability.getTargets().add(target)
    _pay(java, game, ability, owner)
    game.getAction().checkStateEffects(True)
    return game, owner, target


def _instances(game, identity):
    """Retain every live-zone instance so a stale stack object cannot be hidden."""
    return [
        {
            "form": _form(card),
            "zone": str(card.getZone().getZoneType()),
            "attached": bool(card.isAttachedToEntity()),
            "world": bool(card.getGame() == game),
            "tapped": bool(card.isTapped()),
        }
        for card in game.getCardsInGame()
        if card.getId() == identity
    ]


def _observe(java, scenario):
    """Pay a real cost, copy the pending cast and resolve only the copied stack."""
    game, _, target = _setup(java, scenario)
    original = game.getStack().peekAbility()
    snapshot = (
        java.JClass("forge.ai.simulation.GameCopier")(game).withStack(True).makeCopy()
    )
    copied = snapshot.getStack().peekAbility()
    state = {
        "case": scenario[0],
        "before": _form(original.getHostCard()),
        "copied_pending": _form(copied.getHostCard()),
        "paid": len(copied.getPayingMana()),
    }
    snapshot.getStack().resolveStack()
    snapshot.getAction().checkStateEffects(True)
    state.update(
        resolved_instances=_instances(snapshot, copied.getHostCard().getId()),
        remaining_stack=int(snapshot.getStack().size()),
        source_pending=_form(original.getHostCard()),
        source_entries=int(game.getStack().size()),
    )
    if target is not None:
        state["target_instances"] = _instances(snapshot, target.getId())
        state["source_target"] = _instances(game, target.getId())
    return state


def main() -> None:
    """Write bounded receipts for six paid native cast forms."""
    java, engine = initialize_test_engine()
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "cast-forms.json",
        {"engine": engine, "cases": [_observe(java, case) for case in SCENARIOS]},
    )


if __name__ == "__main__":
    main()
