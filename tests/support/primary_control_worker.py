"""Fixed paid primary-control choices and native future-turn outcomes."""

import hashlib
import json
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Trusted fixed scenarios; deck uploads cannot select executable code or paths.
# pylint: disable=wrong-import-position
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _postcombat
from tests.support.control_turn_worker import _player
from tests.support.decision_worker import _ability, _state
from tests.support.keyword_expiration_worker import _resolve
from tests.support.native_worker import initialize_test_engine
from tests.support.starting_life_worker import _pod
from tests.support.turn_schedule_worker import _advance, _next_turn
from tests.support.usage_worker import _add, _find

CARDS = {
    "worst": "Worst Fears",
    "mind": "Mindslaver",
    # Public MTG card title used in a fixed gameplay fixture.
    "secret": "Secret of Bloodbending",  # nosec B105
    "sorin": "Sorin Markov",
    "bracelet": "The Dominion Bracelet",
}
MODES = ("heuristic", "hybrid", "full")


def _world_fingerprint(game):
    """Observe original participants and every fixture card, including ordered zones."""
    observations = []
    for player in game.getRegisteredPlayers():
        observations.append(
            [
                int(player.getId()),
                int(player.getLife()),
                int(player.getManaPool().totalMana()),
            ]
        )
        for zone_type in player.ALL_ZONES:
            zone = player.getZone(zone_type)
            if zone is not None:
                observations.append(
                    [str(zone_type), [int(card.getId()) for card in zone.getCards()]]
                )
    for card in game.getCardsInGame():
        observations.append(
            [
                int(card.getId()),
                bool(card.isTapped()),
                int(card.getController().getId()),
                int(card.getNetPower()),
                int(card.getNetToughness()),
                sorted(
                    (str(entry.getElement()), int(entry.getCount()))
                    for entry in card.getCounters().entrySet()
                ),
            ]
        )
    return hashlib.sha256(
        json.dumps(observations, ensure_ascii=True).encode("utf-8")
    ).hexdigest()


def _board(java, helpful):
    game, actor = _pod(java, 40)
    zones = java.JClass("forge.game.zone.ZoneType")
    for seat, player in enumerate(game.getRegisteredPlayers()):
        player.getZone(zones.Library).setCards(
            java.JClass("forge.game.card.CardCollection")()
        )
        for _ in range(8):
            if helpful or seat == 0:
                for land in ("Swamp", "Mountain", "Island"):
                    _add(java, land, player, zones.Battlefield)
            _add(
                java,
                "Forest" if helpful else "Darksteel Colossus",
                player,
                zones.Library,
            )
    _postcombat(java, game, actor)
    if helpful:
        actor.setLife(2, None)
        _add(java, "Shivan Dragon", _player(java, game, 1), zones.Battlefield)
    return game, actor, zones


def _source(java, game, actor, zones, key):
    name = CARDS[key]
    card = _add(java, name, actor, zones.Hand)
    identities = [int(card.getId())]
    setup_paid = []
    if key not in {"worst", "secret"}:
        setup_paid.append(_resolve(java, game, card.getFirstSpellAbility(), actor))
        card = _find(game, card.getId())
    if key == "sorin":
        # Explicit previously accumulated loyalty; not a claimed activation history.
        counters = java.JClass("com.google.common.collect.HashMultiset").create()
        counters.add(
            java.JClass("forge.game.card.CounterEnumType").LOYALTY, java.JInt(8)
        )
        card.setCounters(counters)
    if key == "bracelet":
        creature = _add(java, "Ornithopter", actor, zones.Battlefield)
        creature.setTapped(True)
        equip = next(
            action
            for action in card.getSpellAbilities()
            if str(action.getApi()) == "Attach"
        )
        setup_paid.append(_resolve(java, game, equip, actor, creature))
        identities.append(int(creature.getId()))
        card = creature
    return card, identities, setup_paid


def _choose(java, actor, mode):
    options = java.JClass("forge.ai.AIOption")
    actor.getController().getAi().setUseSimulation(
        None
        if mode == "heuristic"
        else (
            options.USE_HYBRID_SIMULATION
            if mode == "hybrid"
            else options.USE_FULL_SIMULATION
        )
    )
    randoms = java.JClass("forge.util.MyRandom")
    saved = randoms.getRandom()
    actions = actor.getController().chooseSpellAbilityToPlay()
    return (
        None if actions is None or actions.isEmpty() else actions.get(java.JInt(0))
    ), bool(randoms.getRandom() == saved)


