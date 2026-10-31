"""Fixed paid controlled-attack decisions and native combat outcomes."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Trusted fixed scenarios; uploaded data never chooses an executable program.
# pylint: disable=wrong-import-position
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.interactions.suite import write_bounded_json
from tests.support.control_turn_worker import _player, _pod, _schedule
from tests.support.decision_worker import _state as _decision_state
from tests.support.native_worker import initialize_test_engine
from tests.support.turn_schedule_worker import _advance, _next_turn, _seat
from tests.support.usage_worker import _add, _settle

KINDS = (
    "ordinary",
    "controlled",
    "forced",
    "only_master",
    "optional_tax",
    "cap",
    "chain",
    "self",
    "blocked",
    "paid_tax",
    "group",
    "combat_only",
    "timeout",
)
CASES = tuple(kind + "_" + mode for kind in KINDS for mode in ("heuristic", "full"))
PERMANENTS = {
    "only_master": ((2, "Blazing Archon"), (3, "Blazing Archon")),
    "optional_tax": ((2, "Blazing Archon"), (3, "Blazing Archon"), (0, "Propaganda")),
    "cap": ((0, "Silent Arbiter"),),
    "blocked": ((3, "Blazing Archon"), (2, "Baneslayer Angel")),
    "paid_tax": ((0, "Blazing Archon"), (3, "Blazing Archon"), (2, "Propaganda")),
}


def _state(java, game, identities):
    state = _decision_state(java, game, identities)
    cards = {int(card.getId()): card for card in game.getCardsInGame()}
    for row in state["cards"]:
        card = cards.get(row["id"])
        row["tapped"] = None if card is None else bool(card.isTapped())
        row["controller"] = None if card is None else _seat(game, card.getController())
    return state


def _prepare(java, case):
    kind, mode = case.rsplit("_", 1)
    game = _pod(java)
    zones = java.JClass("forge.game.zone.ZoneType")
    actor = _player(java, game, 1)
    owner = _player(java, game, 0)
    owner.setLife(2, None)
    creature = (
        "Juggernaut"
        if kind in {"forced", "only_master", "optional_tax"}
        else "Mogg Flunkies" if kind == "group" else "Shivan Dragon"
    )
    attackers = [
        int(_add(java, creature, actor, zones.Battlefield).getId())
        for _ in range(2 if kind in {"cap", "group"} else 1)
    ]
    context = {
        "kind": kind,
        "ids": list(attackers),
        "attackers": attackers,
        "setup_paid": [],
        "beneficiary": 1,
    }
    if kind not in {"ordinary", "self"}:
        identity, paid = _schedule(
            java, game, "combat" if kind == "combat_only" else "worst", 0, 1
        )
        context["ids"].append(identity)
        context.update(setup_paid=paid, beneficiary=0)
    _next_turn(java, game)
    if kind == "combat_only" and actor.getControllingPlayer() is not None:
        raise ValueError("Normal Secret cost unexpectedly acquired turn control")
    _advance(java, game, "COMBAT_BEGIN")
    _restrictions(java, game, context, zones)
    game.getAction().checkStateEffects(True)
    phase = java.JClass("forge.game.phase.PhaseType").COMBAT_DECLARE_ATTACKERS
    game.getPhaseHandler().devModeSet(phase, actor, java.JBoolean(False))
    game.getPhaseHandler().setCombat(java.JClass("forge.game.combat.Combat")(actor))
    actor.getController().getAi().setUseSimulation(
        java.JClass("forge.ai.AIOption").USE_FULL_SIMULATION if mode == "full" else None
    )
    return game, context


def _restrictions(java, game, context, zones):
    """Establish fixed native control links and public attack restrictions."""
    kind = context["kind"]
    owner = _player(java, game, 0)
    actor = _player(java, game, 1)
    if kind == "chain":
        owner.setLife(40, None)
        beneficiary = _player(java, game, 2)
        beneficiary.setLife(2, None)
        owner.addController(game.getNextTimestamp(), beneficiary)
        context["beneficiary"] = 2
    if kind == "self":
        actor.addController(game.getNextTimestamp(), actor)
    for seat, name in PERMANENTS.get(kind, ()):
        card = _add(java, name, _player(java, game, seat), zones.Battlefield)
        context["ids"].append(int(card.getId()))


def _declaration(java, game):
    combat = game.getCombat()
    player_type = java.JClass("forge.game.player.Player")
    rows = []
    for attacker in combat.getAttackers():
        defender = combat.getDefenderByAttacker(attacker)
        rows.append(
            {
                "attacker": int(attacker.getId()),
                "defender_player": (
                    _seat(game, defender) if isinstance(defender, player_type) else None
                ),
                "defender_card": (
                    None if isinstance(defender, player_type) else int(defender.getId())
                ),
                "in_world": bool(
                    attacker.getGame() == game and defender.getGame() == game
                ),
            }
        )
    return sorted(rows, key=lambda row: row["attacker"])


def _choose(java, game, context):
    actor = _player(java, game, 1)
    combat = game.getCombat()
    agent = actor.getController()
    util = java.JClass("forge.game.combat.CombatUtil")
    randoms = java.JClass("forge.util.MyRandom")
    saved_provider = randoms.getRandom()
    fallback = None
    if context["kind"] == "timeout":
        game.AI_TIMEOUT = 0
        agent.declareAttackers(actor, combat)
        fallback = bool(util.validateAttackers(combat))
        combat.clearAttackers()
        game.AI_TIMEOUT = 60
    agent.declareAttackers(actor, combat)
    return {
        "selected": _declaration(java, game),
        "legal": bool(util.validateAttackers(combat)),
        "rng_restored": bool(randoms.getRandom() == saved_provider),
        "timeout_fallback_legal": fallback,
    }


def _case(java, case):
    game, context = _prepare(java, case)
    identities = context["ids"]
    before = _state(java, game, identities)
    choice = _choose(java, game, context)
    after_choice = _state(java, game, identities)
    selected = java.JClass("java.util.LinkedHashMap")(
        game.getCombat().getAttackersAndDefenders()
    )
    _player(java, game, 1).getController().getAi().setAttackOverride(selected)
    game.getPhaseHandler().devDeclareAttackers()
    _settle(game)
    declared = _declaration(java, game)
    after_declaration = _state(java, game, identities)
    _advance(java, game, "MAIN2")
    return {
        "case": case,
        **context,
        **choice,
        "before": before,
        "after_choice": after_choice,
        "declared": declared,
        "after_declaration": after_declaration,
        "after": _state(java, game, identities),
    }


def main() -> None:
    """Retain twenty-six choices, payments, identities and native combat outcomes."""
    java, engine = initialize_test_engine()
    run = Path(sys.argv[1]).resolve()
    parts = []
    for mode in ("heuristic", "full"):
        cases = [_case(java, case) for case in CASES if case.endswith(mode)]
        path = run / ("attack-" + mode + ".json")
        write_bounded_json(path, {"cases": cases})
        parts.append(
            {"name": path.name, "sha256": file_hash(path), "cases": len(cases)}
        )
    write_bounded_json(
        run / "attack.json",
        {
            "engine": engine,
            "default_profile_sha256": file_hash(
                read_settings().home / "res/ai/Default.ai"
            ),
            "parts": parts,
        },
    )


if __name__ == "__main__":
    main()
