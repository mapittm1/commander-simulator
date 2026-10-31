"""Paid host-bound returns, linked exile and already-paid pending actions."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed native fixtures; uploaded card text cannot select code or paths.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _postcombat
from tests.support.duration_worker import _snapshot
from tests.support.keyword_expiration_worker import _resolve
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.usage_worker import _add, _cast, _find, _settle, _setup


def _pod(java):
    game, owner, zones = _setup(java, ())
    for player in game.getPlayers():
        for name in ("Plains",) * 10 + ("Island",) * 4 + ("Swamp",) * 4 + ("Forest",):
            _add(java, name, player, zones.Battlefield)
    _postcombat(java, game, owner)
    return game, owner, zones


def _card_rows(java, game, identities):
    counter = java.JClass("forge.game.card.CounterEnumType").P1P1
    rows = []
    for identity in identities:
        card = _find(game, identity)
        rows.append(
            {
                "id": int(card.getId()),
                "zone": str(card.getZone().getZoneType()),
                "owner": int(card.getOwner().getId()),
                "controller": int(card.getController().getId()),
                "counters": int(card.getCounters(counter)),
                "timestamp": int(card.getGameTimestamp()),
                "in_world": bool(card.getGame() == game),
            }
        )
    return rows


def _state(java, game, host, identities):
    zones = java.JClass("forge.game.zone.ZoneType")
    linked = list(host.getUntilLeavesBattlefield())
    return {
        "host_zone": str(_find(game, host.getId()).getZone().getZoneType()),
        "linked": sorted(int(card.getId()) for card in linked),
        "linked_world": all(card.getGame() == game for card in linked),
        "cards": _card_rows(java, game, identities),
        "tokens": sum(card.isToken() for card in game.getCardsIn(zones.Battlefield)),
    }


def _targets(java, game, owner, zones, kind):
    if kind == "hand":
        return [
            _add(
                java, "Lightning Bolt", game.getPlayers().get(java.JInt(1)), zones.Hand
            )
        ]
    targets = []
    if kind == "lagrella":
        targets.append(_add(java, "Runeclaw Bear", owner, zones.Battlefield))
    for seat in range(1, 4 if kind in {"grasp", "lagrella"} else 2):
        targets.append(
            _add(
                java,
                "Grizzly Bears",
                game.getPlayers().get(java.JInt(seat)),
                zones.Battlefield,
            )
        )
    return targets


def _prepare(java, kind):
    game, owner, zones = _pod(java)
    costs = {"preparation": 0}
    if kind == "token":
        _, costs["preparation"] = _cast(
            java, game, "Raise the Alarm", game.getPlayers().get(java.JInt(1)), zones
        )
        _settle(game)
        if sum(card.isToken() for card in game.getCardsIn(zones.Battlefield)) != 2:
            raise ValueError("Fixed return witness did not create two tokens")
    else:
        _targets(java, game, owner, zones, kind)
    name = {
        "hand": "Kitesail Freebooter",
        "priest": "Banisher Priest",
        "grasp": "Grasp of Fate",
        "lagrella": "Lagrella, the Magpie",
    }.get(kind, "Banishing Light")
    host, costs["host"] = _cast(java, game, name, owner, zones)
    _settle(game)
    identities = [int(card.getId()) for card in host.getUntilLeavesBattlefield()]
    if kind == "early":
        target = _find(game, identities[0])
        spell = _add(
            java, "Pull from Eternity", owner, zones.Hand
        ).getFirstSpellAbility()
        spell.setActivatingPlayer(owner)
        if not spell.canTarget(target):
            raise ValueError("Fixed early-move witness selected an illegal target")
        costs["preparation"] = _resolve(java, game, spell, owner, target)
    return game, host, identities, costs


def _remove(java, game, host, kind):
    owner = game.getPlayers().get(java.JInt(0))
    zones = java.JClass("forge.game.zone.ZoneType")
    name = (
        "Unsummon"
        if kind in {"priest", "hand", "lagrella"}
        else "Disperse" if kind == "bounce" else "Disenchant"
    )
    spell = _add(java, name, owner, zones.Hand).getFirstSpellAbility()
    spell.setActivatingPlayer(owner)
    if not spell.canTarget(host):
        raise ValueError("Fixed return witness selected an illegal host removal")
    return _resolve(java, game, spell, owner, host)


def _scenario(java, kind):
    game, host, identities, costs = _prepare(java, kind)
    observations = {"before": _state(java, game, host, identities)}
    copied, gaps = _snapshot(java, game)
    mapped = _find(copied, host.getId())
    observations["copied_before"] = _state(java, copied, mapped, identities)
    costs["copied_removal"] = _remove(java, copied, mapped, kind)
    observations["after"] = _state(java, copied, mapped, identities)
    observations["source"] = _state(java, game, host, identities)
    costs["source_removal"] = _remove(java, game, host, kind)
    observations["source_after"] = _state(java, game, host, identities)
    return {
        "case": kind,
        "paid": costs,
        "acting_id": int(game.getPlayers().get(java.JInt(0)).getId()),
        **observations,
        "unmapped": gaps,
    }


def _pending_setup(java, host_gone):
    if not host_gone:
        game, host, identities, costs = _prepare(java, "destroy")
        return game, host, identities, costs
    game, owner, zones = _pod(java)
    target = _add(
        java, "Grizzly Bears", game.getPlayers().get(java.JInt(1)), zones.Battlefield
    )
    host = _add(java, "Banishing Light", owner, zones.Hand)
    _pay(java, game, host.getFirstSpellAbility(), owner)
    costs = {
        "host": len(game.getStack().peekAbility().getPayingMana()),
        "preparation": 0,
    }
    game.getStack().resolveStack()
    game.getAction().checkStateEffects(True)
    game.getTriggerHandler().runWaitingTriggers()
    game.getStack().addAllTriggeredAbilitiesToStack()
    if str(game.getStack().peekAbility().getApi()) != "ChangeZone":
        raise ValueError("Fixed host witness did not retain the ETB trigger")
    return game, _find(game, host.getId()), [int(target.getId())], costs


def _pending(java, host_gone):
    game, host, identities, costs = _pending_setup(java, host_gone)
    zones = java.JClass("forge.game.zone.ZoneType")
    owner = game.getPlayers().get(java.JInt(0))
    removal = _add(java, "Disenchant", owner, zones.Hand).getFirstSpellAbility()
    removal.setActivatingPlayer(owner)
    if not removal.canTarget(host):
        raise ValueError("Fixed pending witness selected an illegal host")
    removal.getTargets().add(host)
    _pay(java, game, removal, owner)
    costs["removal"] = len(game.getStack().peekAbility().getPayingMana())
    if host_gone:
        game.getStack().resolveStack()
    return _observe_pending(java, game, (host, identities), costs, host_gone)


def _observe_pending(java, game, cards, costs, host_gone):
    host, identities = cards
    observations = {
        "before": _state(java, game, host, identities),
        "source_stack": int(game.getStack().size()),
    }
    copied, gaps = _snapshot(java, game)
    mapped = _find(copied, host.getId())
    observations["copied_before"] = _state(java, copied, mapped, identities)
    observations["copied_stack"] = int(copied.getStack().size())
    observations["pending_paid"] = len(copied.getStack().peekAbility().getPayingMana())
    copied.getStack().resolveStack()
    _settle(copied)
    observations["after"] = _state(java, copied, mapped, identities)
    observations["source"] = _state(java, game, host, identities)
    game.getStack().resolveStack()
    _settle(game)
    observations["source_after"] = _state(java, game, host, identities)
    return {
        "case": "host_gone" if host_gone else "pending_removal",
        "paid": costs,
        "acting_id": int(game.getPlayers().get(java.JInt(0)).getId()),
        **observations,
        "unmapped": gaps,
    }


def main() -> None:
    """Retain native paid returns, target identities and independent worlds."""
    java, engine = initialize_test_engine()
    cases = [
        _scenario(java, kind)
        for kind in (
            "destroy",
            "bounce",
            "priest",
            "grasp",
            "hand",
            "lagrella",
            "early",
            "token",
        )
    ]
    cases.extend((_pending(java, False), _pending(java, True)))
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "host-return.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
