"""Fixed native pregame color test worker for serialized, validated deck revisions."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

# Fixed test-only bootstrap; no uploaded code or arbitrary worker selection.
# pylint: disable=wrong-import-position
from commander.engine.forge_config import file_hash
from commander.engine.interactions.fixtures import _new_game
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from tests.support.native_worker import initialize_test_engine


def main():
    """Load the exact backend exports and observe native pregame identity and mana."""
    run = Path(sys.argv[1]).resolve()
    java, engine = initialize_test_engine()
    path = run / "color-inputs.json"
    inputs = read_bounded_json(path)
    cases = [
        _observe(java, run, case["id"], number)
        for number, case in enumerate(inputs["cases"])
    ]
    write_bounded_json(
        run / "color-receipt.json",
        {
            "engine": engine,
            "inputs_sha256": file_hash(path),
            "cases": cases,
        },
        indent=2,
    )


def _observe(java, run, case_id, number):
    path = run / f"color-{number}.dck"
    deck = java.JClass("forge.deck.io.DeckSerializer").fromFile(
        java.JClass("java.io.File")(str(path))
    )
    registered = java.JClass("forge.game.player.RegisteredPlayer")(deck)
    registered.assignCommander()
    game = _new_game(java)
    owner = game.getPlayers().get(java.JInt(0))
    owner.initVariantsZones(registered)
    game.getAction().checkStaticAbilities(False)
    paper = (
        java.JClass("forge.model.FModel")
        .getMagicDb()
        .getCommonCards()
        .getCard("Command Tower")
    )
    tower = java.JClass("forge.game.card.Card").fromPaperCard(paper, owner)
    owner.getZone(java.JClass("forge.game.zone.ZoneType").Battlefield).add(tower)
    ability = tower.getManaAbilities().get(java.JInt(0))
    snapshot = java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()
    snapshot.getAction().checkStaticAbilities(False)
    copied_owner = snapshot.getPlayers().get(java.JInt(0))
    return {
        "snapshot_is_independent": bool(snapshot != game),
        "snapshot_identity_mask": int(copied_owner.getCommanderColorID().getColor()),
        "snapshot_choices": {
            str(card.getName()): int(card.getChosenCommanderColor())
            for card in copied_owner.getCommanders()
        },
        "id": case_id,
        "deck_sha256": file_hash(path),
        "main_count": deck.getMain().countAll(),
        "identity_mask": int(owner.getCommanderColorID().getColor()),
        "tower_colors": str(ability.getManaPart().getComboColors(ability)),
        "commanders": [
            {
                "name": str(card.getName()),
                "pregame_color": int(card.getChosenCommanderColor()),
                "color_mask": int(card.getColor().getColor()),
                "is_commander": bool(card.isCommander()),
            }
            for card in owner.getCommanders()
        ],
    }


if __name__ == "__main__":
    main()
