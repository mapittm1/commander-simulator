"""Build a separately pinned Forge Commander runtime from verified local archives.

This administrator tool executes trusted upstream build plugins. It is never a
deck-upload or web-job entry point. It does not replace the active engine config.
"""

import argparse
import hashlib
import json
import re
import shutil
import sys
import uuid
import zipfile
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT))

# This fixed checkout bootstrap is required when invoked from tools/.
# pylint: disable=wrong-import-position
from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import file_hash, jvm_library
from commander.engine.forge_engine import _environment
from commander.engine.interactions.suite import read_hashed_json, write_bounded_json
from commander.engine.printing_resources import edition_fingerprint

PRIORITY_TESTS = (
    "CancelledCastDuringResolutionTest",
    "CastDuringResolutionPriorityTest",
    "CopyOrderOnStackTest",
    "EndTurnCleanupPriorityTest",
    "ManaAbilityCastPriorityTest",
    "StateBasedActionCastPriorityTest",
    "TurnBasedActionCastPriorityTest",
    "UntapStepCastTest",
)
PRIORITY_PACKAGE = "forge.gamesimulationtests.priority."


def main():
    """Create a fresh build directory and preserve all compiler/test evidence."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-archive", type=Path, required=True)
    parser.add_argument("--maven-archive", type=Path, required=True)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--runtime", type=Path, required=True)
    args = parser.parse_args()
    recipe, recipe_hash = read_hashed_json(PROJECT / "engine/sticker_recipe.json")
    _verify_inputs(args, recipe)
    build_root = PROJECT / "data/engine-builds"
    build_root.mkdir(parents=True, exist_ok=True)
    # Generated uniqueness; source, build results, and runtime are never overwritten.
    run = build_root / uuid.uuid4().hex
    run.mkdir()
    shutil.copyfile(PROJECT / "engine/sticker_recipe.json", run / "build-recipe.json")
    if file_hash(run / "build-recipe.json") != recipe_hash:
        raise ValueError("Engine build recipe changed before snapshot")
    source, maven = run / "source", run / "maven"
    _extract(args.source_archive, source, "forge-" + recipe["forge_commit"])
    _extract(args.maven_archive, maven, "apache-maven-" + recipe["maven_version"])
    _patch_source(source, run, recipe)
    _compile(args, source, maven, run)
    tests = _test_evidence(source, recipe["native_test_count"])
    _verify_unchanged_inputs(args, recipe, recipe_hash)
    runtime = run / "runtime"
    _package(args.source_archive, source, runtime, recipe)
    _verify_unchanged_inputs(args, recipe, recipe_hash)
    evidence = {"native_tests": tests, "recipe_sha256": recipe_hash}
    lock = _lock(runtime, run, recipe, evidence, args)
    write_bounded_json(run / "engine-lock.json", lock, indent=2)
    write_bounded_json(
        run / "engine-config.json",
        {
            "home": "runtime",
            "java": str(args.runtime.resolve()),
            "lock": "engine-lock.json",
        },
        indent=2,
    )
    print(
        json.dumps(
            {
                "status": "built_and_native_regressions_passed",
                "directory": str(run),
                "config": str(run / "engine-config.json"),
                "tests": tests,
            },
            indent=2,
        )
    )


def _verify_priority_fixtures(priority_tests):
    if set(priority_tests) != {"PriorityTestBase", *PRIORITY_TESTS}:
        raise ValueError("Unexpected upstream priority regression registry")
    for name, digest in priority_tests.items():
        if file_hash(PROJECT / "engine/tests" / f"{name}.java") != digest:
            raise ValueError(f"Native priority fixture {name} changed")


def _verify_inputs(args, recipe):
    if file_hash(args.source_archive) != recipe["source_sha256"]:
        raise ValueError("Forge source archive differs from the reviewed snapshot")
    digest = hashlib.sha512()
    with args.maven_archive.open("rb") as stream:
        while raw := stream.read(1024 * 1024):
            digest.update(raw)
    if digest.hexdigest() != recipe["maven_sha512"]:
        raise ValueError("Maven archive differs from Apache's published checksum")
    for entry in recipe["patches"]:
        if (
            entry["path"]
            not in {
                "engine/patches/wicker-picker-upstream-470d983.patch",
                "engine/patches/wicker-picker-local-regressions.patch",
                "engine/patches/commander-color-choice.patch",
                "engine/patches/companion-pregame-types.patch",
                "engine/patches/supplementary-ai-zones.patch",
                "engine/patches/sticker-snapshot-state.patch",
                "engine/patches/historical-lki-state.patch",
                "engine/patches/damage-history-state.patch",
                "engine/patches/participant-snapshot-state.patch",
                "engine/patches/starting-life-snapshot.patch",
                "engine/patches/paid-stack-state.patch",
                "engine/patches/cast-form-snapshot.patch",
                "engine/patches/usage-snapshot-state.patch",
                "engine/patches/scheduled-effect-snapshots.patch",
                "engine/patches/delayed-trigger-snapshots.patch",
                "engine/patches/turn-schedule-snapshots.patch",
                "engine/patches/conditional-cleanup-snapshots.patch",
                "engine/patches/animation-snapshot-state.patch",
                "engine/patches/copy-frame-expiration.patch",
                "engine/patches/protection-expiration.patch",
                "engine/patches/keyword-expiration.patch",
                "engine/patches/blocking-prevention.patch",
                "engine/patches/ring-text.patch",
                "engine/patches/attachment-mana.patch",
                "engine/patches/host-return.patch",
                "engine/patches/effect-cleanup.patch",
                "engine/patches/detain-control-age.patch",
                "engine/patches/player-control.patch",
                "engine/patches/decision-perspective.patch",
                "engine/patches/controlled-attack.patch",
                "engine/patches/primary-control-planning.patch",
                "engine/patches/spell-control-retargeting.patch",
                "engine/patches/composed-spell-control.patch",
                "engine/patches/mass-counter-removal.patch",
                "engine/patches/exchange-pile-planning.patch",
                "engine/patches/divine-intervention-trigger.patch",
                "engine/patches/expired-mana-reservations.patch",
                "engine/patches/overabundance-mana-order.patch",
                "engine/patches/reflected-mana-payment.patch",
                "engine/patches/mana-trigger-events.patch",
                "engine/patches/typed-mana-projection.patch",
                "engine/patches/mana-type-replacement-preview.patch",
                "engine/patches/chosen-mana-replacement.patch",
                "engine/patches/unpaid-cast-recovery.patch",
                "engine/patches/combined-sacrifice-allocation.patch",
                "engine/patches/failed-activation-payment-recovery.patch",
                "engine/patches/tap-cost-selection-recovery.patch",
                "engine/patches/combined-mana-tap-reservations.patch",
                "engine/patches/energy-discard-payment-recovery.patch",
                "engine/patches/card-counter-payment-recovery.patch",
                "engine/patches/sacrifice-payment-recovery.patch",
                "engine/patches/star-trek-reviewed-engine-overlay.patch",
                "engine/patches/star-trek-saga-common-card-type.patch",
                "engine/patches/star-trek-dilemma-cast.patch",
                "engine/patches/star-trek-combat-entry.patch",
                "engine/patches/star-trek-hit-it-reveal.patch",
                "engine/patches/commander-dice-history.patch",
                "engine/patches/commander-optional-dice-payment.patch",
                "engine/patches/commander-explore-order.patch",
                "engine/patches/commander-discover.patch",
                "engine/patches/commander-discover-scalar.patch",
                "engine/patches/commander-switch-block-ai.patch",
                "engine/patches/commander-empower-ai.patch",
                "engine/patches/commander-text-choices.patch",
                "engine/patches/commander-private-knowledge.patch",
                "engine/patches/commander-modal-text.patch",
                "engine/patches/commander-look-at-positions.patch",
                "engine/patches/commander-parent-text.patch",
                "engine/patches/commander-villainous-choice.patch",
                "engine/patches/commander-learn.patch",
                "engine/patches/commander-reverse-turn-order.patch",
                "engine/patches/commander-secret-vote.patch",
                "engine/patches/commander-empty-vote.patch",
                "engine/patches/commander-cirdan-optional.patch",
                "engine/patches/commander-restore-turn-order.patch",
                "engine/patches/commander-extra-turn-direction.patch",
                "engine/patches/commander-transform-copy.patch",
                "engine/patches/commander-snapshot-stack.patch",
                "engine/patches/commander-oracle-corrections.patch",
                "engine/patches/commander-priority.patch",
                "engine/patches/commander-tempt-copy.patch",
                "engine/patches/commander-conditional-mana.patch",
                "engine/patches/commander-ulalek.patch",
                "engine/patches/commander-ulalek-ability-only.patch",
                "engine/patches/commander-reaper-limit.patch",
                "engine/patches/commander-rinoa-return.patch",
                "engine/patches/commander-prevented-return.patch",
                "engine/patches/commander-targeted-return.patch",
                "engine/patches/commander-optional-life.patch",
                "engine/patches/commander-optional-draw.patch",
                "engine/patches/commander-optional-copy.patch",
                "engine/patches/commander-carpet-mana.patch",
                "engine/patches/commander-optional-counters.patch",
                "engine/patches/commander-optional-x.patch",
                "engine/patches/commander-priority-boarding.patch",
                "engine/patches/commander-optional-play.patch",
            }
            or file_hash(PROJECT / entry["path"]) != entry["sha256"]
        ):
            raise ValueError("Engine patch registry or content changed")
    regression_sources = {
        "StickerKickerRegressionTest.java": "test_sha256",
        "CommanderColorChoiceTest.java": "commander_color_test_sha256",
        "CompanionPregameTest.java": "companion_pregame_test_sha256",
        "SupplementaryGameCopyTest.java": "supplementary_copy_test_sha256",
        "StickerGameCopyTest.java": "sticker_copy_test_sha256",
        "HistoricalGameCopyTest.java": "historical_lki_test_sha256",
        "DamageGameCopyTest.java": "damage_history_test_sha256",
        "ParticipantGameCopyTest.java": "participant_copy_test_sha256",
        "StartingLifeGameCopyTest.java": "starting_life_test_sha256",
        "StackGameCopyTest.java": "stack_snapshot_test_sha256",
        "FaceGameCopyTest.java": "cast_form_test_sha256",
        "UsageGameCopyTest.java": "usage_snapshot_test_sha256",
        "TurnUsageGameCopyTest.java": "turn_usage_test_sha256",
        "DelayedGameCopyTest.java": "delayed_trigger_test_sha256",
        "TurnScheduleGameCopyTest.java": "turn_schedule_test_sha256",
        "ConditionalGameCopyTest.java": "conditional_cleanup_test_sha256",
        "AnimationGameCopyTest.java": "animation_snapshot_test_sha256",
        "CloneDurationGameCopyTest.java": "clone_duration_test_sha256",
        "ProtectionGameCopyTest.java": "protection_expiration_test_sha256",
        "KeywordExpirationGameCopyTest.java": "keyword_expiration_test_sha256",
        "BlockingPreventionGameCopyTest.java": "blocking_prevention_test_sha256",
        "RingTextGameCopyTest.java": "ring_text_test_sha256",
        "AttachmentManaGameCopyTest.java": "attachment_mana_test_sha256",
        "HostReturnGameCopyTest.java": "host_return_test_sha256",
        "EffectCleanupGameCopyTest.java": "effect_cleanup_test_sha256",
        "DetainGameCopyTest.java": "detain_control_age_test_sha256",
        "ControlTurnGameCopyTest.java": "player_control_test_sha256",
        "ControlledDecisionAiTest.java": "decision_perspective_test_sha256",
        "ControlledCombatAiCoverageTest.java": "controlled_attack_test_sha256",
        "PlayerControlPlanningTest.java": "primary_control_test_sha256",
        "PreviewRandomIsolationTest.java": "preview_random_test_sha256",
        "ControlOutcomeScoringTest.java": "control_outcome_test_sha256",
        "CompletedPlanRecoveryTest.java": "completed_plan_test_sha256",
        "ResolvingControlForecastTest.java": "resolving_control_test_sha256",
        "SpellControlCoverageTest.java": "spell_control_test_sha256",
        "ComposedSpellControlTest.java": "composed_control_test_sha256",
        "OptionalTriggerCheckpointTest.java": "optional_checkpoint_test_sha256",
        "SpellControlCastHistoryTest.java": "spell_cast_provenance_test_sha256",
        "MassCounterRemovalTest.java": "mass_counter_test_sha256",
        "PaidOutcomePlannerTest.java": "paid_outcome_test_sha256",
        "CulturalExchangeCoverageTest.java": "cultural_exchange_test_sha256",
        "MultiplePilesCoverageTest.java": "multiple_piles_test_sha256",
        "ExchangeChoiceContractsTest.java": "exchange_choice_test_sha256",
        "AITest.java": "packaged_ai_base_sha256",
        "DrawEndingCoverageTest.java": "draw_ending_test_sha256",
        "CostPreviewIsolationTest.java": "cost_preview_test_sha256",
        "ReflectedManaCoverageTest.java": "reflected_mana_test_sha256",
        "ManaTriggerFollowupTest.java": "mana_trigger_test_sha256",
        "MixedReflectedManaTest.java": "mixed_reflected_mana_test_sha256",
        "ManaAddedCoverageTest.java": "mana_added_test_sha256",
        "SnowManaProjectionTest.java": "snow_mana_projection_test_sha256",
        "ManaReplacementCoverageTest.java": "mana_replacement_test_sha256",
        "ChosenManaReplacementProbeTest.java": "chosen_mana_replacement_test_sha256",
        "UnpaidCastRecoveryProbeTest.java": "unpaid_cast_recovery_test_sha256",
        "OverlappingCastCostsProbeTest.java": "overlapping_cast_costs_test_sha256",
        "NonmanaPaymentRecoveryProbeTest.java": "nonmana_payment_recovery_test_sha256",
        "FailedActivationManaProbeTest.java": "failed_activation_mana_test_sha256",
        "FailedTapCostProbeTest.java": "failed_tap_cost_test_sha256",
        "FailedGroupTapCostProbeTest.java": "failed_group_tap_cost_test_sha256",
        "GroupTapSelectionProbeTest.java": "group_tap_selection_test_sha256",
        "TapManaOverlapProbeTest.java": "tap_mana_overlap_test_sha256",
        "NestedTapManaOverlapProbeTest.java": "nested_tap_mana_overlap_test_sha256",
        "FilterTapManaOverlapProbeTest.java": "filter_tap_mana_overlap_test_sha256",
        "NestedManaRollbackProbeTest.java": "nested_mana_rollback_test_sha256",
        "EnergyCostRecoveryProbeTest.java": "energy_cost_recovery_test_sha256",
        "DiscardCostRecoveryProbeTest.java": "discard_cost_recovery_test_sha256",
        "MixedDiscardRecoveryProbeTest.java": "mixed_discard_recovery_test_sha256",
        "CardCounterCostRecoveryProbeTest.java": "card_counter_recovery_test_sha256",
        "GroupedCounterCostProbeTest.java": "grouped_counter_cost_test_sha256",
        "SacrificeCostRecoveryProbeTest.java": "sacrifice_cost_recovery_test_sha256",
        "StarTrekSagaProbeTest.java": "star_trek_saga_test_sha256",
        "StarTrekDilemmaProbeTest.java": "star_trek_dilemma_test_sha256",
        "StarTrekBeamResolutionTest.java": "star_trek_beam_resolution_test_sha256",
        "StarTrekCombatEntryTest.java": "star_trek_combat_entry_test_sha256",
        "CardCharacteristicDiscrepancyTest.java": "card_characteristic_discrepancy_test_sha256",
        "StarTrekBattlefrontTest.java": "star_trek_battlefront_test_sha256",
        "StarTrekFederationTest.java": "star_trek_federation_test_sha256",
        "StarTrekRevealTest.java": "star_trek_reveal_test_sha256",
        "CommanderDiceReplacementTest.java": "commander_dice_replacement_test_sha256",
        "CommanderOptionalDiePaymentTest.java": "commander_optional_die_payment_test_sha256",
        "CommanderExploreTest.java": "commander_explore_test_sha256",
        "CommanderDiscoverTest.java": "commander_discover_test_sha256",
        "CommanderDiscoverLkiTest.java": "commander_discover_lki_test_sha256",
        "CommanderDiscoverScalarTest.java": "commander_discover_scalar_test_sha256",
        "CommanderDiscoverVehicleTest.java": "commander_discover_vehicle_test_sha256",
        "CommanderDiscoverCastFormsTest.java": "commander_discover_cast_forms_test_sha256",
        "CommanderDiscoverTaxTest.java": "commander_discover_tax_test_sha256",
        "CommanderDiscoverSacrificeTest.java": "commander_discover_sacrifice_test_sha256",
        "CommanderSwitchBlockAiTest.java": "commander_switch_block_ai_test_sha256",
        "CommanderEmpowerAiTest.java": "commander_empower_ai_test_sha256",
        "CommanderChangeTextAiTest.java": "commander_change_text_ai_test_sha256",
        "CommanderTextCompositionTest.java": "commander_text_composition_test_sha256",
        "CommanderPluralTextTest.java": "commander_plural_text_test_sha256",
        "CommanderLookAtCoverageTest.java": "commander_look_at_coverage_test_sha256",
        "CommanderSpyNetworkKnowledgeTest.java": "commander_spy_network_knowledge_test_sha256",
        "CommanderLibraryLookAtPositionTest.java": "commander_library_look_at_position_test_sha256",
        "CommanderCrystalSprayTest.java": "commander_crystal_spray_test_sha256",
        "CommanderCrystalSprayRulesTest.java": "commander_crystal_spray_rules_test_sha256",
        "CommanderCrystalSprayStackTest.java": "commander_crystal_spray_stack_test_sha256",
        "CommanderBaubleCoverageTest.java": "commander_bauble_coverage_test_sha256",
        "CommanderVintageOpeningReplayTest.java": "commander_vintage_opening_test_sha256",
        "CommanderCompositeModeCoverageTest.java": "commander_composite_mode_test_sha256",
        "CommanderModalTextCoverageTest.java": "commander_modal_text_test_sha256",
        "CommanderModalBudgetTest.java": "commander_modal_budget_test_sha256",
        "CommanderVillainousChoiceTest.java": "commander_villainous_choice_test_sha256",
        "CommanderLearnTest.java": "commander_learn_test_sha256",
        "CommanderReverseTurnOrderTest.java": "commander_reverse_turn_order_test_sha256",
        "CommanderExtraTurnDirectionTest.java": "commander_extra_turn_direction_test_sha256",
        "CommanderEndTurnTest.java": "commander_end_turn_test_sha256",
        "CommanderIncubateTest.java": "commander_incubate_test_sha256",
        "CommanderTransformHistoryTest.java": "commander_transform_history_test_sha256",
        "CommanderSnapshotTransformStackTest.java": "commander_snapshot_stack_test_sha256",
        "CommanderOracleCorrectionsTest.java": "commander_oracle_corrections_test_sha256",
        "CommanderTemptCopyTest.java": "commander_tempt_copy_test_sha256",
        "CommanderConditionalManaTest.java": "commander_conditional_mana_test_sha256",
        "CommanderUlalekTest.java": "commander_ulalek_test_sha256",
        "CommanderUlalekAbilityOnlyTest.java": "commander_ulalek_ability_only_test_sha256",
        "CommanderReaperLimitTest.java": "commander_reaper_limit_test_sha256",
        "CommanderRinoaReturnTest.java": "commander_rinoa_return_test_sha256",
        "CommanderPreventedReturnTest.java": "commander_prevented_return_test_sha256",
        "CommanderTargetedReturnTest.java": "commander_targeted_return_test_sha256",
        "CommanderOptionalLifeGainTest.java": "commander_optional_life_gain_test_sha256",
        "CommanderOptionalDrawTest.java": "commander_optional_draw_test_sha256",
        "CommanderOptionalCopyTest.java": "commander_optional_copy_test_sha256",
        "CommanderCarpetManaTest.java": "commander_carpet_mana_test_sha256",
        "CommanderOptionalCountersTest.java": "commander_optional_counters_test_sha256",
        "CommanderOptionalXTest.java": "commander_optional_x_test_sha256",
        "CommanderPriorityBoardingTest.java": "commander_priority_boarding_test_sha256",
        "CommanderOptionalPlayTest.java": "commander_optional_play_test_sha256",
        "CommanderSecretVoteTest.java": "commander_secret_vote_test_sha256",
        "CommanderEmptyVoteTest.java": "commander_empty_vote_test_sha256",
        "CommanderPublicVoteTest.java": "commander_public_vote_test_sha256",
        "CommanderCirdanTest.java": "commander_cirdan_test_sha256",
        "CommanderTimeTravelTest.java": "commander_time_travel_test_sha256",
        "ProliferateFightAndInvestigateTest.java": "proliferate_fight_investigate_test_sha256",
        "StarTrekHoloEngineerTest.java": "star_trek_holo_engineer_test_sha256",
        "StarTrekNamedAbilityTest.java": "star_trek_named_ability_test_sha256",
        "StarTrekStationTest.java": "star_trek_station_test_sha256",
        "StarTrekKobayashiTest.java": "star_trek_kobayashi_test_sha256",
        "StarTrekAssimilateProbeTest.java": "star_trek_assimilate_test_sha256",
    }
    for filename, hash_key in regression_sources.items():
        if file_hash(PROJECT / "engine/tests" / filename) != recipe[hash_key]:
            raise ValueError(
                f"Native regression source {filename} changed; review the recipe"
            )
    _verify_priority_fixtures(recipe["upstream_priority_tests"])
    if recipe["card_overrides"] != [
        "w/wicker_picker.txt",
        "t/the_prismatic_piper.txt",
        "f/faceless_one.txt",
        "w/worst_fears.txt",
        "m/mindslaver.txt",
        "a/aethersnatch.txt",
        "c/commandeer.txt",
        "p/perplexing_chimera.txt",
        "s/sudden_substitution.txt",
        "a/aether_snap.txt",
        "d/dust_of_moments.txt",
        "o/oblivion_stone.txt",
        "c/cultural_exchange.txt",
        "w/whims_of_the_fates.txt",
        "d/divine_intervention.txt",
        "o/overabundance.txt",
        "a/all_good_things.txt",
        "h/hit_it.txt",
        "c/cirdan_the_shipwright.txt",
        "i/ixidors_will.txt",
        "v/vault_13_dwellers_journey.txt",
        "c/chefs_kiss.txt",
        "u/ulalek_fused_atrocity.txt",
        "t/tempt_with_mayhem.txt",
        "t/the_reaper_king_no_more.txt",
        "r/rinoa_angel_wing.txt",
        "s/skyfisher_spider.txt",
        "u/uro_titan_of_natures_wrath.txt",
        "c/carpet_of_flowers.txt",
        "p/priority_boarding.txt",
        "e/emet_selch_of_the_third_seat.txt",
        "g/general_jarkeld.txt",
        "s/sorrows_path.txt",
        "a/alter_reality.txt",
        "b/balduvian_shaman.txt",
        "g/glamerdye.txt",
        "m/magical_hack.txt",
        "s/sleight_of_mind.txt",
        "m/mishras_bauble.txt",
        "w/whim_of_volrath.txt",
        "m/mind_bend.txt",
        "s/spectral_shift.txt",
        "c/crystal_spray.txt",
    ]:
        raise ValueError("Unexpected native card override registry")
    if not re.fullmatch(r"[a-f0-9]{40}", recipe["forge_commit"]):
        raise ValueError("Expected an immutable source commit")
    executable = "java.exe" if sys.platform == "win32" else "java"
    if not (args.jdk / "bin" / executable).is_file() or not args.runtime.is_file():
        raise ValueError("Select a trusted installed JDK and Java runtime executable")


def _verify_unchanged_inputs(args, recipe, recipe_hash):
    """Do not stamp a build with a recipe or reviewed input edited during compilation."""
    if file_hash(PROJECT / "engine/sticker_recipe.json") != recipe_hash:
        raise ValueError("Engine build recipe changed during compilation")
    _verify_inputs(args, recipe)


def _extract(path, destination, prefix):
    destination.mkdir()
    with zipfile.ZipFile(path) as archive:
        members = archive.infolist()
        if len(members) > 100000 or sum(item.file_size for item in members) > 1024**3:
            raise ValueError("Build archive exceeds extraction limits")
        for item in members:
            parts = Path(item.filename).parts
            if not _safe_member(item, parts, prefix):
                raise ValueError("Unsafe build archive member")
            if item.is_dir() or len(parts) == 1:
                continue
            target = destination.joinpath(*parts[1:])
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(item))


def _checked(command, cwd, run, name, timeout=30):
    process = run_bounded(
        command,
        cwd,
        run / (name + ".log"),
        ProcessLimits(timeout, environment=_environment(run)),
    )
    if process.get("status") != "finished" or process.get("returncode") != 0:
        raise ValueError(
            "Engine build step failed; inspect " + str(run / (name + ".log"))
        )
    return process


def _patch_source(source, run, recipe):
    git = shutil.which("git")
    if not git:
        raise ValueError("Git is required to apply the reviewed engine patches")
    directory = source.relative_to(PROJECT).as_posix()
    for index, entry in enumerate(recipe["patches"]):
        patch_path = str(PROJECT / entry["path"])
        _checked(
            [git, "apply", "--check", "--directory=" + directory, patch_path],
            PROJECT,
            run,
            f"patch-{index}-check",
        )
        _checked(
            [git, "apply", "--directory=" + directory, patch_path],
            PROJECT,
            run,
            f"patch-{index}-apply",
        )
    priority_dir = (
        source / "forge-gui-desktop/src/test/java/forge/gamesimulationtests/priority"
    )
    priority_dir.mkdir(parents=True, exist_ok=True)
    for name in recipe["upstream_priority_tests"]:
        fixture = f"{name}.java"
        shutil.copyfile(PROJECT / "engine/tests" / fixture, priority_dir / fixture)
    pom = source / "forge-gui-desktop/pom.xml"
    text = pom.read_text(encoding="utf-8")
    if text.count("<headerType>gui</headerType>") != 1:
        raise ValueError("Unexpected GUI launcher packaging configuration")
    pom.write_text(
        text.replace(
            "<headerType>gui</headerType>",
            "<skip>true</skip>\n                            <headerType>gui</headerType>",
        ),
        encoding="utf-8",
    )
    shutil.copyfile(
        PROJECT / "engine/tests/StickerKickerRegressionTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/StickerKickerRegressionTest.java",
    )
    shutil.copyfile(
        PROJECT / "engine/tests/CommanderColorChoiceTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/CommanderColorChoiceTest.java",
    )
    shutil.copyfile(
        PROJECT / "engine/tests/CompanionPregameTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/CompanionPregameTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/SupplementaryGameCopyTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/SupplementaryGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/StickerGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/StickerGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/HistoricalGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/HistoricalGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/DamageGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/DamageGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/ParticipantGameCopyTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/ParticipantGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/StartingLifeGameCopyTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/StartingLifeGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/StackGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/StackGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/FaceGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/FaceGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/UsageGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/UsageGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/TurnUsageGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/TurnUsageGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/DelayedGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/DelayedGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/TurnScheduleGameCopyTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/TurnScheduleGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/ConditionalGameCopyTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/ConditionalGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/AnimationGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/AnimationGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/CloneDurationGameCopyTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/CloneDurationGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/ProtectionGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/ProtectionGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/KeywordExpirationGameCopyTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/KeywordExpirationGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/BlockingPreventionGameCopyTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/BlockingPreventionGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/RingTextGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/RingTextGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/AttachmentManaGameCopyTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/AttachmentManaGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/HostReturnGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/HostReturnGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/EffectCleanupGameCopyTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/EffectCleanupGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/DetainGameCopyTest.java",
        source / "forge-gui-desktop/src/test/java/forge/ai/DetainGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/ControlTurnGameCopyTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/ControlTurnGameCopyTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/ControlledDecisionAiTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/ControlledDecisionAiTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/ControlledCombatAiCoverageTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/ControlledCombatAiCoverageTest.java",
    )

    for filename in (
        "PlayerControlPlanningTest.java",
        "PreviewRandomIsolationTest.java",
        "ControlOutcomeScoringTest.java",
        "CompletedPlanRecoveryTest.java",
        "SpellControlCoverageTest.java",
        "ComposedSpellControlTest.java",
        "OptionalTriggerCheckpointTest.java",
        "SpellControlCastHistoryTest.java",
        "MassCounterRemovalTest.java",
        "PaidOutcomePlannerTest.java",
        "CulturalExchangeCoverageTest.java",
        "MultiplePilesCoverageTest.java",
        "ExchangeChoiceContractsTest.java",
        "AITest.java",
        "DrawEndingCoverageTest.java",
        "CostPreviewIsolationTest.java",
        "ReflectedManaCoverageTest.java",
        "ManaTriggerFollowupTest.java",
        "MixedReflectedManaTest.java",
        "ManaAddedCoverageTest.java",
        "SnowManaProjectionTest.java",
        "ManaReplacementCoverageTest.java",
        "ChosenManaReplacementProbeTest.java",
        "UnpaidCastRecoveryProbeTest.java",
        "OverlappingCastCostsProbeTest.java",
        "NonmanaPaymentRecoveryProbeTest.java",
        "FailedActivationManaProbeTest.java",
        "FailedTapCostProbeTest.java",
        "FailedGroupTapCostProbeTest.java",
        "GroupTapSelectionProbeTest.java",
        "TapManaOverlapProbeTest.java",
        "NestedTapManaOverlapProbeTest.java",
        "FilterTapManaOverlapProbeTest.java",
        "NestedManaRollbackProbeTest.java",
        "EnergyCostRecoveryProbeTest.java",
        "DiscardCostRecoveryProbeTest.java",
        "MixedDiscardRecoveryProbeTest.java",
        "CardCounterCostRecoveryProbeTest.java",
        "GroupedCounterCostProbeTest.java",
        "SacrificeCostRecoveryProbeTest.java",
        "StarTrekSagaProbeTest.java",
        "StarTrekDilemmaProbeTest.java",
        "StarTrekBeamResolutionTest.java",
        "StarTrekCombatEntryTest.java",
        "CardCharacteristicDiscrepancyTest.java",
        "StarTrekBattlefrontTest.java",
        "StarTrekFederationTest.java",
        "StarTrekRevealTest.java",
        "CommanderDiceReplacementTest.java",
        "CommanderOptionalDiePaymentTest.java",
        "CommanderExploreTest.java",
        "CommanderDiscoverTest.java",
        "CommanderDiscoverLkiTest.java",
        "CommanderDiscoverScalarTest.java",
        "CommanderDiscoverVehicleTest.java",
        "CommanderDiscoverCastFormsTest.java",
        "CommanderDiscoverTaxTest.java",
        "CommanderDiscoverSacrificeTest.java",
        "CommanderSwitchBlockAiTest.java",
        "CommanderEmpowerAiTest.java",
        "CommanderChangeTextAiTest.java",
        "CommanderTextCompositionTest.java",
        "CommanderPluralTextTest.java",
        "CommanderLookAtCoverageTest.java",
        "CommanderSpyNetworkKnowledgeTest.java",
        "CommanderLibraryLookAtPositionTest.java",
        "CommanderCrystalSprayTest.java",
        "CommanderCrystalSprayRulesTest.java",
        "CommanderCrystalSprayStackTest.java",
        "CommanderBaubleCoverageTest.java",
        "CommanderVintageOpeningReplayTest.java",
        "CommanderCompositeModeCoverageTest.java",
        "CommanderModalTextCoverageTest.java",
        "CommanderVillainousChoiceTest.java",
        "CommanderLearnTest.java",
        "CommanderReverseTurnOrderTest.java",
        "CommanderExtraTurnDirectionTest.java",
        "CommanderEndTurnTest.java",
        "CommanderIncubateTest.java",
        "CommanderTransformHistoryTest.java",
        "CommanderSnapshotTransformStackTest.java",
        "CommanderOracleCorrectionsTest.java",
        "CommanderTemptCopyTest.java",
        "CommanderConditionalManaTest.java",
        "CommanderUlalekTest.java",
        "CommanderUlalekAbilityOnlyTest.java",
        "CommanderReaperLimitTest.java",
        "CommanderRinoaReturnTest.java",
        "CommanderPreventedReturnTest.java",
        "CommanderTargetedReturnTest.java",
        "CommanderOptionalLifeGainTest.java",
        "CommanderOptionalDrawTest.java",
        "CommanderOptionalCopyTest.java",
        "CommanderCarpetManaTest.java",
        "CommanderOptionalCountersTest.java",
        "CommanderOptionalXTest.java",
        "CommanderPriorityBoardingTest.java",
        "CommanderOptionalPlayTest.java",
        "CommanderSecretVoteTest.java",
        "CommanderEmptyVoteTest.java",
        "CommanderPublicVoteTest.java",
        "CommanderCirdanTest.java",
        "CommanderTimeTravelTest.java",
        "ProliferateFightAndInvestigateTest.java",
        "StarTrekHoloEngineerTest.java",
        "StarTrekNamedAbilityTest.java",
        "StarTrekStationTest.java",
        "StarTrekKobayashiTest.java",
        "StarTrekAssimilateProbeTest.java",
    ):
        shutil.copyfile(
            PROJECT / "engine/tests" / filename,
            source / "forge-gui-desktop/src/test/java/forge/ai" / filename,
        )

    shutil.copyfile(
        PROJECT / "engine/tests/CommanderModalBudgetTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/simulation/CommanderModalBudgetTest.java",
    )

    shutil.copyfile(
        PROJECT / "engine/tests/ResolvingControlForecastTest.java",
        source
        / "forge-gui-desktop/src/test/java/forge/ai/simulation/ResolvingControlForecastTest.java",
    )


def _compile(args, source, maven, run):
    (run / "home").mkdir()
    # Maven resolves its checked-in relative settings path against the process
    # directory; Forge's native tests require the desktop-module directory.
    (source / ".mvn/maven.config").write_text(
        "--settings\n" + str(source / ".mvn/local-settings.xml") + "\n",
        encoding="utf-8",
    )
    executable = "java.exe" if sys.platform == "win32" else "java"
    command = [
        str(args.jdk / "bin" / executable),
        "-Xmx2048m",
        "-Djava.awt.headless=true",
    ]
    for module in (
        "java.base/java.lang",
        "java.base/java.time",
        "java.base/java.text",
        "java.base/java.util",
        "java.base/java.util.regex",
        "java.base/java.util.stream",
        "java.base/java.lang.reflect",
        "java.desktop/javax.imageio.spi",
    ):
        command.extend(["--add-opens", module + "=ALL-UNNAMED"])
    command.extend(
        [
            "-Duser.home=" + str(run / "home"),
            "-Dmaven.home=" + str(maven),
            "-Dmaven.multiModuleProjectDirectory=" + str(source),
            "-Dclassworlds.conf=" + str(maven / "bin/m2.conf"),
            "-classpath",
            str(next((maven / "boot").glob("plexus-classworlds-*.jar"))),
            "org.codehaus.plexus.classworlds.launcher.Launcher",
            "-B",
            "-ntp",
            "-DforkCount=0",
            "-Dmaven.repo.local=" + str(PROJECT / "data/engine-builds/m2"),
            "-Dmaven.compiler.proc=none",
            "-Dtest=Sticker*Test,CommanderColorChoiceTest,CompanionPregameTest,SupplementaryGameCopyTest,StickerGameCopyTest,HistoricalGameCopyTest,DamageGameCopyTest,ParticipantGameCopyTest,StartingLifeGameCopyTest,StackGameCopyTest,FaceGameCopyTest,UsageGameCopyTest,TurnUsageGameCopyTest,DelayedGameCopyTest,TurnScheduleGameCopyTest,ConditionalGameCopyTest,AnimationGameCopyTest,CloneDurationGameCopyTest,ProtectionGameCopyTest,KeywordExpirationGameCopyTest,BlockingPreventionGameCopyTest,RingTextGameCopyTest,AttachmentManaGameCopyTest,HostReturnGameCopyTest,EffectCleanupGameCopyTest,DetainGameCopyTest,ControlTurnGameCopyTest,ControlledDecisionAiTest,ControlledCombatAiCoverageTest,PlayerControlPlanningTest,PreviewRandomIsolationTest,ControlOutcomeScoringTest,CompletedPlanRecoveryTest,ResolvingControlForecastTest,SpellControlCoverageTest,ComposedSpellControlTest,OptionalTriggerCheckpointTest,SpellControlCastHistoryTest,MassCounterRemovalTest,PaidOutcomePlannerTest,CulturalExchangeCoverageTest,MultiplePilesCoverageTest,ExchangeChoiceContractsTest,DrawEndingCoverageTest,CostPreviewIsolationTest,ReflectedManaCoverageTest,ManaTriggerFollowupTest,MixedReflectedManaTest,ManaAddedCoverageTest,SnowManaProjectionTest,ManaReplacementCoverageTest,ChosenManaReplacementProbeTest,UnpaidCastRecoveryProbeTest,OverlappingCastCostsProbeTest,NonmanaPaymentRecoveryProbeTest,FailedActivationManaProbeTest,FailedTapCostProbeTest,FailedGroupTapCostProbeTest,GroupTapSelectionProbeTest,TapManaOverlapProbeTest,NestedTapManaOverlapProbeTest,FilterTapManaOverlapProbeTest,NestedManaRollbackProbeTest,EnergyCostRecoveryProbeTest,DiscardCostRecoveryProbeTest,MixedDiscardRecoveryProbeTest,CardCounterCostRecoveryProbeTest,GroupedCounterCostProbeTest,SacrificeCostRecoveryProbeTest,StarTrekSagaProbeTest,StarTrekDilemmaProbeTest,StarTrekAssimilateProbeTest,StarTrekBeamResolutionTest,StarTrekCombatEntryTest,StarTrekKobayashiTest,CardCharacteristicDiscrepancyTest,StarTrekBattlefrontTest,StarTrekFederationTest,StarTrekRevealTest,StarTrekStationTest,CommanderDiceReplacementTest,CommanderOptionalDiePaymentTest,CommanderExploreTest,CommanderDiscoverTest,CommanderDiscoverLkiTest,CommanderDiscoverScalarTest,CommanderDiscoverVehicleTest,CommanderDiscoverCastFormsTest,CommanderDiscoverTaxTest,CommanderDiscoverSacrificeTest,CommanderSwitchBlockAiTest,CommanderEmpowerAiTest,CommanderChangeTextAiTest,CommanderTextCompositionTest,CommanderPluralTextTest,CommanderLookAtCoverageTest,CommanderSpyNetworkKnowledgeTest,CommanderLibraryLookAtPositionTest,CommanderCrystalSprayTest,CommanderCrystalSprayRulesTest,CommanderCrystalSprayStackTest,CommanderBaubleCoverageTest,CommanderVintageOpeningReplayTest,CommanderCompositeModeCoverageTest,CommanderModalTextCoverageTest,CommanderModalBudgetTest,CommanderVillainousChoiceTest,CommanderLearnTest,CommanderReverseTurnOrderTest,CommanderExtraTurnDirectionTest,CommanderEndTurnTest,CommanderIncubateTest,CommanderTransformHistoryTest,CommanderSnapshotTransformStackTest,CommanderOracleCorrectionsTest,CommanderTemptCopyTest,CommanderConditionalManaTest,CommanderUlalekTest,CommanderUlalekAbilityOnlyTest,CommanderReaperLimitTest,CommanderRinoaReturnTest,CommanderPreventedReturnTest,CommanderTargetedReturnTest,CommanderOptionalLifeGainTest,CommanderOptionalDrawTest,CommanderOptionalCopyTest,CommanderCarpetManaTest,CommanderOptionalCountersTest,CommanderOptionalXTest,CommanderPriorityBoardingTest,CommanderOptionalPlayTest,forge.gamesimulationtests.priority.*Test,CommanderSecretVoteTest,CommanderEmptyVoteTest,CommanderPublicVoteTest,CommanderCirdanTest,CommanderTimeTravelTest,ProliferateFightAndInvestigateTest,StarTrekHoloEngineerTest,StarTrekNamedAbilityTest,ManaSacrificeTest,BeamMeUpTest",
            "-Dsurefire.failIfNoSpecifiedTests=false",
            "-f",
            str(source / "pom.xml"),
            "-pl",
            "forge-gui-desktop",
            "-am",
            "package",
        ]
    )
    _checked(command, source / "forge-gui-desktop", run, "maven-build", 600)


def _test_evidence(source, expected_count=33):
    path = source / "forge-gui-desktop/target/surefire-reports/testng-results.xml"
    with path.open("rb") as stream:
        prefix = stream.read(1024).decode("utf-8")
    header = re.search(r"<testng-results\b([^>]*)>", prefix)
    if header is None:
        raise ValueError("Missing native sticker test result header")
    values = dict(re.findall(r'(total|passed|failed|skipped)="(\d+)"', header[1]))
    if values != {
        "total": str(expected_count),
        "passed": str(expected_count),
        "failed": "0",
        "skipped": "0",
    }:
        raise ValueError("All pinned native regression tests must pass without skips")
    return {key: int(value) for key, value in values.items()}


def _package(archive_path, source, runtime, recipe):
    runtime.mkdir()
    shutil.copyfile(source / "LICENSE", runtime / "LICENSE.txt")
    prefix = "forge-" + recipe["forge_commit"] + "/forge-gui/res/"
    scripts = {}
    with zipfile.ZipFile(archive_path) as archive:
        for member in archive.infolist():
            if not member.filename.startswith(prefix) or member.is_dir():
                continue
            name = member.filename[len(prefix) :]
            raw = archive.read(member)
            if name.startswith("cardsfolder/"):
                if name.endswith(".txt"):
                    scripts[name[len("cardsfolder/") :]] = raw
            else:
                path = runtime / "res" / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(raw)
    for name in recipe["card_overrides"]:
        scripts[name] = (source / "forge-gui/res/cardsfolder" / name).read_bytes()
    folder = runtime / "res/cardsfolder"
    folder.mkdir()
    with zipfile.ZipFile(folder / "cardsfolder.zip", "x") as archive:
        for name, raw in sorted(scripts.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type, info.create_system, info.external_attr = (
                zipfile.ZIP_DEFLATED,
                3,
                0o644 << 16,
            )
            archive.writestr(info, raw, compresslevel=1)
    shutil.copyfile(
        source / "forge-gui-desktop/target" / recipe["jar_name"],
        runtime / recipe["jar_name"],
    )


def _lock(runtime, run, recipe, evidence, args):
    return {
        "forge_version": recipe["runtime_version"],
        "capabilities": recipe["capabilities"],
        "jar_name": recipe["jar_name"],
        "jar_sha256": file_hash(runtime / recipe["jar_name"]),
        "cards_sha256": file_hash(runtime / "res/cardsfolder/cardsfolder.zip"),
        "source_commit": recipe["forge_commit"],
        "source_archive_sha256": recipe["source_sha256"],
        "recipe_sha256": evidence["recipe_sha256"],
        "native_tests": evidence["native_tests"],
        "build_log_sha256": file_hash(run / "maven-build.log"),
        "edition_resources": edition_fingerprint(runtime),
        "runtime_executable_sha256": file_hash(args.runtime),
        "runtime_jvm_sha256": file_hash(Path(jvm_library(args.runtime))),
        "purpose": "Separately built candidate; run catalog, pregame and interaction checks before activation",
    }


def _safe_member(item, parts, prefix):
    if not parts or parts[0] != prefix or ".." in parts:
        return False
    return (
        ":" not in item.filename
        and not item.filename.startswith(("/", "\\"))
        and item.file_size <= 64 * 1024**2
        and (item.external_attr >> 16) & 0o170000 != 0o120000
    )


if __name__ == "__main__":
    main()
