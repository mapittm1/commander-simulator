"""Bounded execution shared by fixed, opt-in native test workers."""

import importlib
import sys
from dataclasses import dataclass
from pathlib import Path

from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.interactions.native_bridge import initialize_native_engine
from commander.engine.interactions.suite import write_bounded_json
from commander.engine.native_runtime import (
    _engine_profile,
    _environment,
    jvm_library,
    require_bridge,
)


@dataclass(frozen=True)
class WorkerInvocation:
    """A trusted worker path and fixed internal arguments; uploads cannot supply them."""

    path: Path
    arguments: tuple[str, ...] = ()


def run_test_worker(
    settings,
    run: Path,
    worker: Path | WorkerInvocation,
    log_name: str,
    support_sources: tuple[Path, ...] = (),
):
    """Retain source identity and use the normal isolated engine profile lifecycle."""
    invocation = (
        worker if isinstance(worker, WorkerInvocation) else WorkerInvocation(worker)
    )
    before = file_hash(invocation.path)
    support_hashes = {str(path): file_hash(path) for path in support_sources}
    with _engine_profile(settings, run):
        process = run_bounded(
            [
                sys.executable,
                "-I",
                str(invocation.path),
                str(run),
                *invocation.arguments,
            ],
            settings.home,
            run / log_name,
            ProcessLimits(90, environment=_environment(run)),
        )
    if file_hash(invocation.path) != before:
        raise ValueError("Fixed native test worker changed during execution")
    if any(file_hash(Path(path)) != digest for path, digest in support_hashes.items()):
        raise ValueError("Fixed native test support source changed during execution")
    process["support_source_hashes"] = support_hashes
    if support_sources:
        write_bounded_json(
            run / "rules-process.json", {"process": process, "worker_sha256": before}
        )
    return process, before


def initialize_test_engine():
    """Start only the operator-selected verified engine in an isolated test worker."""
    settings = read_settings()
    require_bridge()
    engine = settings.verify()
    java = importlib.import_module("jpype")
    initialize_native_engine(
        java, jvm_library(settings.java), [str(settings.home / engine["jar_name"])]
    )
    return java, engine


def load_registered_deck(java, path):
    """Read a generated native export and assign its declared commanders."""
    deck = java.JClass("forge.deck.io.DeckSerializer").fromFile(
        java.JClass("java.io.File")(str(path))
    )
    registered = java.JClass("forge.game.player.RegisteredPlayer")(deck)
    registered.assignCommander()
    return registered
