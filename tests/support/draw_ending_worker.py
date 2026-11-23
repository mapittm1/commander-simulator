"""Run fixed native rules tests against the verified packaged JAR, without Maven."""

import json
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted test sources and dependencies; uploads cannot select executable code.
# pylint: disable=wrong-import-position
from commander.engine.forge_config import file_hash, read_settings
from tests.support.packaged_rules import (
    RuleFamily,
    class_origin,
    compile_rules,
    run_rules,
    test_results,
)
from tools.build_sticker_engine import PRIORITY_PACKAGE, PRIORITY_TESTS

SOURCES = {
    "AITest": "packaged_ai_base_sha256",
    "InspectionTestSupport": "inspection_test_support_sha256",
    "InformationChoiceTestSupport": "information_choice_test_support_sha256",
    "CommanderInformationChoiceTest": "commander_information_choice_test_sha256",
    "CommanderInformationOrderingTest": "commander_information_ordering_test_sha256",
    "CommanderInformationValueTest": "commander_information_value_test_sha256",
    "CommanderInformationContextTest": "commander_information_context_test_sha256",
    "CommanderResolvingInformationCopyTest": "commander_resolving_information_copy_test_sha256",
    "LibraryChoiceTestSupport": "library_choice_test_support_sha256",
    "CommanderLibraryChoiceKnowledgeTest": "commander_library_choice_knowledge_test_sha256",
    "CommanderLibraryChoiceBoundaryTest": "commander_library_choice_boundary_test_sha256",
    "CommanderLibraryKnowledgeTransitionTest": "commander_library_knowledge_transition_test_sha256",
    "CommanderLibraryObservationBoundaryTest": "commander_library_observation_boundary_test_sha256",
    "CommanderInspectionAiTest": "commander_inspection_ai_test_sha256",
    "CommanderInspectionKnowledgeTest": "commander_inspection_knowledge_test_sha256",
    "DrawEndingCoverageTest": "draw_ending_test_sha256",
    "CostPreviewIsolationTest": "cost_preview_test_sha256",
    "ReflectedManaCoverageTest": "reflected_mana_test_sha256",
    "ManaTriggerFollowupTest": "mana_trigger_test_sha256",
    "MixedReflectedManaTest": "mixed_reflected_mana_test_sha256",
    "ManaAddedCoverageTest": "mana_added_test_sha256",
    "SnowManaProjectionTest": "snow_mana_projection_test_sha256",
    "ManaReplacementCoverageTest": "mana_replacement_test_sha256",
    "ChosenManaReplacementProbeTest": "chosen_mana_replacement_test_sha256",
    "UnpaidCastRecoveryProbeTest": "unpaid_cast_recovery_test_sha256",
    "OverlappingCastCostsProbeTest": "overlapping_cast_costs_test_sha256",
    "NonmanaPaymentRecoveryProbeTest": "nonmana_payment_recovery_test_sha256",
    "FailedActivationManaProbeTest": "failed_activation_mana_test_sha256",
    "FailedTapCostProbeTest": "failed_tap_cost_test_sha256",
    "FailedGroupTapCostProbeTest": "failed_group_tap_cost_test_sha256",
    "GroupTapSelectionProbeTest": "group_tap_selection_test_sha256",
    "TapManaOverlapProbeTest": "tap_mana_overlap_test_sha256",
    "NestedTapManaOverlapProbeTest": "nested_tap_mana_overlap_test_sha256",
    "FilterTapManaOverlapProbeTest": "filter_tap_mana_overlap_test_sha256",
    "NestedManaRollbackProbeTest": "nested_mana_rollback_test_sha256",
    "EnergyCostRecoveryProbeTest": "energy_cost_recovery_test_sha256",
    "DiscardCostRecoveryProbeTest": "discard_cost_recovery_test_sha256",
    "MixedDiscardRecoveryProbeTest": "mixed_discard_recovery_test_sha256",
    "CardCounterCostRecoveryProbeTest": "card_counter_recovery_test_sha256",
    "GroupedCounterCostProbeTest": "grouped_counter_cost_test_sha256",
    "SacrificeCostRecoveryProbeTest": "sacrifice_cost_recovery_test_sha256",
    "StarTrekSagaProbeTest": "star_trek_saga_test_sha256",
    "StarTrekDilemmaProbeTest": "star_trek_dilemma_test_sha256",
    "StarTrekBeamResolutionTest": "star_trek_beam_resolution_test_sha256",
    "StarTrekCombatEntryTest": "star_trek_combat_entry_test_sha256",
    "CardCharacteristicDiscrepancyTest": "card_characteristic_discrepancy_test_sha256",
    "StarTrekBattlefrontTest": "star_trek_battlefront_test_sha256",
    "StarTrekFederationTest": "star_trek_federation_test_sha256",
    "StarTrekRevealTest": "star_trek_reveal_test_sha256",
    "CommanderDiceReplacementTest": "commander_dice_replacement_test_sha256",
    "CommanderOptionalDiePaymentTest": "commander_optional_die_payment_test_sha256",
    "CommanderExploreTest": "commander_explore_test_sha256",
    "CommanderDiscoverTest": "commander_discover_test_sha256",
    "CommanderDiscoverLkiTest": "commander_discover_lki_test_sha256",
    "CommanderDiscoverScalarTest": "commander_discover_scalar_test_sha256",
    "CommanderDiscoverVehicleTest": "commander_discover_vehicle_test_sha256",
    "CommanderDiscoverCastFormsTest": "commander_discover_cast_forms_test_sha256",
    "CommanderDiscoverTaxTest": "commander_discover_tax_test_sha256",
    "CommanderDiscoverSacrificeTest": "commander_discover_sacrifice_test_sha256",
    "CommanderSwitchBlockAiTest": "commander_switch_block_ai_test_sha256",
    "CommanderEmpowerAiTest": "commander_empower_ai_test_sha256",
    "CommanderChangeTextAiTest": "commander_change_text_ai_test_sha256",
    "CommanderTextCompositionTest": "commander_text_composition_test_sha256",
    "CommanderPluralTextTest": "commander_plural_text_test_sha256",
    "CommanderLookAtCoverageTest": "commander_look_at_coverage_test_sha256",
    "CommanderSpyNetworkKnowledgeTest": "commander_spy_network_knowledge_test_sha256",
    "CommanderLibraryLookAtPositionTest": "commander_library_look_at_position_test_sha256",
    "CommanderCrystalSprayTest": "commander_crystal_spray_test_sha256",
    "CommanderCrystalSprayRulesTest": "commander_crystal_spray_rules_test_sha256",
    "CommanderCrystalSprayStackTest": "commander_crystal_spray_stack_test_sha256",
    "CommanderTraitDoctoringRulesTest": "commander_trait_doctoring_rules_test_sha256",
    "CommanderCipherCastingTest": "commander_cipher_casting_test_sha256",
    "CommanderBaubleCoverageTest": "commander_bauble_coverage_test_sha256",
    "CommanderVintageOpeningReplayTest": "commander_vintage_opening_test_sha256",
    "CommanderCompositeModeCoverageTest": "commander_composite_mode_test_sha256",
    "CommanderModalTextCoverageTest": "commander_modal_text_test_sha256",
    "CommanderModalBudgetTest": "commander_modal_budget_test_sha256",
    "CommanderVillainousChoiceTest": "commander_villainous_choice_test_sha256",
    "CommanderLearnTest": "commander_learn_test_sha256",
    "CommanderReverseTurnOrderTest": "commander_reverse_turn_order_test_sha256",
    "CommanderExtraTurnDirectionTest": "commander_extra_turn_direction_test_sha256",
    "CommanderEndTurnTest": "commander_end_turn_test_sha256",
    "CommanderIncubateTest": "commander_incubate_test_sha256",
    "CommanderTransformHistoryTest": "commander_transform_history_test_sha256",
    "CommanderSnapshotTransformStackTest": "commander_snapshot_stack_test_sha256",
    "CommanderOracleCorrectionsTest": "commander_oracle_corrections_test_sha256",
    "CommanderTemptCopyTest": "commander_tempt_copy_test_sha256",
    "CommanderConditionalManaTest": "commander_conditional_mana_test_sha256",
    "CommanderUlalekTest": "commander_ulalek_test_sha256",
    "CommanderUlalekAbilityOnlyTest": "commander_ulalek_ability_only_test_sha256",
    "CommanderReaperLimitTest": "commander_reaper_limit_test_sha256",
    "CommanderRinoaReturnTest": "commander_rinoa_return_test_sha256",
    "CommanderPreventedReturnTest": "commander_prevented_return_test_sha256",
    "CommanderTargetedReturnTest": "commander_targeted_return_test_sha256",
    "CommanderOptionalLifeGainTest": "commander_optional_life_gain_test_sha256",
    "CommanderOptionalDrawTest": "commander_optional_draw_test_sha256",
    "CommanderOptionalCopyTest": "commander_optional_copy_test_sha256",
    "CommanderCarpetManaTest": "commander_carpet_mana_test_sha256",
    "CommanderOptionalCountersTest": "commander_optional_counters_test_sha256",
    "CommanderOptionalXTest": "commander_optional_x_test_sha256",
    "CommanderPriorityBoardingTest": "commander_priority_boarding_test_sha256",
    "CommanderOptionalPlayTest": "commander_optional_play_test_sha256",
    "CommanderSecretVoteTest": "commander_secret_vote_test_sha256",
    "CommanderEmptyVoteTest": "commander_empty_vote_test_sha256",
    "CommanderPublicVoteTest": "commander_public_vote_test_sha256",
    "CommanderCirdanTest": "commander_cirdan_test_sha256",
    "CommanderTimeTravelTest": "commander_time_travel_test_sha256",
    "ProliferateFightAndInvestigateTest": "proliferate_fight_investigate_test_sha256",
    "StarTrekHoloEngineerTest": "star_trek_holo_engineer_test_sha256",
    "StarTrekNamedAbilityTest": "star_trek_named_ability_test_sha256",
    "StarTrekStationTest": "star_trek_station_test_sha256",
    "StarTrekKobayashiTest": "star_trek_kobayashi_test_sha256",
    "StarTrekAssimilateProbeTest": "star_trek_assimilate_test_sha256",
}
TEST_CLASS_PACKAGES = {"CommanderModalBudgetTest": "forge.ai.simulation"}
TEST_CLASSES = tuple(
    f"{TEST_CLASS_PACKAGES.get(name, 'forge.ai')}.{name}"
    for name in SOURCES
    if name
    not in {
        "AITest",
        "InspectionTestSupport",
        "InformationChoiceTestSupport",
        "LibraryChoiceTestSupport",
    }
) + tuple(PRIORITY_PACKAGE + name for name in PRIORITY_TESTS)
NATIVE_CLASSES = (
    "forge.game.ability.effects.LookAtEffect",
    "forge.ai.InspectionKnowledge",
    "forge.ai.InformationEffects",
    "forge.ai.ability.PumpAi",
    "forge.ai.ability.PermanentNoncreatureAi",
    "forge.game.ability.effects.RearrangeTopOfLibraryEffect",
    "forge.ai.ability.LookAtAi",
    "forge.ai.simulation.PossibleTargetSelector",
    "forge.game.ability.effects.ChangeTextEffect",
    "forge.game.ability.effects.EncodeEffect",
    "forge.ai.AiController",
    "forge.ai.ability.ChooseGenericAi",
    "forge.ai.ability.CharmAi",
    "forge.ai.simulation.ModalTextChoices",
    "forge.ai.AiCardMemory",
    "forge.game.player.PlayerController",
    "forge.ai.PlayerControllerAi",
    "forge.ai.simulation.PaidOutcomePlanner",
    "forge.ai.simulation.Plan",
    "forge.ai.simulation.SimulationController",
    "forge.ai.simulation.GameStateEvaluator",
    "forge.ai.simulation.SpellAbilityChoiceCopier",
    "forge.ai.simulation.SpellAbilityChoicesIterator",
    "forge.ai.simulation.SpellAbilityPicker",
    "forge.ai.ability.ChangeTextAi",
    "forge.ai.simulation.TextChangeChoices",
    "forge.ai.ComputerUtilMana",
    "forge.ai.SpellApiToAi",
    "forge.ai.ability.SwitchBlockAi",
    "forge.ai.ability.EmpowerAi",
    "forge.game.ability.effects.EmpowerEffect",
    "forge.ai.simulation.OnePlaySafetyChecker",
    "forge.game.Game",
    "forge.game.card.Card",
    "forge.game.trigger.TriggerCounterRemoved",
    "forge.game.ability.effects.GameDrawEffect",
    "forge.game.ability.effects.DrawEffect",
    "forge.game.ability.effects.CopyPermanentEffect",
    "forge.game.ability.effects.CountersPutAllEffect",
    "forge.game.ability.effects.ChangeXEffect",
    "forge.game.ability.effects.PeekAndRevealEffect",
    "forge.game.ability.effects.ImmediateTriggerEffect",
    "forge.game.ability.effects.PlayEffect",
    "forge.game.ability.effects.ManaReflectedEffect",
    "forge.game.spellability.AbilityManaPart",
    "forge.game.trigger.TriggerTapsForMana",
    "forge.game.trigger.TriggerManaAdded",
    "forge.game.mana.ManaCostBeingPaid",
    "forge.ai.ComputerUtil",
    "forge.game.GameActionUtil",
    "forge.game.ability.effects.ReplaceManaEffect",
    "forge.game.trigger.TriggerHandler",
    "forge.ai.AiCostDecision",
    "forge.game.cost.Cost",
    "forge.game.cost.CostPayment",
    "forge.game.cost.CostRemoveCounter",
    "forge.game.cost.CostRemoveAnyCounter",
    "forge.game.GameAction",
    "forge.game.GameEntity",
    "forge.game.ability.AbilityUtils",
    "forge.game.card.CardZoneTable",
    "forge.game.cost.CostDiscard",
    "forge.game.cost.CostPartWithList",
    "forge.game.cost.CostPayEnergy",
    "forge.game.zone.Zone",
    "forge.game.zone.Zone$LibraryObservation",
    "forge.game.player.PlayerController$LibraryOrderObservation",
    "forge.game.cost.CostDecisionMakerBase",
    "forge.ai.ComputerUtilCost",
    "forge.game.cost.CostTap",
    "forge.game.cost.CostTapType",
    "forge.game.cost.CostPayLife",
    "forge.game.player.Player",
    "forge.game.spellability.SpellAbility",
    "forge.game.cost.CostSacrifice",
    "forge.ai.simulation.GameCopier",
    "forge.game.ability.effects.AssimilateEffect",
    "forge.game.ability.effects.CharmEffect",
    "forge.game.player.PlaySpellAbility",
    "forge.game.trigger.TriggerFacesDilemma",
    "forge.game.zone.MagicStack",
    "forge.game.ability.SpellAbilityEffect",
    "forge.game.ability.effects.ChangeZoneEffect",
    "forge.game.ability.effects.LifeGainEffect",
    "forge.game.combat.Combat",
    "forge.game.replacement.ReplacementHandler",
    "forge.game.ability.effects.DigEffect",
    "forge.game.ability.effects.CountersPutEffect",
    "forge.game.card.CardFactoryUtil",
    "forge.game.GameSnapshot",
    "forge.game.ability.effects.RollDiceEffect",
    "forge.game.ability.effects.ExploreEffect",
    "forge.game.ability.effects.DiscoverEffect",
    "forge.game.ability.effects.VillainousChoiceEffect",
    "forge.game.ability.effects.LearnEffect",
    "forge.game.ability.effects.ReverseTurnOrderEffect",
    "forge.game.ability.effects.EndTurnEffect",
    "forge.game.ability.effects.SetStateEffect",
    "forge.game.card.CardCopyService",
    "forge.game.spellability.SpellAbilityStackInstance",
    "forge.game.spellability.SpellAbilitySnapshot",
    "forge.game.ability.effects.CounterEffect",
    "forge.game.ability.effects.CopySpellAbilityEffect",
    "forge.ai.ability.CopySpellAbilityAi",
    "forge.game.spellability.SpellAbilityProperty",
    "forge.game.ability.effects.RepeatEachEffect",
    "forge.game.phase.PhaseHandler",
    "forge.game.ability.effects.VoteEffect",
    "forge.game.ability.effects.TimeTravelEffect",
    "forge.game.trigger.TriggerRolledDie",
    "forge.game.trigger.WrappedAbility",
    "forge.game.trigger.TriggerChangesZoneAll",
    "forge.game.ability.AbilityKey",
)


