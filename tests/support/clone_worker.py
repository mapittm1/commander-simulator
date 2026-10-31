"""Fixed paid temporary-copy and temporary-token-keyword witnesses."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# All fixture programs and paths are fixed trusted code, never uploaded input.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _form, _postcombat
from tests.support.delayed_worker import _settle, _setup
from tests.support.duration_worker import _snapshot, _targeted
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.turn_schedule_worker import _next_turn, _seat
from tests.support.usage_worker import _activated, _add, _cast, _find


def _queue_copy(java, game, source, target):
    ability = _activated(source, "Clone", source.getController())
    ability.getTargets().add(target)
    _pay(java, game, ability, source.getController())
    return len(game.getStack().peekAbility().getPayingMana())


def _mirage(java, name):
    game, owner, zones = _setup(java, ("Island",) * 5)
    _postcombat(java, game, owner)
    target = _add(java, name, game.getPlayers().get(java.JInt(1)), zones.Battlefield)
    source, paid = _cast(java, game, "Mirage Mirror", owner, zones)
    activation = _queue_copy(java, game, source, target)
    game.getStack().resolveStack()
    _settle(game)
    before = _form(java, source)
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    return {
        "case": "mirage",
        "target": name,
        "paid": [paid, activation],
        "before": before,
        "after": _form(java, _find(copied, source.getId())),
        "source": _form(java, source),
        "unmapped": gaps,
    }


def _mirrorweave(java):
    game, owner, zones = _setup(java, ("Plains",) * 4)
    _postcombat(java, game, owner)
    cards = [
        _add(java, "Grizzly Bears", owner, zones.Battlefield),
        _add(
            java,
            "Runeclaw Bear",
            game.getPlayers().get(java.JInt(1)),
            zones.Battlefield,
        ),
    ]
    angel = _add(java, "Serra Angel", owner, zones.Battlefield)
    paid = _targeted(java, game, "Mirrorweave", owner, angel)
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    return {
        "case": "mirrorweave",
        "paid": paid,
        "after": [_form(java, _find(copied, card.getId())) for card in cards],
        "source": [_form(java, card) for card in cards],
        "target": _form(java, _find(copied, angel.getId())),
        "unmapped": gaps,
    }


def _shapesharer(java):
    game, owner, zones = _setup(java, ("Island",) * 5)
    _postcombat(java, game, owner)
    source, paid = _cast(java, game, "Shapesharer", owner, zones)
    angel = _add(java, "Serra Angel", owner, zones.Battlefield)
    ability = _activated(source, "Pump", owner)
    ability.getTargets().add(source)
    ability.getSubAbility().getTargets().add(angel)
    _pay(java, game, ability, owner)
    activation = len(game.getStack().peekAbility().getPayingMana())
    game.getStack().resolveStack()
    _settle(game)
    copied, gaps = _snapshot(java, game)
    turns = []
    for _ in range(4):
        _next_turn(java, copied)
        turns.append(_form(java, _find(copied, source.getId())))
    return {
        "case": "shapesharer",
        "paid": [paid, activation],
        "turns": turns,
        "source": _form(java, source),
        "unmapped": gaps,
    }


def _vesuvan(java):
    game, owner, zones = _setup(java, ("Island",) * 5)
    _postcombat(java, game, owner)
    _add(java, "Serra Angel", owner, zones.Battlefield)
    source, paid = _cast(java, game, "Vesuvan Shapeshifter", owner, zones)
    copied, gaps = _snapshot(java, game)
    target = _find(copied, source.getId())
    if not target.turnFaceDown(True):
        raise ValueError(
            "Fixed copy-duration fixture could not invoke its native facedown event"
        )
    _settle(copied)
    return {
        "case": "vesuvan",
        "paid": paid,
        "face_down": bool(target.isFaceDown()),
        "after_frames": len(target.getCloneStates()),
        "after": _form(java, target),
        "source_frames": len(source.getCloneStates()),
        "source": _form(java, source),
        "unmapped": gaps,
    }


def _tokens(java, game):
    keywords = java.JClass("forge.game.keyword.Keyword")
    zone = java.JClass("forge.game.zone.ZoneType").Battlefield
    return sorted(
        [
            {
                "id": int(card.getId()),
                "owner": _seat(game, card.getOwner()),
                "haste": bool(card.hasKeyword(keywords.HASTE)),
                "menace": bool(card.hasKeyword(keywords.MENACE)),
                "vigilance": bool(card.hasKeyword(keywords.VIGILANCE)),
                "power": int(card.getNetPower()),
            }
            for card in game.getCardsIn(zone)
            if card.isToken()
        ],
        key=lambda row: row["id"],
    )


def _eiganjo(java):
    game, owner, zones = _setup(java, ("Plains", "Plains", "Mountain", "Mountain"))
    _postcombat(java, game, owner)
    spell = _add(java, "Eiganjo Uprising", owner, zones.Hand).getFirstSpellAbility()
    spell.setXManaCostPaid(java.JInt(2))
    _pay(java, game, spell, owner)
    paid = len(game.getStack().peekAbility().getPayingMana())
    game.getStack().resolveStack()
    _settle(game)
    before = _tokens(java, game)
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    return {
        "case": "eiganjo",
        "paid": paid,
        "before": before,
        "after": _tokens(java, copied),
        "source": _tokens(java, game),
        "unmapped": gaps,
    }


def _overlap(java):
    game, owner, zones = _setup(java, ("Island",) * 7)
    _postcombat(java, game, owner)
    source, paid = _cast(java, game, "Mirage Mirror", owner, zones)
    targets = [
        _add(java, name, owner, zones.Battlefield)
        for name in ("Serra Angel", "Grizzly Bears")
    ]
    activations = [_queue_copy(java, game, source, target) for target in targets]
    game.getStack().resolveStack()
    game.getStack().resolveStack()
    _settle(game)
    before = len(source.getCloneStates())
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    target = _find(copied, source.getId())
    return {
        "case": "overlap",
        "paid": [paid, *activations],
        "before_frames": before,
        "after_frames": len(target.getCloneStates()),
        "after": _form(java, target),
        "source_frames": len(source.getCloneStates()),
        "source": _form(java, source),
        "unmapped": gaps,
    }


def _blink(java):
    game, owner, zones = _setup(java, ("Plains",) * 6)
    _postcombat(java, game, owner)
    angel = _add(java, "Serra Angel", owner, zones.Battlefield)
    source, paid = _cast(java, game, "Mirage Mirror", owner, zones)
    activation = _queue_copy(java, game, source, angel)
    game.getStack().resolveStack()
    _settle(game)
    response = _targeted(java, game, "Cloudshift", owner, source)
    current = _find(game, source.getId())
    current.addRemembered("new-incarnation")
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    target = _find(copied, source.getId())
    return {
        "case": "blink",
        "paid": [paid, activation, response],
        "after": _form(java, target),
        "retained": bool(target.isRemembered("new-incarnation")),
        "source": _form(java, current),
        "source_retained": bool(current.isRemembered("new-incarnation")),
        "unmapped": gaps,
    }


def _cursed(java):
    game, owner, zones = _setup(java, ("Mountain",) * 3)
    _postcombat(java, game, owner)
    _add(java, "Serra Angel", owner, zones.Battlefield)
    source, paid = _cast(java, game, "Cursed Mirror", owner, zones)
    before = _form(java, source)
    copied, gaps = _snapshot(java, game)
    _next_turn(java, copied)
    return {
        "case": "cursed",
        "paid": paid,
        "before": before,
        "after": _form(java, _find(copied, source.getId())),
        "source": _form(java, source),
        "unmapped": gaps,
    }


def main() -> None:
    """Record ten paid card paths plus one controlled native face-down event."""
    java, engine = initialize_test_engine()
    cases = [
        _mirage(java, name)
        for name in ("Serra Angel", "Forest", "Sol Ring", "Glorious Anthem")
    ]
    cases.extend(
        (_mirrorweave(java), _shapesharer(java), _vesuvan(java), _eiganjo(java))
    )
    cases.extend((_overlap(java), _blink(java), _cursed(java)))
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "clone-state.json",
        {"engine": engine, "cases": cases},
    )


if __name__ == "__main__":
    main()
