"""Bounded native Battle combat and Siege-defeat checks using fixed reviewed sources."""

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Python -I excludes the checkout; add only this fixed trusted root before imports.
from commander.engine.forge_config import (  # pylint: disable=wrong-import-position
    file_hash,
    read_settings,
)
from commander.engine.interactions.suite import (  # pylint: disable=wrong-import-position
    read_bounded_json,
)
from tests.support.packaged_rules import (  # pylint: disable=wrong-import-position
    RuleFamily,
    run_rules,
)

TEST_CLASSES = ("forge.ai.CommanderBattleCombatTest",)
NATIVE_CLASSES = (
    "forge.ai.ComputerUtil",
    "forge.ai.AiController",
    "forge.ai.PlayerControllerAi",
    "forge.ai.simulation.GameCopier",
    "forge.game.Game",
    "forge.game.GameAction",
    "forge.game.card.Card",
    "forge.game.card.CardCopyService",
    "forge.game.card.CardFactoryUtil",
    "forge.game.card.CardProperty",
    "forge.game.combat.Combat",
    "forge.game.combat.CombatUtil",
    "forge.game.phase.PhaseHandler",
    "forge.game.trigger.TriggerHandler",
    "forge.game.spellability.SpellAbility",
    "forge.game.cost.CostPayment",
    "forge.game.ability.AbilityUtils",
    "forge.game.ability.effects.CountersRemoveEffect",
    "forge.game.ability.effects.FogEffect",
    "forge.game.ability.effects.PlayEffect",
    "forge.game.ability.effects.ChangeZoneEffect",
    "forge.game.ability.effects.CounterEffect",
    "forge.game.ability.effects.DestroyEffect",
    "forge.game.ability.effects.TokenEffect",
)
REVIEW_SOURCES = (
    PROJECT / "engine/sticker_recipe.json",
    PROJECT / "engine/battle_combat_tests.json",
)
FAMILY = RuleFamily(
    TEST_CLASSES,
    ("forge.ai.AITest",),
    NATIVE_CLASSES,
    "battle-combat.json",
    "battle-combat",
)


def reviewed_inputs() -> tuple[list[Path], Path]:
    """Allow only the fixed Java sources and the existing pinned TestNG dependency."""
    recipe = read_bounded_json(PROJECT / "engine/sticker_recipe.json", 65536)
    review = read_bounded_json(PROJECT / "engine/battle_combat_tests.json", 16384)
    sources = [
        PROJECT / "engine/tests/AITest.java",
        PROJECT / "engine/tests/CommanderBattleCombatTest.java",
    ]
    hashes = (recipe.get("packaged_ai_base_sha256"), review.get("fixture_sha256"))
    for source, expected_hash in zip(sources, hashes):
        if file_hash(source) != expected_hash:
            raise ValueError("Reviewed Battle combat source changed")
    expected_path = "data/engine-builds/m2/org/testng/testng/7.10.2/testng-7.10.2.jar"
    dependency = recipe.get("packaged_testng")
    jar = PROJECT / expected_path
    if (
        not isinstance(dependency, dict)
        or dependency.get("path") != expected_path
        or file_hash(jar) != dependency.get("sha256")
    ):
        raise ValueError("Pinned Battle combat TestNG dependency changed")
    return sources, jar


def main() -> None:
    """Run the fixed family in the shared bounded native-worker lifecycle."""
    _, dependency = reviewed_inputs()
    run_rules(
        read_settings(),
        Path(sys.argv[1]).resolve(strict=True),
        dependency,
        FAMILY,
    )


if __name__ == "__main__":
    main()