def _result(java, game, identities, key):
    state = _state(java, game, identities)
    host = _find(game, identities[-1])
    state["host_power"] = int(host.getNetPower())
    state["loyalty"] = (
        int(host.getCounters(java.JClass("forge.game.card.CounterEnumType").LOYALTY))
        if key == "sorin"
        else None
    )
    return state


def _normal_cost_only(actor):
    """Leave exactly four untapped Islands, making the optional ten-mana cost unavailable."""
    available_blue = 0
    for land in actor.getLandsInPlay():
        usable = str(land.getName()) == "Island" and available_blue < 4
        land.setTapped(not usable)
        available_blue += int(usable)


def _follow_prefixes(java, game, actor, row, action):
    """Execute actual bounded plan prefixes without supplying a desired activation."""
    for _ in range(3):
        if (
            action is None
            or str(action.getApi()) != "Attach"
            or row["key"] != "bracelet"
        ):
            break
        prefix = _ability(game, action)
        prefix["paid"] = _resolve(java, game, action, actor)
        row["prefixes"].append(prefix)
        action, restored = _choose(java, actor, row["mode"])
        row["rng_restored"] &= restored
    return action


def _positive(java, key, mode, normal=False):
    game, actor, zones = _board(java, True)
    card, identities, setup_paid = _source(java, game, actor, zones, key)
    if normal:
        _normal_cost_only(actor)
    before = _state(java, game, identities)
    fingerprint = _world_fingerprint(game)
    action, restored = _choose(java, actor, mode)
    row = {
        "case": ("normal_secret" if normal else key) + "_" + mode,
        "key": key,
        "mode": mode,
        "normal": normal,
        "host_id": int(card.getId()),
        "setup_paid": setup_paid,
        "before": before,
        "after_choice": _state(java, game, identities),
        "world_unchanged": fingerprint == _world_fingerprint(game),
        "rng_restored": restored,
        "prefixes": [],
        "selected": None,
    }
    action = _follow_prefixes(java, game, actor, row, action)
    row["selected"] = None if action is None else _ability(game, action)
    if action is None or str(action.getApi()) != "ControlPlayer":
        return row
    # No test supplies a missing target: payment uses the AI's actual selection.
    row["paid"] = _resolve(java, game, action, actor)
    row["after_payment"] = _result(java, game, identities, key)
    _next_turn(java, game)
    row["acquired"] = _state(java, game, identities)
    _advance(java, game, "MAIN2")
    row["after"] = _result(java, game, identities, key)
    return row


def _negative(java, key):
    game, actor, zones = _board(java, False)
    card, identities, setup_paid = _source(java, game, actor, zones, key)
    action = next(
        candidate
        for candidate in card.getSpellAbilities()
        if str(candidate.getApi()) == "ControlPlayer"
    )
    action.setActivatingPlayer(actor)
    before = _state(java, game, identities)
    fingerprint = _world_fingerprint(game)
    can_pay = bool(
        java.JClass("forge.ai.ComputerUtilCost").canPayCost(action, actor, False)
    )
    decision = (
        java.JClass("forge.ai.SpellApiToAi")
        .Converter.get(action)
        .canPlayWithSubs(actor, action)
    )
    return {
        "case": "decline_" + key,
        "key": key,
        "setup_paid": setup_paid,
        "can_pay": can_pay,
        "decision": str(decision.decision()),
        "willing": bool(decision.willingToPlay()),
        "before": before,
        "after_choice": _state(java, game, identities),
        "world_unchanged": fingerprint == _world_fingerprint(game),
    }


def main() -> None:
    """Write four bounded fixed-name receipt parts for twenty-three actual scenarios."""
    java, engine = initialize_test_engine()
    run = Path(sys.argv[1]).resolve()
    parts = []
    for mode in (*MODES, "decline"):
        rows = (
            [_negative(java, key) for key in CARDS]
            if mode == "decline"
            else [_positive(java, key, mode) for key in CARDS]
            + [_positive(java, "secret", mode, True)]
        )
        path = run / ("primary-control-" + mode + ".json")
        write_bounded_json(path, {"cases": rows})
        parts.append({"name": path.name, "sha256": file_hash(path), "cases": len(rows)})
    manifest = {"engine": engine, "parts": parts}
    manifest["default_profile_sha256"] = file_hash(
        read_settings().home / "res/ai/Default.ai"
    )
    write_bounded_json(run / "primary-control.json", manifest)


if __name__ == "__main__":
    main()
