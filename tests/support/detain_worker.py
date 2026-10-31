"""Paid Detain deadlines and haste-loss witnesses on controlled native boards."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted fixtures; uploaded decks never select executable programs.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _postcombat
from tests.support.duration_worker import _snapshot
from tests.support.keyword_expiration_worker import _resolve
from tests.support.native_worker import initialize_test_engine
from tests.support.turn_schedule_worker import _next_turn, _seat
from tests.support.usage_worker import _add, _cast, _find, _settle, _setup

KINDS = (
    "activation",
    "combat",
    "mass",
    "artifact",
    "same",
    "different",
    "blink",
    "control",
    "extra",
    "expired",
    "haste",
)


def _player(java, game, seat):
    return game.getPlayers().get(java.JInt(seat))


def _pod(java):
    game, owner, zones = _setup(java, ())
    for player in game.getPlayers():
        for name in ("Island", "Plains", "Forest", "Mountain"):
            for _ in range(8):
                _add(java, name, player, zones.Battlefield)
        for _ in range(20):
            _add(java, "Forest", player, zones.Library)
    _postcombat(java, game, owner)
    _next_turn(java, game)
    return game, zones


def _spell(java, game, name, seat, target):
    owner = _player(java, game, seat)
    zones = java.JClass("forge.game.zone.ZoneType")
    ability = _add(java, name, owner, zones.Hand).getFirstSpellAbility()
    ability.setActivatingPlayer(owner)
    if not ability.canTarget(target):
        raise ValueError("Fixed Detain witness selected an illegal target")
    return _resolve(java, game, ability, owner, target)


def _action(java, game, card):
    ability = next(a for a in card.getSpellAbilities() if a.isActivatedAbility())
    ability.setActivatingPlayer(card.getController())
    if ability.usesTargeting():
        zones = java.JClass("forge.game.zone.ZoneType")
        candidates = [_player(java, game, 0)] + list(
            _player(java, game, 0).getCardsIn(zones.Battlefield)
        )
        target = next(
            candidate for candidate in candidates if ability.canTarget(candidate)
        )
        if not ability.canTarget(target):
            raise ValueError("Fixed activated ability selected an illegal target")
        ability.getTargets().add(target)
    return ability


def _state(java, game, identities):
    history = java.JClass("forge.game.card.CardCopyService")
    combat = java.JClass("forge.game.combat.CombatUtil")
    limits = java.JClass("forge.game.staticability.StaticAbilityCantAttackBlock")
    defender = _player(java, game, 0)
    rows = []
    for identity in identities:
        card = _find(game, identity)
        lki = history.getLKICopy(card)
        row = {
            "id": int(card.getId()),
            "detained": bool(card.isDetained()),
            "view_detained": bool(card.getView().isDetained()),
            "lki_detained": bool(lki.isDetained()),
            "first_turn_controlled": bool(card.isFirstTurnControlled()),
            "lki_first_turn_controlled": bool(lki.isFirstTurnControlled()),
            "haste": bool(card.hasKeyword("Haste")),
            "sick": bool(card.isSick()),
            "tapped": bool(card.isTapped()),
            "phased_out": bool(card.isPhasedOut()),
            "activation": bool(_action(java, game, card).canPlay()),
            "controller": _seat(game, card.getController()),
            "zone": str(card.getZone().getZoneType()),
            "in_world": bool(
                card.getGame() == game
                and lki.getGame() == game
                and card.getController().getGame() == game
            ),
        }
        if card.isCreature():
            row.update(
                cant_attack=bool(limits.cantAttack(card, defender)),
                cant_block=bool(limits.cantBlock(card)),
                attacker_sick=bool(combat.isAttackerSick(card, defender)),
                can_attack=bool(combat.canAttack(card, defender)),
                can_block=bool(combat.canBlock(card)),
            )
        rows.append(row)
    turn = game.getPhaseHandler().getPlayerTurn()
    return {"cards": rows, "turn": _seat(game, turn), "extra": bool(turn.isExtraTurn())}


def _decree(java, game, targets):
    zones = java.JClass("forge.game.zone.ZoneType")
    owner = _player(java, game, 0)
    ability = _add(java, "Lyev Decree", owner, zones.Hand).getFirstSpellAbility()
    ability.setActivatingPlayer(owner)
    for target in targets:
        if not ability.canTarget(target):
            raise ValueError("Fixed mass Detain witness selected an illegal target")
        ability.getTargets().add(target)
    return _resolve(java, game, ability, owner)


def _prepare(java, kind):
    game, zones = _pod(java)
    opponent = _player(java, game, 1)
    paid = []
    if kind == "haste":
        _, cost = _cast(java, game, "Fervor", opponent, zones)
        paid.append(cost)
    target, cost = _cast(
        java,
        game,
        "Icy Manipulator" if kind == "artifact" else "Azure Mage",
        opponent,
        zones,
    )
    paid.append(cost)
    cards = [target]
    if kind == "mass":
        elf, cost = _cast(java, game, "Llanowar Elves", opponent, zones)
        cards.append(elf)
        paid.append(cost)
    for _ in range(3 if kind == "haste" else 7):
        _next_turn(java, game)
    if kind == "artifact":
        _, cost = _cast(java, game, "Lyev Skyknight", _player(java, game, 0), zones)
        _settle(game)
    elif kind == "mass":
        cost = _decree(java, game, cards)
    else:
        cost = _spell(java, game, "Inaction Injunction", 0, target)
    paid.append(cost)
    if kind == "same":
        paid.append(_spell(java, game, "Inaction Injunction", 0, target))
    if kind in {"different", "control"}:
        _next_turn(java, game)
        _next_turn(java, game)
        paid.append(
            _spell(
                java,
                game,
                "Inaction Injunction" if kind == "different" else "Act of Treason",
                2,
                target,
            )
        )
    if kind == "blink":
        paid.append(_spell(java, game, "Cloudshift", 1, target))
    if kind == "extra":
        paid.append(_spell(java, game, "Time Warp", 0, _player(java, game, 0)))
    if kind == "expired":
        for _ in range(4):
            _next_turn(java, game)
    return game, [int(card.getId()) for card in cards], paid


def _deadline(java, game, identities, kind):
    steps = 1 if kind == "extra" else 2 if kind in {"different", "control"} else 4
    states = []
    for _ in range(steps):
        _next_turn(java, game)
        states.append(_state(java, game, identities))
    if kind == "different":
        for _ in range(2):
            _next_turn(java, game)
            states.append(_state(java, game, identities))
    return states


def _detain(java, kind):
    game, identities, paid = _prepare(java, kind)
    before = _state(java, game, identities)
    copied, gaps = _snapshot(java, game)
    pending = _state(java, copied, identities)
    checkpoints = _deadline(java, copied, identities, kind)
    source = _state(java, game, identities)
    source_checkpoints = _deadline(java, game, identities, kind)
    return {
        "case": kind,
        "paid": paid,
        "before": before,
        "copied_before": pending,
        "checkpoints": checkpoints,
        "source": source,
        "source_checkpoints": source_checkpoints,
        "unmapped": gaps,
    }


def _haste_removal(java):
    game, zones = _pod(java)
    owner = _player(java, game, 1)
    fervor, cost = _cast(java, game, "Fervor", owner, zones)
    paid = [cost]
    wizard, cost = _cast(java, game, "Prodigal Pyromancer", owner, zones)
    paid.append(cost)
    identities = [int(wizard.getId())]
    observations = {"before": _state(java, game, identities)}
    copied, gaps = _snapshot(java, game)
    observations["copied_before"] = _state(java, copied, identities)
    paid.append(_spell(java, game, "Naturalize", 0, fervor))
    observations["source_after"] = _state(java, game, identities)
    observations["independent"] = _state(java, copied, identities)
    paid.append(_spell(java, copied, "Naturalize", 0, _find(copied, fervor.getId())))
    observations["after"] = _state(java, copied, identities)
    return {"case": "haste_removal", "paid": paid, **observations, "unmapped": gaps}


def main() -> None:
    """Retain eleven Detain cases and a paid haste-loss activation witness."""
    java, engine = initialize_test_engine()
    cases = [_detain(java, kind) for kind in KINDS]
    cases.append(_haste_removal(java))
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "detain.json", {"engine": engine, "cases": cases}
    )


if __name__ == "__main__":
    main()
