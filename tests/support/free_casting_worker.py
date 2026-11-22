"""Fixed free-casting and cost-modifier regressions; each uses the normal worker budget."""

import json
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Python -I excludes the checkout; the bootstrap above adds only this fixed trusted root.
from commander.engine.forge_config import (  # pylint: disable=wrong-import-position
    file_hash,
    read_settings,
)
from tests.support.packaged_rules import (  # pylint: disable=wrong-import-position
    RuleFamily,
    run_rules,
)

SOURCES = {
    "AITest": "packaged_ai_base_sha256",
    "CommanderTraitDoctoringAiTest": "commander_trait_doctoring_ai_test_sha256",
    "CommanderCipherCopyAiTest": "commander_cipher_copy_ai_test_sha256",
    "CommanderFreeCastChoiceCopyTest": "commander_free_cast_choice_copy_test_sha256",
    "CommanderTextTargetIdentityTest": "commander_text_target_identity_test_sha256",
    "CommanderCipherFreeCastBoundaryTest": "commander_cipher_free_cast_boundary_test_sha256",
    "CommanderEffectCastPreviewTest": "commander_effect_cast_preview_test_sha256",
    "CommanderGrantedCostPreviewTest": "commander_granted_cost_preview_test_sha256",
    "CommanderGrantedCostBoundaryTest": "commander_granted_cost_boundary_test_sha256",
    "CommanderCostModifierPreviewTest": "commander_cost_modifier_preview_test_sha256",
    "CommanderKefnetCostTest": "commander_kefnet_cost_test_sha256",
    "CommanderCostModifierExpressionTest": "commander_cost_modifier_expression_test_sha256",
    "CommanderRaisedCastCostTest": "commander_raised_cast_cost_test_sha256",
    "CommanderHandCostModifierTest": "commander_hand_cost_modifier_test_sha256",
    "CommanderTransientCopySafetyTest": "commander_transient_copy_safety_test_sha256",
}
TEST_CLASSES = tuple("forge.ai." + name for name in SOURCES if name != "AITest")
NATIVE_CLASSES = (
    "forge.game.spellability.SpellAbility",
    "forge.ai.simulation.GameSimulator",
    "forge.ai.simulation.SpellAbilityChoiceCopier",
    "forge.game.cost.Cost",
    "forge.game.GameActionUtil",
    "forge.ai.SpellAbilityAi",
    "forge.ai.simulation.OnePlaySafetyChecker",
    "forge.game.cost.CostAdjustment",
    "forge.ai.ability.RevealAi",
    "forge.ai.simulation.PaidOutcomePlanner",
    "forge.ai.simulation.PossibleTargetSelector",
    "forge.ai.simulation.PossibleTargetSelector$SimilarTargetSkipper",
    "forge.ai.ability.PlayAi",
    "forge.ai.simulation.TextChangeChoices",
    "forge.ai.simulation.ModalTextChoices",
    "forge.game.Game",
    "forge.game.card.Card",
    "forge.game.card.CardCopyService",
    "forge.game.ability.AbilityUtils",
    "forge.game.ability.effects.PlayEffect",
    "forge.game.ability.effects.EncodeEffect",
    "forge.game.ability.effects.ChangeTextEffect",
    "forge.ai.ComputerUtil",
    "forge.ai.PlayerControllerAi",
    "forge.ai.AiController",
)


def reviewed_inputs() -> tuple[list[Path], Path]:
    """Select only the pinned sources and dependency; no uploaded definitions execute."""
    recipe = json.loads((PROJECT / "engine/sticker_recipe.json").read_bytes())
    sources = [PROJECT / "engine/tests" / (name + ".java") for name in SOURCES]
    for source, key in zip(sources, SOURCES.values()):
        if file_hash(source) != recipe[key]:
            raise ValueError("Reviewed free-casting source changed")
    expected = "data/engine-builds/m2/org/testng/testng/7.10.2/testng-7.10.2.jar"
    dependency = recipe["packaged_testng"]
    jar = PROJECT / expected
    if dependency["path"] != expected or file_hash(jar) != dependency["sha256"]:
        raise ValueError("Pinned free-casting TestNG dependency changed")
    return sources, jar


def main() -> None:
    """Run the fixed family and retain every hash-bound native method result."""
    settings = read_settings()
    run = Path(sys.argv[1]).resolve(strict=True)
    _, dependency = reviewed_inputs()
    family = RuleFamily(
        TEST_CLASSES,
        ("forge.ai.AITest",),
        NATIVE_CLASSES,
        "free-casting.json",
        "free-casting",
    )
    run_rules(settings, run, dependency, family)


if __name__ == "__main__":
    main()
