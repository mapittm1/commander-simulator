"""Paid protection deadlines in the packaged native runtime."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted test programs, independent of uploaded deck contents.
# pylint: disable=wrong-import-position
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _postcombat
from tests.support.delayed_worker import _settle, _setup
from tests.support.duration_worker import _snapshot, _targeted
from tests.support.native_worker import initialize_test_engine
from tests.support.turn_schedule_worker import _next_turn
from tests.support.usage_worker import _add, _cast, _find


def _state(java, game, lion_id, knight_id):
    protection = java.JClass("forge.game.keyword.Keyword").PROTECTION
    return {
        "creature": bool(_find(game, lion_id).hasKeyword(protection)),
        "player": bool(game.getPlayers().get(java.JInt(0)).hasKeyword(protection)),
        "intrinsic": bool(_find(game, knight_id).hasKeyword("Protection from black")),
    }


def _case(java, name):
    game, owner, zones = _setup(java, ("Plains",) * 8)
    _postcombat(java, game, owner)
    lion = _add(java, "Silvercoat Lion", owner, zones.Battlefield)
    knight = _add(java, "White Knight", owner, zones.Battlefield)
    if name == "Shelter":
        paid = _targeted(java, game, name, owner, lion)
    else:
        _, paid = _cast(java, game, name, owner, zones)
    _settle(game)
    before = _state(java, game, lion.getId(), knight.getId())
    copied, gaps = _snapshot(java, game)
    copied_before = _state(java, copied, lion.getId(), knight.getId())
    _next_turn(java, copied)
    after = _state(java, copied, lion.getId(), knight.getId())
    source = _state(java, game, lion.getId(), knight.getId())
    _next_turn(java, game)
    return {
        "card": name,
        "paid": paid,
        "before": before,
        "copied_before": copied_before,
        "after": after,
        "source": source,
        "source_after": _state(java, game, lion.getId(), knight.getId()),
        "unmapped": gaps,
    }


def main() -> None:
    """Retain independent before/after states without replaying the paid spells."""
    java, engine = initialize_test_engine()
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "protection-state.json",
        {
            "engine": engine,
            "cases": [
                _case(java, name)
                for name in ("Shelter", "Brave the Elements", "Seht's Tiger")
            ],
        },
    )


if __name__ == "__main__":
    main()
