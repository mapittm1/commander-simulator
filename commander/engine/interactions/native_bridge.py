"""Compile only the repository's fixed test controller with trusted local JDK tooling."""

import hashlib
import os
import re
import shutil
from pathlib import Path

from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import file_hash

SOURCE = Path(__file__).parent / "native/ReplacementChoiceController.java"
CLASS_RELATIVE = Path("native/commander/interactions/ReplacementChoiceController.class")
CLASS_NAME = "commander.interactions.ReplacementChoiceController"
PRIORITY_SOURCE = SOURCE.with_name("PriorityTestController.java")
PRIORITY_CLASS_RELATIVE = CLASS_RELATIVE.with_name("PriorityTestController.class")
PRIORITY_CLASS_NAME = "commander.interactions.PriorityTestController"
MAX_CLASS_BYTES = 65536


def priority_class_hash(run: Path) -> str:
    """Hash only the fixed generated priority controller."""
    return class_hash(run, relative=PRIORITY_CLASS_RELATIVE)


def class_hash(run: Path, *, relative=CLASS_RELATIVE) -> str:
    """Accept only the fixed generated class, with a small independent byte bound."""
    path = run / relative
    with path.open("rb") as stream:
        raw = stream.read(MAX_CLASS_BYTES + 1)
    if not 0 < len(raw) <= MAX_CLASS_BYTES:
        raise ValueError("Test controller class exceeds its size limit")
    return hashlib.sha256(raw).hexdigest()


def build_native_bridge(settings, run: Path, environment: dict) -> dict:
    """Disable annotation processors and compile fixed source to Java 17 bytecode."""
    configured = os.environ.get("COMMANDER_JAVAC") or shutil.which("javac")
    if not configured:
        raise ValueError(
            "Interaction tests require JDK 17+ javac; set COMMANDER_JAVAC to its executable"
        )
    compiler = Path(configured).resolve(strict=True)
    before = file_hash(compiler)
    output = run / "native"
    output.mkdir()
    log = run / "compiler.log"
    command = [
        str(compiler),
        "-version",
        "--release",
        "17",
        "-proc:none",
        "-encoding",
        "UTF-8",
        "-classpath",
        str(settings.home / settings.verify()["jar_name"]),
        "-d",
        str(output),
        str(PRIORITY_SOURCE),
        str(SOURCE),
    ]
    process = run_bounded(
        command, run, log, ProcessLimits(30, log_bytes=65536, environment=environment)
    )
    text = log.read_text(encoding="utf-8", errors="replace")
    version = re.search(r"(?m)^javac (\d+)(?:\.[^\r\n ]+)?", text)
    if (
        process.get("status") != "finished"
        or process.get("returncode") != 0
        or version is None
        or int(version[1]) < 17
    ):
        raise ValueError(
            "Fixed test controller compilation failed; inspect " + str(log)
        )
    if before != file_hash(compiler):
        raise ValueError("Java compiler changed during compilation")
    return {
        "compiler": str(compiler),
        "compiler_sha256": before,
        "compiler_version": version[0],
        "class_sha256": class_hash(run),
        "priority_class_sha256": priority_class_hash(run),
        "log_sha256": file_hash(log),
        "process": process,
    }


def bridge_unchanged(run: Path, evidence: dict) -> bool:
    """Reject changed generated code or compiler executables after the native run."""
    try:
        return (
            class_hash(run) == evidence["class_sha256"]
            and priority_class_hash(run) == evidence["priority_class_sha256"]
            and file_hash(Path(evidence["compiler"])) == evidence["compiler_sha256"]
        )
    except (KeyError, OSError, ValueError):
        return False


def fixture_errors(java) -> tuple:
    """Known native observation errors become explicit failed/incomplete evidence."""
    return (java.JException, RuntimeError, ValueError, TypeError, AttributeError)
