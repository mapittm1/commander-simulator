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
    }
    for filename, hash_key in regression_sources.items():
        if file_hash(PROJECT / "engine/tests" / filename) != recipe[hash_key]:
            raise ValueError(
                f"Native regression source {filename} changed; review the recipe"
            )
    if recipe["card_overrides"] != [
        "w/wicker_picker.txt",
        "t/the_prismatic_piper.txt",
        "f/faceless_one.txt",
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
            "-Dtest=Sticker*Test,CommanderColorChoiceTest,CompanionPregameTest,SupplementaryGameCopyTest,StickerGameCopyTest,HistoricalGameCopyTest,DamageGameCopyTest,ParticipantGameCopyTest,StartingLifeGameCopyTest,StackGameCopyTest,FaceGameCopyTest,UsageGameCopyTest,TurnUsageGameCopyTest,DelayedGameCopyTest,TurnScheduleGameCopyTest,ConditionalGameCopyTest,AnimationGameCopyTest,CloneDurationGameCopyTest,ProtectionGameCopyTest,KeywordExpirationGameCopyTest,BlockingPreventionGameCopyTest,RingTextGameCopyTest,AttachmentManaGameCopyTest,HostReturnGameCopyTest,EffectCleanupGameCopyTest,DetainGameCopyTest,ControlTurnGameCopyTest",
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
