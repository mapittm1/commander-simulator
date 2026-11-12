"""Run fixed native rules tests against the verified packaged JAR, without Maven."""

import importlib
import json
import os
import shutil
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT))

# Fixed trusted test sources and dependencies; uploads cannot select executable code.
# pylint: disable=wrong-import-position
from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import file_hash, jvm_library, read_settings
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.engine.native_runtime import _environment, require_bridge
from tests.support.native_receipts import write_test_receipts

COMPILER_FLAGS = (
    "-version",
    "--release",
    "17",
    "-proc:none",
    "-implicit:none",
    "-encoding",
    "UTF-8",
)
SOURCES = {
    "AITest": "packaged_ai_base_sha256",
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
}
TEST_CLASSES = tuple(f"forge.ai.{name}" for name in SOURCES if name != "AITest")
NATIVE_CLASSES = (
    "forge.ai.ComputerUtilMana",
    "forge.ai.simulation.OnePlaySafetyChecker",
    "forge.game.Game",
    "forge.game.card.Card",
    "forge.game.trigger.TriggerCounterRemoved",
    "forge.game.ability.effects.GameDrawEffect",
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
    "forge.game.cost.CostDecisionMakerBase",
    "forge.ai.ComputerUtilCost",
    "forge.game.cost.CostTap",
    "forge.game.cost.CostTapType",
    "forge.game.cost.CostPayLife",
    "forge.game.player.Player",
    "forge.game.spellability.SpellAbility",
    "forge.game.cost.CostSacrifice",
    "forge.ai.simulation.GameCopier",
)


def _inputs():
    recipe = json.loads(
        (PROJECT / "engine/sticker_recipe.json").read_text(encoding="utf-8")
    )
    sources = [PROJECT / "engine/tests" / f"{name}.java" for name in SOURCES]
    for source, key in zip(sources, SOURCES.values()):
        if file_hash(source) != recipe[key]:
            raise ValueError("Reviewed packaged rules source changed")
    dependency = recipe["packaged_testng"]
    expected = "data/engine-builds/m2/org/testng/testng/7.10.2/testng-7.10.2.jar"
    if dependency["path"] != expected:
        raise ValueError("Unexpected fixed TestNG dependency path")
    jar = PROJECT / expected
    if file_hash(jar) != dependency["sha256"]:
        raise ValueError("Pinned local TestNG dependency changed")
    return sources, jar


def _compile(settings, run, sources, dependency):
    configured = os.environ.get("COMMANDER_JAVAC") or shutil.which("javac")
    if not configured:
        raise ValueError(
            "Packaged rules tests require COMMANDER_JAVAC or javac on PATH"
        )
    compiler = Path(configured).resolve(strict=True)
    inputs = [compiler, dependency, *sources]
    hashes = {str(path): file_hash(path) for path in inputs}
    output = run / "rules-classes"
    output.mkdir()
    archive = settings.home / settings.verify()["jar_name"]
    command = [
        str(compiler),
        *COMPILER_FLAGS,
        "-classpath",
        os.pathsep.join(map(str, (archive, dependency))),
        "-d",
        str(output),
        *map(str, sources),
    ]
    process = run_bounded(
        command,
        run,
        run / "rules-compiler.log",
        ProcessLimits(30, log_bytes=65536, environment=_environment(run)),
    )
    if process.get("status") != "finished" or process.get("returncode") != 0:
        raise ValueError(
            "Fixed packaged rules compilation failed; inspect rules-compiler.log"
        )
    if any(file_hash(Path(path)) != value for path, value in hashes.items()):
        raise ValueError("Packaged rules inputs changed during compilation")
    classes = {
        path.relative_to(output).as_posix(): file_hash(path)
        for path in output.rglob("*.class")
    }
    write_bounded_json(
        run / "rules-inputs.json", {"inputs": hashes, "classes": classes}
    )
    return output, hashes


