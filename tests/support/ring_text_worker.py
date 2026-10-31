"""Paid Ring progression, bearer loss, edited words and exchanged text boxes."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted native test programs, independent of uploaded decks or code.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _postcombat
from tests.support.delayed_worker import _settle, _setup
from tests.support.duration_worker import _snapshot
from tests.support.keyword_expiration_worker import _resolve, _targeted
from tests.support.native_worker import initialize_test_engine
from tests.support.turn_schedule_worker import _advance, _next_turn
from tests.support.usage_worker import _add, _cast, _find


def _pod(java):
    game, owner, zones = _setup(java, ())
    for player in game.getPlayers():
        for name in ("Island",) * 12 + ("Plains",) * 4 + ("Mountain",) * 3:
            _add(java, name, player, zones.Battlefield)
        for _ in range(7):
            _add(java, "Forest", player, zones.Library)
    _postcombat(java, game, owner)
    return game, owner, zones


def _cast_settled(java, game, name, owner, zones):
    card, paid = _cast(java, game, name, owner, zones)
    _settle(game)
    return card, paid


def _word_state(card, white, blue):
    return {
        "words": {str(k): str(v) for k, v in card.getChangedTextColorWords().items()},
        "power": [int(white.getNetPower()), int(blue.getNetPower())],
    }


def _words(java, permanent):
    game, owner, zones = _pod(java)
    honor, paid = _cast_settled(java, game, "Honor of the Pure", owner, zones)
    white = _add(java, "Silvercoat Lion", owner, zones.Battlefield)
    blue = _add(java, "Coral Merfolk", owner, zones.Battlefield)
    _add(java, "Savannah Lions", owner, zones.Hand)
    cost = _targeted(
        java, game, "Sleight of Mind" if permanent else "Whim of Volrath", owner, honor
    )
    observations = _observe_words(java, game, (honor, white, blue))
    return {
        "case": "words_permanent" if permanent else "words_temporary",
        "paid": [paid, cost],
        **observations,
    }


def _observe_words(java, game, cards):
    observations = {"before": _word_state(*cards)}
    copied, gaps = _snapshot(java, game)
    mapped = [_find(copied, card.getId()) for card in cards]
    observations["copied_before"] = _word_state(*mapped)
    _next_turn(java, copied)
    observations["after"] = _word_state(*mapped)
    observations["source"] = _word_state(*cards)
    _next_turn(java, game)
    observations["source_after"] = _word_state(*cards)
    return {**observations, "unmapped": gaps}


def _box(card):
    return {
        "flying": bool(card.hasKeyword("Flying")),
        "vigilance": bool(card.hasKeyword("Vigilance")),
        "triggers": len(card.getTriggers()),
        "destroy": any(
            a.isActivatedAbility() and str(a.getApi()) == "Destroy"
            for a in card.getSpellAbilities()
        ),
        "zone": str(card.getZone().getZoneType()),
        "damage": int(card.getDamage()),
        "memory": bool(card.isRemembered("new-incarnation")),
    }


def _exchange_setup(java, case):
    game, owner, zones = _pod(java)
    opponent = game.getPlayers().get(java.JInt(1))
    names = {
        "static": "Merfolk Sovereign",
        "trigger": "Wall of Omens",
        "replacement": "Cho-Manno, Revolutionary",
    }
    first = _add(java, names.get(case, "Serra Angel"), owner, zones.Battlefield)
    second = _add(
        java,
        "Royal Assassin" if case in {"keywords", "blink"} else "Grizzly Bears",
        opponent,
        zones.Battlefield,
    )
    host, paid = _cast_settled(java, game, "Exchange of Words", owner, zones)
    merfolk = []
    if case == "static":
        merfolk = [
            _add(java, "Merfolk of the Pearl Trident", player, zones.Battlefield)
            for player in (owner, opponent)
        ]
        _settle(game)
    if case == "blink":
        _targeted(java, game, "Cloudshift", opponent, second)
        second = _find(game, second.getId())
        second.addRemembered("new-incarnation")
    return game, first, second, host, merfolk, paid


def _exchange(java, case):
    game, first, second, host, merfolk, paid = _exchange_setup(java, case)
    before = [_box(card) for card in (first, second)]
    copied, gaps = _snapshot(java, game)
    mapped = [_find(copied, card.getId()) for card in (first, second)]
    result = {
        "case": "exchange_" + case,
        "paid": [paid],
        "before": before,
        "copied_before": [_box(card) for card in mapped],
        "unmapped": gaps,
    }
    copied_owner = copied.getPlayers().get(java.JInt(0))
    result["paid"].append(
        _targeted(java, copied, "Disenchant", copied_owner, _find(copied, host.getId()))
    )
    result["after"] = [_box(card) for card in mapped]
    result["source"] = [_box(card) for card in (first, second)]
    if case == "static":
        result["merfolk_copy"] = [
            int(_find(copied, card.getId()).getNetPower()) for card in merfolk
        ]
        result["merfolk_source"] = [int(card.getNetPower()) for card in merfolk]
    elif case == "trigger":
        result["library_before_blink"] = len(
            copied_owner.getCardsIn(java.JClass("forge.game.zone.ZoneType").Library)
        )
        result["paid"].append(
            _targeted(java, copied, "Cloudshift", copied_owner, mapped[0])
        )
        result["library_after_blink"] = len(
            copied_owner.getCardsIn(java.JClass("forge.game.zone.ZoneType").Library)
        )
    elif case == "replacement":
        result["paid"].append(
            _targeted(java, copied, "Lightning Bolt", copied_owner, mapped[0])
        )
        result["copy_damage"] = _box(_find(copied, first.getId()))
        result["paid"].append(
            _targeted(
                java, game, "Lightning Bolt", game.getPlayers().get(java.JInt(0)), first
            )
        )
        result["source_damage"] = _box(_find(game, first.getId()))
    return result


def _ring_state(java, game, seat, identity):
    player = game.getPlayers().get(java.JInt(seat))
    ring = player.getTheRing()
    bearer = player.getRingBearer()
    physical = _find(game, identity)
    zones = java.JClass("forge.game.zone.ZoneType")
    return {
        "count": int(player.getNumRingTemptedYou()),
        "ring_id": None if ring is None else int(ring.getId()),
        "ring_triggers": None if ring is None else len(ring.getTriggers()),
        "ring_cards": sum(
            str(c.getName()) == "The Ring" for c in player.getCardsIn(zones.Command)
        ),
        "bearer_id": None if bearer is None else int(bearer.getId()),
        "physical_id": int(physical.getId()),
        "flag": bool(physical.isRingBearer()),
        "legendary": bool(physical.getType().isLegendary()),
        "zone": str(physical.getZone().getZoneType()),
    }


def _ring_setup(java, level, name="Grizzly Bears"):
    game, owner, zones = _pod(java)
    bearer = _add(java, name, owner, zones.Battlefield)
    costs = []
    for _ in range(level):
        _, paid = _cast_settled(java, game, "Birthday Escape", owner, zones)
        costs.append(paid)
    return game, owner, bearer, costs


def _ring_level(java, level):
    game, _, bearer, costs = _ring_setup(java, level)
    before = _ring_state(java, game, 0, bearer.getId())
    copied, gaps = _snapshot(java, game)
    result = {
        "case": "ring_level",
        "level": level,
        "paid": costs,
        "before": before,
        "copied_before": _ring_state(java, copied, 0, bearer.getId()),
        "unmapped": gaps,
    }
    _, paid = _cast_settled(
        java,
        copied,
        "Birthday Escape",
        copied.getPlayers().get(java.JInt(0)),
        java.JClass("forge.game.zone.ZoneType"),
    )
    result["paid"].append(paid)
    result["after"] = _ring_state(java, copied, 0, bearer.getId())
    result["source"] = _ring_state(java, game, 0, bearer.getId())
    return result


def _bearer_loss(java, control):
    game, _, bearer, costs = _ring_setup(java, 1)
    before = _ring_state(java, game, 0, bearer.getId())
    copied, gaps = _snapshot(java, game)
    copied_before = _ring_state(java, copied, 0, bearer.getId())
    if control:
        _next_turn(java, copied)
    actor = copied.getPlayers().get(java.JInt(1 if control else 0))
    paid = _targeted(
        java,
        copied,
        "Act of Treason" if control else "Unsummon",
        actor,
        _find(copied, bearer.getId()),
    )
    return {
        "case": "bearer_control" if control else "bearer_leave",
        "paid": [*costs, paid],
        "before": before,
        "copied_before": copied_before,
        "after": _ring_state(java, copied, 0, bearer.getId()),
        "source": _ring_state(java, game, 0, bearer.getId()),
        "unmapped": gaps,
    }


def _former_setup(java):
    game, first, zones = _pod(java)
    second = game.getPlayers().get(java.JInt(1))
    old = _add(java, "Wall of Stone", first, zones.Battlefield)
    _, paid = _cast_settled(java, game, "Birthday Escape", first, zones)
    _next_turn(java, game)
    control = _targeted(java, game, "Control Magic", second, old)
    _, tempted = _cast_settled(java, game, "Birthday Escape", second, zones)
    for _ in range(3):
        _next_turn(java, game)
    newer, free = _cast_settled(java, game, "Ornithopter", first, zones)
    _, again = _cast_settled(java, game, "Birthday Escape", first, zones)
    return game, old, newer, [paid, control, tempted, free, again]


def _former(java, copy_world):
    source, old, newer, costs = _former_setup(java)
    source_before = [
        _ring_state(java, source, seat, card.getId())
        for seat, card in ((0, newer), (1, old))
    ]
    game, gaps = _snapshot(java, source) if copy_world else (source, [])
    before = [
        _ring_state(java, game, seat, card.getId())
        for seat, card in ((0, newer), (1, old))
    ]
    costs.append(
        _targeted(
            java,
            game,
            "Cloudshift",
            game.getPlayers().get(java.JInt(1)),
            _find(game, old.getId()),
        )
    )
    return {
        "case": "former_copy" if copy_world else "former_original",
        "paid": costs,
        "source_before": source_before,
        "before": before,
        "after": [
            _ring_state(java, game, seat, card.getId())
            for seat, card in ((0, newer), (1, old))
        ],
        "source": [
            _ring_state(java, source, seat, card.getId())
            for seat, card in ((0, newer), (1, old))
        ],
        "unmapped": gaps,
    }


def _designation(java):
    game, owner, bearer, costs = _ring_setup(java, 1, "Wall of Stone")
    clone, paid = _cast_settled(
        java, game, "Clone", owner, java.JClass("forge.game.zone.ZoneType")
    )
    lki = java.JClass("forge.game.card.CardCopyService").getLKICopy(bearer)
    return {
        "case": "designation",
        "paid": [*costs, paid],
        "clone_flag": bool(clone.isRingBearer()),
        "clone_legendary": bool(clone.getType().isLegendary()),
        "lki_flag": bool(lki.isRingBearer()),
        "unmapped": [],
    }


def _illegal_pair(java):
    game, owner, zones = _pod(java)
    angel = _add(java, "Serra Angel", owner, zones.Battlefield)
    bogle = _add(
        java, "Slippery Bogle", game.getPlayers().get(java.JInt(1)), zones.Battlefield
    )
    _, paid = _cast_settled(java, game, "Exchange of Words", owner, zones)
    return {
        "case": "illegal_pair",
        "paid": [paid],
        "angel_flying": bool(angel.hasKeyword("Flying")),
        "bogle_flying": bool(bogle.hasKeyword("Flying")),
        "unmapped": [],
    }


def _fight(java, game, identity):
    owner = game.getPlayers().get(java.JInt(0))
    attacker = _find(game, identity)
    phases = java.JClass("forge.game.phase.PhaseType")
    zones = java.JClass("forge.game.zone.ZoneType")
    game.getPhaseHandler().devModeSet(
        phases.COMBAT_DECLARE_ATTACKERS, owner, java.JInt(1)
    )
    game.getPhaseHandler().setCombat(java.JClass("forge.game.combat.Combat")(owner))
    game.getCombat().addAttacker(attacker, game.getPlayers().get(java.JInt(1)))
    java.JClass("forge.game.combat.CombatUtil").checkDeclaredAttacker(
        game, attacker, game.getCombat(), True
    )
    _settle(game)
    result = {
        "library": len(owner.getCardsIn(zones.Library)),
        "hand": len(owner.getCardsIn(zones.Hand)),
    }
    _advance(java, game, "MAIN2")
    result["life"] = [int(player.getLife()) for player in game.getPlayers()]
    return result


def _combat(java, blocked):
    game, owner, bearer, costs = _ring_setup(java, 3 if blocked else 4)
    zones = java.JClass("forge.game.zone.ZoneType")
    wall = None
    if blocked:
        _add(java, "Forest", owner, zones.Battlefield)
        wall = _add(
            java,
            "Wall of Omens",
            game.getPlayers().get(java.JInt(1)),
            zones.Battlefield,
        )
        hunt = _add(java, "Hunt Down", owner, zones.Hand).getFirstSpellAbility()
        hunt.getTargets().add(bearer)
        hunt.getSubAbility().getTargets().add(wall)
        costs.append(_resolve(java, game, hunt, owner))
    before = {
        "library": len(owner.getCardsIn(zones.Library)),
        "hand": len(owner.getCardsIn(zones.Hand)),
    }
    copied, gaps = _snapshot(java, game)
    after = _fight(java, copied, bearer.getId())
    source = {
        "library": len(owner.getCardsIn(zones.Library)),
        "hand": len(owner.getCardsIn(zones.Hand)),
        "life": [int(player.getLife()) for player in game.getPlayers()],
    }
    if wall is not None:
        after["wall_zone"] = str(_find(copied, wall.getId()).getZone().getZoneType())
        source["wall_zone"] = str(wall.getZone().getZoneType())
    return {
        "case": "combat_block" if blocked else "combat_hit",
        "paid": costs,
        "before": before,
        "after": after,
        "source": source,
        "unmapped": gaps,
    }


def main() -> None:
    """Record copied-game outcomes and two original-game designation contracts."""
    java, engine = initialize_test_engine()
    cases = [_words(java, False), _words(java, True)]
    cases.extend(
        _exchange(java, case)
        for case in "keywords static trigger replacement blink".split()
    )
    cases.extend(_ring_level(java, level) for level in range(1, 5))
    cases.extend((_bearer_loss(java, False), _bearer_loss(java, True)))
    cases.extend(
        (
            _former(java, True),
            _former(java, False),
            _designation(java),
            _illegal_pair(java),
        )
    )
    cases.extend((_combat(java, False), _combat(java, True)))
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "ring-text.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
