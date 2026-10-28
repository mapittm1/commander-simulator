"""Trusted process profiles and native bridge configuration, independent of game services."""

import json
import os
import uuid
from contextlib import contextmanager
from importlib import metadata
from pathlib import Path

from commander.engine.forge_config import EngineSettings
from commander.engine.forge_config import jvm_library as _jvm_library
from commander.paths import DATA_ROOT

BRIDGE_VERSION = "1.7.1"


def _environment(run: Path) -> dict:
    """Do not pass account tokens, Java option injection, or other parent secrets."""
    environment = {
        name: value
        for name, value in os.environ.items()
        if name.upper() in {"SYSTEMROOT", "WINDIR", "LANG", "LC_ALL"}
    }
    for variable, folder in (
        ("TEMP", "temp"),
        ("TMP", "temp"),
        ("APPDATA", "appdata"),
        ("LOCALAPPDATA", "localappdata"),
    ):
        path = run / folder
        path.mkdir(exist_ok=True)
        environment[variable] = str(path)
    return environment


@contextmanager
def _engine_profile(settings: EngineSettings, run: Path):
    """Serialize access to shared engine assets and restore the exact original profile."""
    profile = settings.home / "forge.profile.properties"
    if profile.is_symlink():
        raise ValueError("Engine profile must not be a symbolic link")
    lock = settings.home / ".commander-simulator.lock"
    with lock.open("x", encoding="utf-8") as handle:
        handle.write(json.dumps({"pid": os.getpid(), "run": run.name}))
    original = None
    captured = False
    try:
        original = profile.read_bytes() if profile.exists() else None
        captured = True
        profile.write_text(
            f"userDir={(run / 'user').as_posix()}\ncacheDir={(run / 'cache').as_posix()}\n",
            encoding="utf-8",
        )
        yield
    finally:
        try:
            if captured:
                if original is None:
                    profile.unlink(missing_ok=True)
                else:
                    profile.write_bytes(original)
        finally:
            lock.unlink(missing_ok=True)


def create_run(root: Path = DATA_ROOT / "runs") -> Path:
    """Create a unique private artifact directory without overwriting earlier runs."""
    run = root / uuid.uuid4().hex
    run.mkdir(parents=True, exist_ok=False)
    return run


def require_bridge(version_reader=None) -> None:
    """Require the optional exact native bridge version without starting a JVM."""
    version_reader = version_reader or metadata.version
    try:
        version = version_reader("JPype1")
    except metadata.PackageNotFoundError as error:
        raise ValueError(
            "Install optional interaction tools: python -m pip install -r requirements-interactions.txt"
        ) from error
    if version != BRIDGE_VERSION:
        raise ValueError(
            "Interaction tests require the pinned JPype1 version; install requirements-interactions.txt"
        )


def jvm_library(java: Path) -> str:
    """Find only a supported JVM library beside the selected trusted runtime."""
    return _jvm_library(java)