def _origin(java, name):
    location = (
        java.JClass(name).class_.getProtectionDomain().getCodeSource().getLocation()
    )
    return Path(str(java.JClass("java.io.File")(location.toURI()).getCanonicalPath()))


def _results(listener):
    rows = []
    groups = (
        ("PASS", listener.getPassedTests()),
        ("FAIL", listener.getFailedTests()),
        ("SKIP", listener.getSkippedTests()),
    )
    for status, results in groups:
        rows.extend(
            {"method": str(item.getMethod().getMethodName()), "status": status}
            for item in results
        )
    counts = {
        "total": len(rows),
        "passed": len(groups[0][1]),
        "failed": len(groups[1][1]),
        "skipped": len(groups[2][1]),
    }
    return counts, sorted(rows, key=lambda row: row["method"])


def main() -> None:
    """Compile fixed reviewed sources and retain hash-bound native outcomes."""
    settings = read_settings()
    engine = settings.verify()
    run = Path(sys.argv[1]).resolve(strict=True)
    _, dependency = _inputs()
    metadata = read_bounded_json(run / "rules-inputs.json")
    output, hashes = run / "rules-classes", metadata["inputs"]
    if metadata["classes"] != {
        path.relative_to(output).as_posix(): file_hash(path)
        for path in output.rglob("*.class")
    }:
        raise ValueError("Compiled fixed rules classes changed before execution")
    if any(file_hash(Path(path)) != value for path, value in hashes.items()):
        raise ValueError("Packaged rules inputs changed before execution")
    require_bridge()
    java = importlib.import_module("jpype")
    archive = settings.home / engine["jar_name"]
    java.startJVM(
        jvm_library(settings.java),
        "-Xmx2048m",
        "-XX:ActiveProcessorCount=2",
        "-Djava.awt.headless=true",
        "-Dfile.encoding=UTF-8",
        classpath=list(map(str, (archive, output, dependency))),
        convertStrings=True,
    )
    for name in NATIVE_CLASSES:
        if not _origin(java, name).samefile(archive):
            raise ValueError(
                "Rules tests did not load production classes from the packaged JAR"
            )
    for name in ("forge.ai.AITest", *TEST_CLASSES):
        if not _origin(java, name).samefile(output):
            raise ValueError(
                "Rules test classes did not load from the isolated compilation"
            )
    runner = java.JClass("org.testng.TestNG")()
    listener = java.JClass("org.testng.TestListenerAdapter")()
    runner.addListener(listener)
    runner.setTestClasses(
        java.JArray(java.JClass("java.lang.Class"))(
            [java.JClass(name).class_ for name in TEST_CLASSES]
        )
    )
    runner.setOutputDirectory(str(run / "rules-reports"))
    runner.setUseDefaultListeners(True)
    runner.run()
    counts, rows = _results(listener)
    if any(file_hash(Path(path)) != value for path, value in hashes.items()):
        raise ValueError("Packaged rules inputs changed during execution")
    summary = {
        "counts": counts,
        "testng_status": int(runner.getStatus()),
        "inputs": hashes,
        "classes": {
            path.relative_to(output).as_posix(): file_hash(path)
            for path in output.rglob("*.class")
        },
        "report_sha256": file_hash(run / "rules-reports/testng-results.xml"),
        "configuration_failures": len(listener.getConfigurationFailures()),
        "configuration_skips": len(listener.getConfigurationSkips()),
        "partial_successes": len(listener.getFailedButWithinSuccessPercentageTests()),
        "production_classes_from_packaged_jar": list(NATIVE_CLASSES),
        "compiler_log_sha256": file_hash(run / "rules-compiler.log"),
    }
    if summary["classes"] != metadata["classes"]:
        raise ValueError("Compiled fixed rules classes changed during execution")
    write_test_receipts(
        run,
        engine,
        "draw-endings.json",
        {"draw-ending-summary.json": [summary], "draw-ending-methods.json": rows},
    )


if __name__ == "__main__":
    main()
