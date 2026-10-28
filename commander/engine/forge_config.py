"""Trusted local engine configuration and pinned executable/resource checks."""

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path

from commander.json_safety import require_json_depth
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
    lock_path: Path | None = None

    def verify(self) -> dict:
        """Require the pinned Forge JAR and card scripts before executing Java."""
        lock = read_engine_lock(self.lock_path or LOCK_PATH)
        if self.java.name not in {"java", "java.exe"} or not self.java.is_file():
            raise ValueError("Configure a trusted Java executable, not a shell script")
        checks = [
            (self.home / lock["jar_name"], lock["jar_sha256"]),
            (self.home / "res/cardsfolder/cardsfolder.zip", lock["cards_sha256"]),
        ]
        if "runtime_executable_sha256" in lock:
            checks.append((self.java, lock["runtime_executable_sha256"]))
        if "runtime_jvm_sha256" in lock:
            checks.append((Path(jvm_library(self.java)), lock["runtime_jvm_sha256"]))
        for path, expected in checks:
            if not path.is_file() or file_hash(path) != expected:
                raise ValueError(f"Pinned engine integrity check failed: {path.name}")
        folder = self.home / "res/cardsfolder"
        if any(entry.name != "cardsfolder.zip" for entry in folder.iterdir()):
            raise ValueError(
                "Pinned card folder must contain only cardsfolder.zip; loose scripts are not admitted"
            )
        return lock


def read_settings(path: Path = DEFAULT_CONFIG) -> EngineSettings:
    """Load a bounded administrator configuration; paths are relative to that file."""
    with path.open("rb") as stream:
        raw = stream.read(4097)
    if len(raw) > 4096:
        raise ValueError("Engine configuration exceeds 4 KiB")
    data = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=_unique_fields)
    if not isinstance(data, dict) or set(data) not in (
        {"home", "java"},
        {"home", "java", "lock"},
    ):
        raise ValueError(
            "Engine configuration requires home/java and an optional pinned lock"
        )
    for value in data.values():
        if not isinstance(value, str) or not value or not value.isprintable():
            raise ValueError("Invalid engine configuration path")
    settings = EngineSettings(
        (path.resolve().parent / data["home"]).resolve(),
        (path.resolve().parent / data["java"]).resolve(),
        (path.resolve().parent / data["lock"]).resolve() if "lock" in data else None,
    )
    settings.verify()
    return settings


def read_engine_lock(path: Path) -> dict:
    """Bound trusted lock metadata and prevent resource names from escaping engine home."""
    with path.open("rb") as stream:
        raw = stream.read(65537)
    if len(raw) > 65536:
        raise ValueError("Engine lock exceeds 64 KiB")
    text = raw.decode("utf-8-sig")
    require_json_depth(text, 16)
    lock = json.loads(text, object_pairs_hook=_unique_fields)
    if not isinstance(lock, dict) or not {
        "jar_name",
        "jar_sha256",
        "cards_sha256",
    } <= set(lock):
        raise ValueError(
            "Engine lock requires pinned executable and card resource hashes"
        )
    if not isinstance(lock["jar_name"], str) or not re.fullmatch(
        r"[A-Za-z0-9][A-Za-z0-9._+-]{0,199}\.jar", lock["jar_name"]
    ):
        raise ValueError("Engine lock JAR name must be a plain bounded filename")
    for key in (
        "jar_sha256",
        "cards_sha256",
        "runtime_executable_sha256",
        "runtime_jvm_sha256",
    ):
        if key not in lock:
            continue
        if not isinstance(lock[key], str) or not re.fullmatch(
            r"[a-f0-9]{64}", lock[key]
        ):
            raise ValueError("Engine lock requires lowercase SHA-256 hashes")
    return lock


def _unique_fields(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate engine lock field")
        result[key] = value
    return result


def supported_java_version(value) -> bool:
    """Accept a bounded recorded Java major version supported by the native engine."""
    return (
        isinstance(value, str)
        and 0 < len(value) <= 100
        and value.split(".")[0].isdigit()
        and int(value.split(".")[0]) >= 17
    )


def require_engine_capabilities(lock: dict, entries: list) -> None:
    """Reject unsupported pregame choices before admitting a game or a batch."""
    if any(entry.get("zone") == "companion" for entry in entries):
        capabilities = lock.get("capabilities", [])
        if (
            not isinstance(capabilities, list)
            or "native_companion_pregame" not in capabilities
        ):
            raise ValueError(
                "Selected engine does not support reviewed companion pregame behavior; install the reviewed Commander build"
            )
    if any("commander_color" in entry for entry in entries):
        capabilities = lock.get("capabilities", [])
        if (
            not isinstance(capabilities, list)
            or "explicit_commander_colors" not in capabilities
        ):
            raise ValueError(
                "Selected engine does not support explicit commander colors; install the reviewed color-capable build"
            )


def jvm_library(java: Path) -> str:
    """Locate the JVM library paired with the selected trusted Java executable."""
    root = java.parent.parent
    for relative in (
        "bin/server/jvm.dll",
        "lib/server/libjvm.so",
        "lib/server/libjvm.dylib",
    ):
        path = root / relative
        if path.is_file():
            return str(path)
    raise ValueError("Configured Java runtime does not expose a supported JVM library")
