"""Fixed partial-state tests of native meld formation and separation only."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

# Fixed test-only bootstrap; uploaded code cannot select this worker.
# pylint: disable=wrong-import-position
from commander.engine.forge_config import file_hash
from commander.engine.interactions.fixtures import _card, _new_game
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from tests.support.native_worker import initialize_test_engine


def main():
    """Resolve printed meld effects in fresh states; do not claim payment/timing coverage."""
    run = Path(sys.argv[1]).resolve()
    java, engine = initialize_test_engine()
    path = run / "meld-inputs.json"
    plan = read_bounded_json(path)
    rows = [
        _observe(java, pair, variant)
        for pair in plan["pairs"]
        for variant in plan["variants"]
    ]
    write_bounded_json(
        run / "meld-receipt.json",
        {"engine": engine, "inputs_sha256": file_hash(path), "cases": rows},
        indent=2,
    )


def _observe(java, pair, variant):
    game = _new_game(java)
    actor = game.getPlayers().get(java.JInt(0))
    zone = java.JClass("forge.game.zone.ZoneType")
    for player in game.getPlayers():
        player.setLife(40, None)
        _card(java, {"engine_name": "Forest"}, player, zone.Library)
    primary = _card(java, pair["primary"], actor, zone.Battlefield)
    _setup_secondary(java, game, pair, variant)
    game.getAction().checkStaticAbilities(False)
    game.getTriggerHandler().resetActiveTriggers()
    game.copyLastState()
    ability = _meld_ability(java, primary)
    ability.setActivatingPlayer(actor)
    if str(ability.getParam("Primary")) != pair["primary"]["engine_name"]:
        raise ValueError("Printed native primary does not match frozen identity")
    if str(ability.getParam("Secondary")) != pair["secondary"]["engine_name"]:
        raise ValueError("Printed native secondary does not match frozen identity")
    before = _zones(java, game, pair)
    java.JClass("forge.game.ability.effects.MeldEffect")().resolve(ability)
    after = _zones(java, game, pair)
    result = next(
        (
            card
            for card in actor.getCardsIn(zone.Battlefield)
            if str(card.getName()) == pair["result"]["name"]
        ),
        None,
    )
    result_state = _result_state(result)
    if result is not None and variant == "form_graveyard":
        game.getAction().moveToGraveyard(result, None)
    if result is not None and variant == "form_exile":
        game.getAction().exile(result, None, java.JClass("java.util.HashMap")())
    return {
        "result": pair["result"]["name"],
        "variant": variant,
        **result_state,
        "before": before,
        "after": after,
        "after_departure": _zones(java, game, pair),
    }


def _setup_secondary(java, game, pair, variant):
    actor, other = (game.getPlayers().get(java.JInt(index)) for index in (0, 1))
    zone = java.JClass("forge.game.zone.ZoneType")
    secondary = None
    if variant != "missing":
        owner = other if variant == "not_owned" else actor
        destination = zone.Graveyard if variant == "graveyard" else zone.Battlefield
        secondary = _card(
            java, pair["secondary"], owner, None if variant == "clone" else destination
        )
        if variant == "not_owned":
            other.getZone(zone.Battlefield).remove(secondary)
            actor.getZone(zone.Battlefield).add(secondary)
            secondary.setController(actor, game.getNextTimestamp())
        if variant == "not_controlled":
            actor.getZone(zone.Battlefield).remove(secondary)
            other.getZone(zone.Battlefield).add(secondary)
            secondary.setController(other, game.getNextTimestamp())
        if variant == "clone":
            template = secondary
            secondary = _card(java, {"engine_name": "Clone"}, actor, zone.Battlefield)
            states = java.JClass("forge.game.card.CardFactory").getCloneStates(
                template, secondary, secondary.getFirstSpellAbility()
            )
            secondary.addCloneState(states, game.getNextTimestamp())
            if (
                str(secondary.getName()) != pair["secondary"]["engine_name"]
                or str(secondary.getPaperCard().getName()) != "Clone"
            ):
                raise ValueError("Physical Clone control did not copy the meld part")


def _result_state(result):
    return {
        "formed": result is not None,
        "linked_name": (
            None
            if result is None or result.getMeldedWith() is None
            else str(result.getMeldedWith().getName())
        ),
        "state": None if result is None else str(result.getCurrentStateName()),
    }


def _meld_ability(java, primary):
    for ability in primary.getSpellAbilities():
        if str(ability.getApi()) == "Meld":
            return ability
    text = str(primary.getSVar("Meld"))
    if not text or "$ Meld" not in text:
        raise ValueError("Expected printed native meld ability is absent")
    return java.JClass("forge.game.ability.AbilityFactory").getAbility(text, primary)


def _zones(java, game, pair):
    names = {pair[role]["engine_name"] for role in ("primary", "secondary")}
    names.add(pair["result"]["name"])
    zone = java.JClass("forge.game.zone.ZoneType")
    return {
        str(location): sorted(
            str(card.getName())
            for card in game.getCardsIn(location)
            if str(card.getName()) in names
        )
        for location in (zone.Battlefield, zone.Graveyard, zone.Exile)
    }


if __name__ == "__main__":
    main()
