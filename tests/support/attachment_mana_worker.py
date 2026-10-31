"""Paid Reconfigure cleanup and mana-granted keyword lifetime witnesses."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted native fixtures; uploaded cards never select executable code.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _postcombat
from tests.support.duration_worker import _next_turn, _snapshot
from tests.support.keyword_expiration_worker import _resolve
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.usage_worker import _activated, _add, _cast, _find, _setup


def _pod(java):
    game, owner, zones = _setup(
        java, ("Plains",) * 15 + ("Mountain",) * 12 + ("Island",) * 3
    )
    _postcombat(java, game, owner)
    return game, owner, zones


def _shape(game, equipment, zones):
    attached = equipment.getEntityAttachedTo()
    return {
        "creature": bool(equipment.isCreature()),
        "attached_to": int(attached.getId()) if attached is not None else None,
        "zone": str(equipment.getZone().getZoneType()),
        "effects": sum(
            str(card.getName()) == "Reconfigure Effect"
            for card in game.getCardsIn(zones.Command)
        ),
    }


def _change_attachment(java, game, cards, mode):
    equipment, bearer, second = cards
    owner = equipment.getController()
    zones = java.JClass("forge.game.zone.ZoneType")
    transferred = None
    if mode == "detach":
        cost = _resolve(java, game, _activated(equipment, "Unattach", owner), owner)
    elif mode == "reattach":
        cost = _resolve(
            java, game, _activated(equipment, "Attach", owner), owner, second
        )
        transferred = _shape(game, equipment, zones)
        cost += _resolve(java, game, _activated(equipment, "Unattach", owner), owner)
    else:
        target = bearer if mode == "bearer" else equipment
        spell_name = "Unsummon" if mode == "bearer" else "Disperse"
        bounce = _add(java, spell_name, owner, zones.Hand).getFirstSpellAbility()
        bounce.setActivatingPlayer(owner)
        if not bounce.canTarget(target):
            raise ValueError("Fixed attachment witness selected an illegal target")
        cost = _resolve(java, game, bounce, owner, target)
        if mode == "source":
            equipment = next(
                card
                for card in owner.getCardsIn(zones.Hand)
                if card.getId() == equipment.getId()
            )
    return cost, transferred, equipment


def _attachment(java, name, mode):
    game, owner, zones = _pod(java)
    bearer = _add(java, "Grizzly Bears", owner, zones.Battlefield)
    second = _add(java, "Runeclaw Bear", owner, zones.Battlefield)
    equipment, paid = _cast(java, game, name, owner, zones)
    attach = _resolve(java, game, _activated(equipment, "Attach", owner), owner, bearer)
    return _observe_attachment(
        java, game, (equipment, bearer, second), [paid, attach], mode
    )


def _observe_attachment(java, game, cards, costs, mode):
    equipment, bearer, second = cards
    zones = java.JClass("forge.game.zone.ZoneType")
    observations = {"before": _shape(game, equipment, zones)}
    copied, gaps = _snapshot(java, game)
    mapped = _find(copied, equipment.getId())
    observations["copied_before"] = _shape(copied, mapped, zones)
    cost, transferred, mapped = _change_attachment(
        java,
        copied,
        (mapped, _find(copied, bearer.getId()), _find(copied, second.getId())),
        mode,
    )
    observations["after"] = _shape(copied, mapped, zones)
    observations["source"] = _shape(game, equipment, zones)
    costs.extend(
        (
            cost,
            _resolve(
                java,
                game,
                _activated(equipment, "Unattach", equipment.getController()),
                equipment.getController(),
            ),
        )
    )
    observations["source_after"] = _shape(game, equipment, zones)
    return {
        "case": str(equipment.getName()) + ":" + mode,
        "paid": costs,
        **observations,
        "transferred": transferred,
        "second": int(second.getId()),
        "unmapped": gaps,
    }


def _grant_mana(java, name):
    game, owner, zones = _pod(java)
    if name in {"Arena of Glory", "Hall of the Bandit Lord"}:
        source = _add(java, name, owner, zones.Battlefield)
        paid = 0
    else:
        source, paid = _cast(java, game, name, owner, zones)
        if name == "Generator Servant":
            for _ in range(4):
                _next_turn(java, game)
    ability = next(
        action
        for action in source.getSpellAbilities()
        if action.isActivatedAbility()
        and str(action.getApi()) == "Mana"
        and action.hasParam("AddsKeywords")
    )
    ability.setActivatingPlayer(owner)
    if not ability.canPlay() or not java.JClass("forge.ai.ComputerUtil").playNoStack(
        owner, ability, game, False
    ):
        raise ValueError("Fixed mana witness failed native payment")
    return game, source, paid


def _spend_mana(java, game, source):
    owner = source.getController()
    zones = java.JClass("forge.game.zone.ZoneType")
    dragon = _add(java, "Furnace Whelp", owner, zones.Hand)
    _pay(java, game, dragon.getFirstSpellAbility(), owner)
    pending = game.getStack().peekAbility()
    sources = [int(mana.getSourceCard().getId()) for mana in pending.getPayingMana()]
    if source.getId() not in sources:
        raise ValueError("Fixed witness did not spend the keyword-granting mana")
    game.getStack().resolveStack()
    return _find(game, dragon.getId()), sources


def _mana(java, name):
    game, source, paid = _grant_mana(java, name)
    dragon, sources = _spend_mana(java, game, source)
    observations = {
        "before": bool(dragon.hasKeyword("Haste")),
        "life": int(source.getController().getLife()),
        "source_zone": str(source.getZone().getZoneType()),
        "exerted": int(source.getExertedThisTurn()),
        "source_tapped": bool(source.isTapped()),
    }
    copied, gaps = _snapshot(java, game)
    mapped = _find(copied, dragon.getId())
    observations["copied_before"] = bool(mapped.hasKeyword("Haste"))
    _next_turn(java, copied)
    observations["after"] = bool(mapped.hasKeyword("Haste"))
    observations["source"] = bool(dragon.hasKeyword("Haste"))
    _next_turn(java, game)
    observations["source_after"] = bool(dragon.hasKeyword("Haste"))
    return {
        "case": name,
        "paid": [paid, len(sources)],
        "mana_sources": sources,
        "source_id": int(source.getId()),
        **observations,
        "unmapped": gaps,
    }


def main() -> None:
    """Retain twelve actual paid cleanup witnesses and their source worlds."""
    java, engine = initialize_test_engine()
    cases = [
        _attachment(java, name, mode)
        for name in ("Lion Sash", "Lizard Blades")
        for mode in ("detach", "bearer", "source", "reattach")
    ]
    cases.extend(
        _mana(java, name)
        for name in (
            "Carnelian Orb of Dragonkind",
            "Generator Servant",
            "Arena of Glory",
            "Hall of the Bandit Lord",
        )
    )
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "attachment-mana.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
