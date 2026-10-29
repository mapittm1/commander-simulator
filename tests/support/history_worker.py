"""Fixed packaged-runtime history and paid morbid spell witnesses."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed test-only checkout bootstrap; no uploaded modules.
# pylint: disable=wrong-import-position
from commander.engine.interactions.fixtures import _card, _new_game
from commander.engine.interactions.suite import write_bounded_json
from tests.support.native_worker import initialize_test_engine


def _state(card):
    return {
        "name": str(card.getName()),
        "power": int(card.getNetPower()),
        "toughness": int(card.getNetToughness()),
        "creature": bool(card.isCreature()),
        "stickers": len(card.getStickers()),
        "zone": str(card.getLastKnownZone().getZoneType()),
        "sheet_in_same_game": all(
            item.getSticker().getSheet().getGame() == card.getGame()
            for item in card.getStickers()
        ),
    }


def _sticker(java, game, card, kind):
    owner = card.getOwner()
    zones = java.JClass("forge.game.zone.ZoneType")
    paper = (
        java.JClass("forge.model.FModel")
        .getMagicDb()
        .getVariantCards()
        .getCard("Eldrazi Guacamole Tightrope")
    )
    sheet = java.JClass("forge.game.card.Card").fromPaperCard(paper, owner)
    owner.getZone(zones.StickerSheets).add(sheet)
    chosen = next(
        item
        for item in java.JClass("forge.game.card.sticker.StickerSheet").getStickers(
            sheet
        )
        if str(item.getKind()) == kind
    )
    card.addSticker(
        java.JClass("forge.game.card.sticker.AppliedSticker")(
            chosen, game.getNextTimestamp(), java.JInt(0)
        )
    )


def _history_case(java, case):
    game = _new_game(java)
    owner = game.getPlayers().get(java.JInt(0))
    zones = java.JClass("forge.game.zone.ZoneType")
    name = "Fable of the Mirror-Breaker" if case == "transformed" else "Grizzly Bears"
    zone = zones.Graveyard if case == "graveyard_pt" else zones.Battlefield
    card = _card(java, {"engine_name": name}, owner, zone)
    if case == "counters":
        counters = java.JClass("com.google.common.collect.HashMultiset").create()
        counters.add(java.JClass("forge.game.card.CounterEnumType").P1P1, java.JInt(2))
        card.setCounters(counters)
    elif case == "transformed":
        card.setState(java.JClass("forge.card.CardStateName").Backside, True)
    else:
        _sticker(java, game, card, "PT" if case == "graveyard_pt" else "NAME")
    current = (
        game.getAction().moveToGraveyard(card, None)
        if case in {"counters", "transformed"}
        else game.getAction().moveToHand(card, None)
    )
    departed = (
        game.getLeftGraveyardThisTurn()
        if case == "graveyard_pt"
        else game.getLeftBattlefieldThisTurn()
    ).get(java.JInt(0))
    snapshot = java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()
    copied = (
        snapshot.getLeftGraveyardThisTurn()
        if case == "graveyard_pt"
        else snapshot.getLeftBattlefieldThisTurn()
    ).get(java.JInt(0))
    return {
        "case": case,
        "current_name": str(current.getName()),
        "original": _state(departed),
        "copied": _state(copied),
        "mapped_context": copied.getGame() == snapshot
        and copied.getOwner().getGame() == snapshot
        and copied.getController().getGame() == snapshot,
    }


def _morbid(java, creature_died):
    game = _new_game(java)
    owner = game.getPlayers().get(java.JInt(0))
    opponent = game.getPlayers().get(java.JInt(1))
    zones = java.JClass("forge.game.zone.ZoneType")
    fable = _card(
        java, {"engine_name": "Fable of the Mirror-Breaker"}, owner, zones.Battlefield
    )
    if creature_died:
        fable.setState(java.JClass("forge.card.CardStateName").Backside, True)
    game.getAction().moveToGraveyard(fable, None)
    target = _card(
        java, {"engine_name": "Colossal Dreadmaw"}, opponent, zones.Battlefield
    )
    _card(java, {"engine_name": "Swamp"}, owner, zones.Battlefield)
    slip = _card(java, {"engine_name": "Tragic Slip"}, owner, zones.Hand)
    ability = slip.getFirstSpellAbility()
    ability.setActivatingPlayer(owner)
    ability.getTargets().add(target)
    paid = java.JClass("forge.ai.ComputerUtil").playStack(ability, owner, game)
    snapshot = (
        java.JClass("forge.ai.simulation.GameCopier")(game).withStack(True).makeCopy()
    )
    stack_size = int(snapshot.getStack().size())
    snapshot.getStack().resolveStack()
    power = next(
        int(card.getNetPower())
        for card in snapshot.getCardsIn(zones.Battlefield)
        if card.getId() == target.getId()
    )
    snapshot.getAction().checkStateEffects(True)
    return {
        "creature_died": creature_died,
        "paid": bool(paid),
        "paying_mana": len(ability.getPayingMana()),
        "power_before_state_actions": power,
        "copied_stack": stack_size,
        "target_in_graveyard": any(
            card.getId() == target.getId()
            for card in snapshot.getCardsIn(zones.Graveyard)
        ),
        "source_target_unchanged": bool(target.isInZone(zones.Battlefield)),
        "source_stack": int(game.getStack().size()),
    }


def main():
    """Run fixed source/zone transitions and one actual paid spell resolution."""
    run = Path(sys.argv[1]).resolve()
    java, engine = initialize_test_engine()
    rows = [
        _history_case(java, case)
        for case in ("counters", "name", "transformed", "graveyard_pt")
    ]
    write_bounded_json(
        run / "history-state.json",
        {
            "engine": engine,
            "cases": rows,
            "morbid": [_morbid(java, value) for value in (True, False)],
        },
    )


if __name__ == "__main__":
    main()
