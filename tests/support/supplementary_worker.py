"""Fixed opt-in test worker for native supplementary pregame loading."""

import importlib
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed test-only checkout bootstrap; no uploaded modules.
# pylint: disable=wrong-import-position
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.interactions.fixtures import _new_game
from commander.engine.interactions.native_bridge import initialize_native_engine
from commander.engine.interactions.runner import _jvm_library, require_bridge
from commander.engine.interactions.suite import write_bounded_json


def main():
    """Load exported sections through the native deck reader and pregame setup."""
    run = Path(sys.argv[1]).resolve()
    settings = read_settings()
    engine = settings.verify()
    require_bridge()
    java = importlib.import_module("jpype")
    initialize_native_engine(
        java, _jvm_library(settings.java), [str(settings.home / engine["jar_name"])]
    )
    selected = _observe(java, run / "selected.dck")
    empty = _observe(java, run / "empty.dck")
    write_bounded_json(
        run / "supplementary-observations.json",
        {
            "engine": engine,
            "selected": selected,
            "empty": empty,
            "selected_sha256": file_hash(run / "selected.dck"),
            "empty_sha256": file_hash(run / "empty.dck"),
        },
        indent=2,
    )


def _observe(java, path):
    deck = java.JClass("forge.deck.io.DeckSerializer").fromFile(
        java.JClass("java.io.File")(str(path))
    )
    registered = java.JClass("forge.game.player.RegisteredPlayer")(deck)
    game = _new_game(java)
    player = game.getPlayers().get(java.JInt(0))
    player.initVariantsZones(registered)
    zone = java.JClass("forge.game.zone.ZoneType")
    snapshot = java.JClass("forge.ai.simulation.GameCopier")(game).makeCopy()
    copied_player = snapshot.getPlayers().get(java.JInt(0))
    return {
        "independent_copy": bool(snapshot != game),
        "copied_sheets": sorted(
            str(card.getName()) for card in copied_player.getCardsIn(zone.StickerSheets)
        ),
        "copied_attractions": sorted(
            str(card.getName())
            for card in copied_player.getCardsIn(zone.AttractionDeck)
        ),
        "registered_sheets": sorted(
            str(card.getName()) for card in registered.getStickerSheets()
        ),
        "registered_attractions": sorted(
            str(card.getName()) for card in registered.getAttractions()
        ),
        "chosen_sheets": sorted(
            str(card.getName()) for card in player.getCardsIn(zone.StickerSheets)
        ),
        "attraction_deck": sorted(
            str(card.getName()) for card in player.getCardsIn(zone.AttractionDeck)
        ),
        "library": len(player.getCardsIn(zone.Library)),
        "battlefield": len(player.getCardsIn(zone.Battlefield)),
        "main_count": deck.getMain().countAll(),
    }


if __name__ == "__main__":
    main()
