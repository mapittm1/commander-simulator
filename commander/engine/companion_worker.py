"""Private companion construction-rule worker; never starts or plays a game."""

import importlib
import sys
from pathlib import Path

from commander.engine.catalog_probe_io import source_hashes
from commander.engine.forge_config import EngineSettings, file_hash
from commander.engine.interactions.fixtures import _new_game
from commander.engine.interactions.native_bridge import initialize_native_engine
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.engine.native_runtime import require_bridge


def main():
    """Evaluate only frozen catalog identities through the pinned native rules engine."""
    path = Path(sys.argv[1]).resolve()
    plan = read_bounded_json(path)
    if plan["schema_version"] != 1 or plan["source_hashes"] != source_hashes():
        raise ValueError("Companion admission source/protocol changed")
    settings = EngineSettings(
        Path(plan["home"]),
        Path(plan["java"]),
        Path(plan["lock"]) if plan["lock"] else None,
    )
    if settings.verify() != plan["engine"]:
        raise ValueError("Companion admission engine changed")
    require_bridge()
    java = importlib.import_module("jpype")
    initialize_native_engine(
        java, plan["jvm"], [str(settings.home / plan["engine"]["jar_name"])]
    )
    observed = observe_admission(java, plan)
    write_bounded_json(
        path.with_name("companion-receipt.json"),
        {
            "schema_version": 1,
            "plan_sha256": file_hash(path),
            "engine": plan["engine"],
            "java_version": str(
                java.JClass("java.lang.System").getProperty("java.version")
            ),
            **observed,
        },
        indent=2,
    )


def observe_admission(java, plan):
    """Include the commander in the starting deck before checking the condition."""
    game = _new_game(java)
    player = game.getPlayers().get(java.JInt(0))
    zone = java.JClass("forge.game.zone.ZoneType")
    for entry in plan["starting"]:
        for _ in range(entry["quantity"]):
            _native_card(
                java,
                entry["engine_name"],
                player,
                zone.Library,
                entry.get("commander_color"),
            )
    companion = _native_card(
        java, plan["companion"]["engine_name"], player, zone.Sideboard
    )
    game.getAction().checkStaticAbilities(False)
    library = player.getCardsIn(zone.Library)
    if len(library) != 100:
        raise ValueError(
            "Companion admission requires the complete 100-card starting deck"
        )
    keywords = list(
        companion.getKeywords(java.JClass("forge.game.keyword.Keyword").COMPANION)
    )
    if len(keywords) != 1:
        raise ValueError("Expected exactly one native companion condition")
    keyword = keywords[0]
    if not keyword.hasSpecialRestriction():
        eligible = player.deckMatchesDeckRestriction(
            companion, keyword.getDeckRestriction()
        )
    else:
        eligible = _special_condition(player, library, keyword.getSpecialRules())
    return {
        "eligible": bool(eligible),
        "starting_cards": len(library),
        "companion_name": str(companion.getName()),
        "condition": str(keyword.getOriginal()),
    }


def _special_condition(player, library, rule):
    if rule == "UniqueManaSymbols":
        return player.allCardsUniqueManaSymbols()
    nonlands = [card for card in library if not card.isLand()]
    if rule == "UniqueNames":
        return len({str(card.getName()) for card in nonlands}) == len(nonlands)
    if rule == "DeckSizePlus20":
        # Commander has exactly 100 starting cards and cannot meet +20 (Yorion).
        return False
    if rule == "SharesCardType":
        types = [
            set(str(value) for value in card.getType().getCoreTypes())
            for card in nonlands
        ]
        return bool(set.intersection(*types)) if types else True
    raise ValueError("Unknown native companion restriction; no admission issued")


def _native_card(java, name, owner, zone, color=None):
    paper = (
        java.JClass("forge.model.FModel").getMagicDb().getCommonCards().getCard(name)
    )
    if paper is None or str(paper.getName()) != name:
        raise ValueError("Companion admission requires an exact native paper identity")
    if color is not None:
        mask = java.JClass("forge.card.MagicColor").fromName(color)
        paper = paper.copyWithMarkedColors(
            java.JClass("forge.card.ColorSet").fromMask(mask)
        )
    card = java.JClass("forge.game.card.Card").fromPaperCard(paper, owner)
    if color is not None:
        owner.initCommanderColor(card)
    owner.getZone(zone).add(card)
    return card


if __name__ == "__main__":
    main()
