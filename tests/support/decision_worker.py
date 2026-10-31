"""Fixed paid decision-beneficiary and response-planning witnesses."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted programs; uploaded decks never select executable scenarios.
# pylint: disable=wrong-import-position
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.interactions.suite import write_bounded_json
from tests.support.animation_worker import _postcombat
from tests.support.control_turn_worker import _player
from tests.support.control_turn_worker import _state as _control_state
from tests.support.keyword_expiration_worker import _resolve
from tests.support.native_worker import initialize_test_engine
from tests.support.stack_worker import _pay
from tests.support.turn_schedule_worker import _advance, _next_turn, _seat
from tests.support.usage_worker import _add, _settle, _setup

CASES = (
    "ordinary_heuristic",
    "ordinary_full",
    "controlled_heuristic",
    "controlled_full",
    "chain_heuristic",
    "chain_full",
    "self_heuristic",
    "self_full",
    "response_ordinary_heuristic",
    "response_ordinary_full",
    "response_controlled_heuristic",
    "response_controlled_full",
    "uncounterable_heuristic",
    "uncounterable_full",
    "ability_heuristic",
    "ability_full",
)


def _ability(game, action):
    return {
        "name": str(action.getHostCard().getName()),
        "host_id": int(action.getHostCard().getId()),
        "api": str(action.getApi()),
        "is_spell": bool(action.isSpell()),
        "actor": _seat(game, action.getActivatingPlayer()),
        "players": [
            _seat(game, player) for player in action.getTargets().getTargetPlayers()
        ],
        "spells": [
            int(spell.getHostCard().getId())
            for spell in action.getTargets().getTargetSpells()
        ],
        "in_world": bool(
            action.getHostCard().getGame() == game
            and action.getActivatingPlayer().getGame() == game
            and all(
                player.getGame() == game
                for player in action.getTargets().getTargetPlayers()
            )
            and all(
                spell.getHostCard().getGame() == game
                for spell in action.getTargets().getTargetSpells()
            )
        ),
    }


def _state(java, game, identities):
    state = _control_state(game, identities)
    zones = java.JClass("forge.game.zone.ZoneType")
    for row, player in zip(state["players"], game.getRegisteredPlayers()):
        row.update(
            life=int(player.getLife()),
            decision_profile=str(
                player.getController().getLobbyPlayer().getAiProfile()
            ),
            tapped_lands=sum(
                bool(card.isTapped())
                for card in player.getCardsIn(zones.Battlefield)
                if card.getType().isLand()
            ),
            hand=sorted(str(card.getName()) for card in player.getCardsIn(zones.Hand)),
        )
    state["stack_cards"] = len(game.getCardsIn(zones.Stack))
    state["stack"] = [
        _ability(game, entry.getSpellAbility()) for entry in game.getStack()
    ]
    return state


def _prepare(java, case):
    game, owner, zones = _setup(java, ())
    for seat, player in enumerate(game.getRegisteredPlayers()):
        # Replace the shared helper's seed library before establishing this fixed board.
        player.getZone(zones.Library).setCards(
            java.JClass("forge.game.card.CardCollection")()
        )
        for _ in range(10):
            _add(java, "Swamp", player, zones.Battlefield)
            _add(java, "Mountain", player, zones.Battlefield)
            _add(java, "Shock" if seat == 1 else "Forest", player, zones.Library)
        for _ in range(4):
            _add(java, "Island", player, zones.Battlefield)
    _postcombat(java, game, owner)
    owner.setLife(2, None)
    actor = _player(java, game, 1)
    context = {"ids": [], "setup_paid": [], "threat_paid": 0, "beneficiary": 1}
    if case.startswith(
        ("controlled", "chain", "response_controlled", "uncounterable", "ability")
    ):
        card = _add(java, "Worst Fears", owner, zones.Hand)
        context["ids"].append(int(card.getId()))
        context["setup_paid"].append(
            _resolve(java, game, card.getFirstSpellAbility(), owner, actor)
        )
        context["beneficiary"] = 0
    _next_turn(java, game)
    _advance(java, game, "MAIN2")
    if (
        len(actor.getCardsIn(zones.Hand)) != 1
        or str(actor.getCardsIn(zones.Hand).get(java.JInt(0)).getName()) != "Shock"
    ):
        raise ValueError("Fixed policy fixture did not draw its single Shock")
    context["ids"].append(int(actor.getCardsIn(zones.Hand).get(java.JInt(0)).getId()))
    if case.startswith("chain"):
        owner.setLife(40, None)
        _player(java, game, 2).setLife(2, None)
        owner.addController(game.getNextTimestamp(), _player(java, game, 2))
        context["beneficiary"] = 2
    if case.startswith("self"):
        actor.addController(game.getNextTimestamp(), actor)
    if case.startswith("response_ordinary"):
        owner.setLife(40, None)
        actor.setLife(2, None)
    if case.startswith(("response", "uncounterable", "ability")):
        _pending(java, game, case, context, zones)
    return game, context


def _pending(java, game, case, context, zones):
    """Pay for the fixed threat and leave its native entry unresolved."""
    enemy = _player(java, game, 2)
    actor = _player(java, game, 1)
    if case.startswith("ability"):
        for _ in range(7):
            _add(java, "Forest", enemy, zones.Graveyard)
        card = _add(java, "Barbarian Ring", enemy, zones.Battlefield)
        action = next(
            ability
            for ability in card.getSpellAbilities()
            if ability.isActivatedAbility() and str(ability.getApi()) == "DealDamage"
        )
    else:
        card = _add(
            java,
            "Volcanic Fallout" if case.startswith("uncounterable") else "Shock",
            enemy,
            zones.Hand,
        )
        action = card.getFirstSpellAbility()
    context["threat_id"] = int(card.getId())
    context["ids"].append(int(card.getId()))
    action.setActivatingPlayer(enemy)
    if not case.startswith("uncounterable"):
        target = _player(java, game, context["beneficiary"])
        if not action.canTarget(target):
            raise ValueError("Fixed pending threat has an illegal target")
        action.getTargets().add(target)
    _pay(java, game, action, enemy)
    context["threat_paid"] = len(game.getStack().peekAbility().getPayingMana())
    context["threat_sources"] = [
        int(mana.getSourceCard().getId())
        for mana in game.getStack().peekAbility().getPayingMana()
    ]
    card = _add(
        java,
        "Stifle" if case.startswith("ability") else "Counterspell",
        actor,
        zones.Hand,
    )
    context["ids"].append(int(card.getId()))


def _configure(java, game, case):
    actor = _player(java, game, 1)
    if case == "response_ordinary_heuristic":
        java.JClass("forge.ai.AiProfileUtil").loadAllProfiles(
            java.JClass("forge.localinstance.properties.ForgeConstants").AI_PROFILE_DIR
        )
        actor.getOriginalLobbyPlayer().setAiProfile("Cautious")
        properties = java.JClass("forge.ai.AiProps")
        profiles = java.JClass("forge.ai.AiProfileUtil")
        if profiles.getIntProperty(
            actor, properties.MIN_SPELL_CMC_TO_COUNTER
        ) != 2 or not profiles.getBoolProperty(
            actor, properties.ALWAYS_COUNTER_DAMAGE_SPELLS
        ):
            raise ValueError(
                "Shipped Cautious response preference does not match the fixed witness"
            )
    mode = (
        java.JClass("forge.ai.AIOption").USE_FULL_SIMULATION
        if case.endswith("full")
        else None
    )
    actor.getController().getAi().setUseSimulation(mode)


def _play(java, game, action):
    actor = _player(java, game, 1)
    if action.getActivatingPlayer() != actor or not action.checkRestrictions(actor):
        raise ValueError("Fixed policy witness selected an invalid acting participant")
    if not java.JClass("forge.ai.ComputerUtilCost").canPayCost(action, actor, False):
        raise ValueError("Fixed policy witness selected an unaffordable action")
    _pay(java, game, action, actor)
    stacked = game.getStack().peekAbility()
    result = {
        "paid": len(stacked.getPayingMana()),
        "paid_actor": _seat(game, stacked.getActivatingPlayer()),
        "mana_owners": [
            _seat(game, mana.getSourceCard().getController())
            for mana in stacked.getPayingMana()
        ],
    }
    game.getStack().resolveStack()
    _settle(game)
    return result


def _case(java, case):
    game, context = _prepare(java, case)
    _configure(java, game, case)
    observations = {"before": _state(java, game, context["ids"])}
    choices = _player(java, game, 1).getController().chooseSpellAbilityToPlay()
    if choices is not None and len(choices) > 1:
        raise ValueError("Fixed policy witness unexpectedly selected multiple actions")
    action = (
        choices.get(java.JInt(0))
        if choices is not None and not choices.isEmpty()
        else None
    )
    observations["after_choice"] = _state(java, game, context["ids"])
    selected = None if action is None else _ability(game, action)
    payment = {"paid": 0, "paid_actor": None, "mana_owners": []}
    if action is not None:
        payment = _play(java, game, action)
    else:
        _settle(game)
    observations["after"] = _state(java, game, context["ids"])
    return {"case": case, **context, **observations, "selected": selected, **payment}


def main() -> None:
    """Retain sixteen actual AI choices and their native paid outcomes."""
    java, engine = initialize_test_engine()
    home = read_settings().home
    profiles = {
        name: file_hash(home / "res/ai" / (name + ".ai"))
        for name in ("Default", "Cautious")
    }
    write_bounded_json(
        Path(sys.argv[1]).resolve() / "decision.json",
        {
            "engine": engine,
            "cautious_profile_sha256": profiles["Cautious"],
            "profile_sha256": profiles,
            "cases": [_case(java, case) for case in CASES],
        },
    )


if __name__ == "__main__":
    main()
