"""Bounded execution shared by fixed, opt-in native test workers."""

import sys

from commander.engine.engine_process import ProcessLimits, run_bounded
from commander.engine.forge_config import file_hash
from commander.engine.native_runtime import _engine_profile, _environment


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
