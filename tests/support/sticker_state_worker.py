"""Fixed native snapshot-fidelity checks; these do not certify sticker effect rules."""

import hashlib
import json
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed test-only checkout bootstrap; no uploaded modules.
# pylint: disable=wrong-import-position
from commander.engine.interactions.fixtures import _card, _new_game
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from tests.support.native_worker import initialize_test_engine


def _state(card):
    """Observe effective characteristics and physical sticker identity separately."""
    records = [
        [
            int(item.getSticker().getSheet().getId()),
            str(item.getSticker().getSlot()),
            str(item.getSticker().getKind()),
            int(item.getTimestamp()),
            int(item.getNamePosition()),
        ]
        for item in card.getStickers()
    ]
    return {
        "name": str(card.getName()),
        "power": int(card.getNetPower()),
        "toughness": int(card.getNetToughness()),
        "haste": bool(card.hasKeyword("Haste")),
        "triggers": len(card.getTriggers()),
        "statics": len(card.getStaticAbilities()),
        "records_sha256": hashlib.sha256(json.dumps(records).encode()).hexdigest(),
        "stickers": len(records),
    }


def _case(java, sheet_name, zone_name):
    game = _new_game(java)
    owner = game.getPlayers().get(java.JInt(0))
    zones = java.JClass("forge.game.zone.ZoneType")
    sheets = java.JClass("forge.game.card.sticker.StickerSheet")
    sheet = java.JClass("forge.game.card.Card").fromPaperCard(
        java.JClass("forge.model.FModel")
        .getMagicDb()
        .getVariantCards()
        .getCard(sheet_name),
        owner,
    )
    owner.getZone(zones.StickerSheets).add(sheet)
    card = _card(
        java, {"engine_name": "Grizzly Bears"}, owner, getattr(zones, zone_name)
    )
    for sticker in sheets.getStickers(sheet):
        card.addSticker(
            java.JClass("forge.game.card.sticker.AppliedSticker")(
                sticker, game.getNextTimestamp(), java.JInt(0)
            )
        )
    game.getAction().checkStaticAbilities(False)
    before = _state(card)
    lki = java.JClass("forge.game.card.CardCopyService").getLKICopy(card)
    snapshot = java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()
    copied_owner = snapshot.getPlayers().get(java.JInt(0))
    copied = next(
        item
        for item in copied_owner.getCardsIn(getattr(zones, zone_name))
        if item.getId() == card.getId()
    )
    return {
        "sheet": sheet_name,
        "zone": zone_name,
        "original": before,
        "copied": _state(copied),
        "lki": _state(lki),
        "available": len(sheets.getAvailableStickers(copied_owner, java.JInt(100))),
        "independent_sheet": _independent_sheet(java, copied, sheet, snapshot),
    }


def _independent_sheet(java, card, source_sheet, snapshot):
    """Card.equals compares stable IDs; test Java object identity explicitly."""
    original_objects = java.JClass("java.util.IdentityHashMap")()
    original_objects.put(source_sheet, source_sheet)
    return all(
        item.getSticker().getSheet().getGame() == snapshot
        and not original_objects.containsKey(item.getSticker().getSheet())
        for item in card.getStickers()
    )


def main():
    """Use only fixed catalog fixture names in a bounded native process."""
    run = Path(sys.argv[1]).resolve()
    inputs = read_bounded_json(run / "sticker-inputs.json")
    java, engine = initialize_test_engine()
    cases = [
        _case(java, name, zone)
        for name in inputs["sheets"]
        for zone in ("Battlefield", "Graveyard", "Exile")
    ]
    write_bounded_json(run / "sticker-state.json", {"engine": engine, "cases": cases})


if __name__ == "__main__":
    main()
