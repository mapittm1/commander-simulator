"""Trusted local engine configuration and pinned executable/resource checks."""

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

from commander.paths import CONFIG_ROOT

LOCK_PATH = CONFIG_ROOT / "engine_lock.json"
DEFAULT_CONFIG = CONFIG_ROOT / "engine_config.json"


def file_hash(path: Path) -> str:
    """Hash a file incrementally without loading executable archives into memory."""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


@dataclass(frozen=True)
class EngineSettings:
    """Operator-owned configuration; never populated from a user's deck upload."""

    home: Path
    java: Path

    def verify(self) -> dict:
        """Require the pinned Forge JAR and card scripts before executing Java."""
        lock = json.loads(LOCK_PATH.read_text(encoding="utf-8"))
        if self.java.name not in {"java", "java.exe"} or not self.java.is_file():
            raise ValueError("Configure a trusted Java executable, not a shell script")
        for path, expected in (
            (self.home / lock["jar_name"], lock["jar_sha256"]),
            (self.home / "res/cardsfolder/cardsfolder.zip", lock["cards_sha256"]),
        ):
            if not path.is_file() or file_hash(path) != expected:
                raise ValueError(f"Pinned engine integrity check failed: {path.name}")
        return lock


def read_settings(path: Path = DEFAULT_CONFIG) -> EngineSettings:
    """Load a bounded administrator configuration; paths are relative to that file."""
    with path.open("rb") as stream:
        raw = stream.read(4097)
    if len(raw) > 4096:
        raise ValueError("Engine configuration exceeds 4 KiB")
    data = json.loads(raw.decode("utf-8-sig"))
    if not isinstance(data, dict) or set(data) != {"home", "java"}:
        raise ValueError("Engine configuration must contain exactly home and java")
    for value in data.values():
        if not isinstance(value, str) or not value or not value.isprintable():
            raise ValueError("Invalid engine configuration path")
    settings = EngineSettings(
        (path.resolve().parent / data["home"]).resolve(),
        (path.resolve().parent / data["java"]).resolve(),
    )
    settings.verify()
    return settings
