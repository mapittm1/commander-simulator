"""Fixed untap, discard-payment and preview-cache checks within the normal worker budget."""

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
    "CommanderUntapResolutionTest": "commander_untap_resolution_test_sha256",
    "CommanderUntapFollowupTest": "commander_untap_followup_test_sha256",
    "CommanderPreviewCacheTest": "commander_preview_cache_test_sha256",
    "CommanderActivatedUntapTest": "commander_activated_untap_test_sha256",
    "CommanderChainedUntapTest": "commander_chained_untap_test_sha256",
    "CommanderMassTapTest": "commander_mass_tap_test_sha256",
    "CommanderMassTapChosenTypeTest": "commander_mass_tap_chosen_type_test_sha256",
    "CommanderChoiceContextTest": "commander_choice_context_test_sha256",
    "CommanderRepeatedDiscardEntrypointTest": "commander_repeated_discard_entrypoint_test_sha256",
}
TEST_CLASSES = tuple("forge.ai." + name for name in SOURCES if name != "AITest")
NATIVE_CLASSES = (
    "forge.ai.AiCache",
    "forge.ai.PlayerControllerAi",
    "forge.ai.ability.TapOrUntapAi",
    "forge.ai.simulation.PaidOutcomePlanner",
    "forge.ai.simulation.SpellAbilityPicker",
    "forge.ai.simulation.SimulationController",
    "forge.ai.simulation.GameSimulator",
    "forge.ai.simulation.GameCopier",
    "forge.ai.simulation.SpellAbilityChoiceCopier",
    "forge.game.ability.effects.TapOrUntapEffect",
    "forge.game.spellability.SpellAbility",
    "forge.game.cost.Cost",
    "forge.game.card.Card",
    "forge.game.Game",
    "forge.ai.AiController",
    "forge.ai.ComputerUtil",
    "forge.ai.ComputerUtilCombat",
    "forge.ai.AiCostDecision",
    "forge.ai.ComputerUtilCost",
    "forge.ai.simulation.DiscardPaymentChoices",
    "forge.ai.simulation.Plan",
    "forge.ai.simulation.Plan$Decision",
    "forge.ai.simulation.SpellAbilityChoicesIterator",
    "forge.ai.simulation.SpellAbilityChoicesIterator$ChoicePoint",
    "forge.ai.simulation.MassTapChoices",
    "forge.ai.simulation.MassTapChoices$Branch",
    "forge.ai.simulation.MassTapChoices$Outcome",
    "forge.ai.ability.ChooseTypeAi",
    "forge.ai.ability.TapOrUntapAllAi",
    "forge.ai.simulation.TextChangeChoices",
    "forge.game.ability.effects.TapOrUntapAllEffect",
    "forge.game.ability.effects.ChooseTypeEffect",
    "forge.game.card.CardCopyService",
    "forge.game.card.CardView",
    "forge.game.spellability.SpellAbilitySnapshot",
)
FAMILY = RuleFamily(
    TEST_CLASSES, ("forge.ai.AITest",), NATIVE_CLASSES, "untap.json", "untap"
)


def reviewed_inputs() -> tuple[list[Path], Path]:
    """Select only the pinned sources and dependency; no uploaded definitions execute."""
    recipe = json.loads((PROJECT / "engine/sticker_recipe.json").read_bytes())
    sources = [PROJECT / "engine/tests" / (name + ".java") for name in SOURCES]
    for source, key in zip(sources, SOURCES.values()):
        if file_hash(source) != recipe[key]:
            raise ValueError("Reviewed untap source changed")
    expected = "data/engine-builds/m2/org/testng/testng/7.10.2/testng-7.10.2.jar"
    dependency = recipe["packaged_testng"]
    jar = PROJECT / expected
    if dependency["path"] != expected or file_hash(jar) != dependency["sha256"]:
        raise ValueError("Pinned untap TestNG dependency changed")
    return sources, jar


def main() -> None:
    """Run the fixed family and retain every hash-bound native method result."""
    settings = read_settings()
    run = Path(sys.argv[1]).resolve(strict=True)
    _, dependency = reviewed_inputs()
    run_rules(settings, run, dependency, FAMILY)


if __name__ == "__main__":
    main()
