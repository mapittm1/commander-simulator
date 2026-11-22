"""Shared compilation and execution for fixed, bounded packaged-native test families."""

import importlib
import os
import shutil
from dataclasses import dataclass
from pathlib import Path
from types import ModuleType
from typing import Any

from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import EngineSettings, file_hash, jvm_library
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.engine.native_runtime import _environment, require_bridge
from tests.support.native_receipts import write_test_receipts

SUPPORT_SOURCES = tuple(
    Path(__file__).with_name(name)
    for name in ("packaged_rules.py", "native_receipts.py", "native_worker.py")
)


@dataclass(frozen=True)
class RuleFamily:
    """Trusted source-selected class and receipt names; uploads cannot define families."""

    test_classes: tuple[str, ...]
    helper_classes: tuple[str, ...]
    native_classes: tuple[str, ...]
    manifest_name: str
    part_prefix: str


COMPILER_FLAGS = (
    "-version",
    "--release",
    "17",
    "-proc:none",
    "-implicit:none",
    "-encoding",
    "UTF-8",
)


def compile_rules(
    settings: EngineSettings, run: Path, sources: list[Path], dependency: Path
) -> tuple[Path, dict[str, str]]:
    """Compile only fixed hash-reviewed Java sources with the pinned native dependency."""
    configured = os.environ.get("COMMANDER_JAVAC") or shutil.which("javac")
    if not configured:
        raise ValueError(
            "Packaged rules tests require COMMANDER_JAVAC or javac on PATH"
        )
    compiler = Path(configured).resolve(strict=True)
    inputs = [compiler, dependency, Path(__file__).resolve(), *sources]
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


def class_origin(java: ModuleType, name: str) -> Path:
    """Locate the exact native archive or isolated test directory for a loaded class."""
    location = (
        java.JClass(name).class_.getProtectionDomain().getCodeSource().getLocation()
    )
    return Path(str(java.JClass("java.io.File")(location.toURI()).getCanonicalPath()))


def test_results(listener: Any) -> tuple[dict[str, int], list[dict[str, str]]]:
    """Retain every native method outcome, including failures and skips."""
    rows = []
    groups = (
        ("PASS", listener.getPassedTests()),
        ("FAIL", listener.getFailedTests()),
        ("SKIP", listener.getSkippedTests()),
    )
    for status, results in groups:
        rows.extend(
            {
                "method": f"{item.getTestClass().getName()}.{item.getMethod().getMethodName()}",
                "status": status,
            }
            for item in results
        )
    counts = {
        "total": len(rows),
        "passed": len(groups[0][1]),
        "failed": len(groups[1][1]),
        "skipped": len(groups[2][1]),
    }
    return counts, sorted(rows, key=lambda row: row["method"])


def run_rules(
    settings: EngineSettings, run: Path, dependency: Path, family: RuleFamily
) -> None:
    """Compile fixed reviewed sources and retain hash-bound native outcomes."""
    engine = settings.verify()
    metadata = read_bounded_json(run / "rules-inputs.json")
    output = run / "rules-classes"
    if metadata["classes"] != {
        path.relative_to(output).as_posix(): file_hash(path)
        for path in output.rglob("*.class")
    }:
        raise ValueError("Compiled fixed rules classes changed before execution")
    if any(
        file_hash(Path(path)) != value for path, value in metadata["inputs"].items()
    ):
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
    for name in family.native_classes:
        if not class_origin(java, name).samefile(archive):
            raise ValueError(
                "Rules tests did not load production classes from the packaged JAR"
            )
    for name in (*family.helper_classes, *family.test_classes):
        if not class_origin(java, name).samefile(output):
            raise ValueError(
                "Rules test classes did not load from the isolated compilation"
            )
    runner = java.JClass("org.testng.TestNG")()
    listener = java.JClass("org.testng.TestListenerAdapter")()
    runner.addListener(listener)
    runner.setTestClasses(
        java.JArray(java.JClass("java.lang.Class"))(
            [java.JClass(name).class_ for name in family.test_classes]
        )
    )
    runner.setOutputDirectory(str(run / "rules-reports"))
    runner.setUseDefaultListeners(True)
    runner.run()
    counts, rows = test_results(listener)
    if any(
        file_hash(Path(path)) != value for path, value in metadata["inputs"].items()
    ):
        raise ValueError("Packaged rules inputs changed during execution")
    summary = {
        "counts": counts,
        "testng_status": int(runner.getStatus()),
        "inputs": metadata["inputs"],
        "classes": {
            path.relative_to(output).as_posix(): file_hash(path)
            for path in output.rglob("*.class")
        },
        "report_sha256": file_hash(run / "rules-reports/testng-results.xml"),
        "configuration_failures": len(listener.getConfigurationFailures()),
        "configuration_skips": len(listener.getConfigurationSkips()),
        "partial_successes": len(listener.getFailedButWithinSuccessPercentageTests()),
        "production_classes_from_packaged_jar": list(family.native_classes),
        "compiler_log_sha256": file_hash(run / "rules-compiler.log"),
    }
    if summary["classes"] != metadata["classes"]:
        raise ValueError("Compiled fixed rules classes changed during execution")
    write_test_receipts(
        run,
        engine,
        family.manifest_name,
        {
            f"{family.part_prefix}-summary.json": [summary],
            f"{family.part_prefix}-methods.json": rows,
        },
    )