def _inputs():
    recipe = json.loads(
        (PROJECT / "engine/sticker_recipe.json").read_text(encoding="utf-8")
    )
    sources = [PROJECT / "engine/tests" / f"{name}.java" for name in SOURCES]
    for source, key in zip(sources, SOURCES.values()):
        if file_hash(source) != recipe[key]:
            raise ValueError("Reviewed packaged rules source changed")
    for name in ("PriorityTestBase", *PRIORITY_TESTS):
        source = PROJECT / "engine/tests" / f"{name}.java"
        if file_hash(source) != recipe["upstream_priority_tests"][name]:
            raise ValueError("Reviewed packaged priority source changed")
        sources.append(source)
    dependency = recipe["packaged_testng"]
    expected = "data/engine-builds/m2/org/testng/testng/7.10.2/testng-7.10.2.jar"
    if dependency["path"] != expected:
        raise ValueError("Unexpected fixed TestNG dependency path")
    jar = PROJECT / expected
    if file_hash(jar) != dependency["sha256"]:
        raise ValueError("Pinned local TestNG dependency changed")
    return sources, jar


def _compile(settings, run, sources, dependency):
    return compile_rules(settings, run, sources, dependency)


def _origin(java, name):
    return class_origin(java, name)


def _results(listener):
    return test_results(listener)


def main() -> None:
    """Execute the existing fixed 933-case packaged family."""
    settings = read_settings()
    run = Path(sys.argv[1]).resolve(strict=True)
    _, dependency = _inputs()
    family = RuleFamily(
        TEST_CLASSES,
        (
            "forge.ai.AITest",
            "forge.ai.InspectionTestSupport",
            "forge.ai.InformationChoiceTestSupport",
            "forge.ai.LibraryChoiceTestSupport",
            PRIORITY_PACKAGE + "PriorityTestBase",
        ),
        NATIVE_CLASSES,
        "draw-endings.json",
        "draw-ending",
    )
    run_rules(settings, run, dependency, family)


if __name__ == "__main__":
    main()
