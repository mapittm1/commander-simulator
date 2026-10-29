"""Fixed native integration worker for serialized Attraction functional variants."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

# Fixed checkout bootstrap; no uploaded worker modules or paths.
# pylint: disable=wrong-import-position
from commander.engine.forge_config import file_hash
from commander.engine.interactions.fixtures import _new_game
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from tests.support.native_worker import initialize_test_engine, load_registered_deck


def main():
    """Load actual exports, observe registered objects and independent AI copies."""
    run = Path(sys.argv[1]).resolve()
    java, engine = initialize_test_engine()
    path = run / "attraction-inputs.json"
    plan = read_bounded_json(path)
    rows = [
        _observe(java, run, choice, number)
        for number, choice in enumerate(plan["choices"])
    ]
    write_bounded_json(
        run / "attraction-receipt.json",
        {"engine": engine, "inputs_sha256": file_hash(path), "cases": rows},
        indent=2,
    )


def _observe(java, run, choice, number):
    path = run / f"attraction-{number}.dck"
    registered = load_registered_deck(java, path)
    game = _new_game(java)
    actor = game.getPlayers().get(java.JInt(0))
    actor.initVariantsZones(registered)
    snapshot = java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()
    copied_actor = snapshot.getPlayers().get(java.JInt(0))
    return {
        "id": choice["id"],
        "deck_sha256": file_hash(path),
        "original": _selection(java, actor, choice),
        "copied": _selection(java, copied_actor, choice),
        "independent_copy": bool(snapshot != game),
    }


def _selection(java, player, choice):
    cards = list(
        player.getCardsIn(java.JClass("forge.game.zone.ZoneType").AttractionDeck)
    )
    selected = [card for card in cards if str(card.getName()) == choice["name"]]
    if len(selected) != 1:
        raise ValueError("Expected exactly one selected Attraction in its native zone")
    card = selected[0]
    paper = card.getPaperCard()
    return {
        "name": str(card.getName()),
        "set": str(paper.getEdition()).lower(),
        "collector_number": str(paper.getCollectorNumber()),
        "attraction_lights": sorted(int(light) for light in card.getAttractionLights()),
        "zone_size": len(cards),
        "is_attraction": bool(card.isAttraction()),
    }


if __name__ == "__main__":
    main()
