"""Bounded execution shared by fixed, opt-in native test workers."""

import importlib
import sys

from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.interactions.native_bridge import initialize_native_engine
from commander.engine.native_runtime import (
    _engine_profile,
    _environment,
    jvm_library,
    require_bridge,
)


def run_test_worker(settings, run, worker, log_name):
    """Retain source identity and use the normal isolated engine profile lifecycle."""
    before = file_hash(worker)
    with _engine_profile(settings, run):
        process = run_bounded(
            [sys.executable, "-I", str(worker), str(run)],
            settings.home,
            run / log_name,
            ProcessLimits(90, environment=_environment(run)),
        )
    if file_hash(worker) != before:
        raise ValueError("Fixed native test worker changed during execution")
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
