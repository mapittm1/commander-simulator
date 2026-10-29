"""Bounded streaming evidence for catalog-wide native construction checks."""

import hashlib
import json
from pathlib import Path

from commander.json_safety import require_json_depth
from commander.paths import PROJECT_ROOT

MAX_ROWS = 100000
MAX_ROW_BYTES = 4096
MAX_STREAM_BYTES = 32 * 1024 * 1024


def stream_hash(path: Path) -> str:
    """Hash only a bounded evidence stream, including before its JSON is parsed."""
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        while raw := stream.read(65536):
            size += len(raw)
            if size > MAX_STREAM_BYTES:
                raise ValueError("Catalog probe stream exceeds its byte limit")
            digest.update(raw)
    return digest.hexdigest()


def encoded_row(row: dict) -> bytes:
    """Apply independent row limits before writing a stream record."""
    raw = (json.dumps(row, ensure_ascii=True, separators=(",", ":")) + "\n").encode(
        "utf-8"
    )
    if len(raw) > MAX_ROW_BYTES:
        raise ValueError("Catalog probe row exceeds 4 KiB")
    return raw


def read_rows(path: Path):
    """Never load a full catalog or a potentially unbounded line into memory."""
    size = 0
    with path.open("rb") as stream:
        for index in range(MAX_ROWS + 1):
            raw = stream.readline(MAX_ROW_BYTES + 1)
            if not raw:
                return
            size += len(raw)
            if index == MAX_ROWS or len(raw) > MAX_ROW_BYTES or size > MAX_STREAM_BYTES:
                raise ValueError("Catalog probe stream exceeds its record/byte limits")
            text = raw.decode("utf-8")
            require_json_depth(text, 8)
            row = json.loads(text, object_pairs_hook=_unique_fields)
            if not isinstance(row, dict):
                raise ValueError("Catalog probe records must be objects")
            yield row


def write_rows(path: Path, rows) -> tuple[int, str]:
    """Write an exclusive bounded stream and return its exact-byte fingerprint."""
    size, count = 0, 0
    digest = hashlib.sha256()
    with path.open("xb") as stream:
        for row in rows:
            raw = encoded_row(row)
            count += 1
            size += len(raw)
            if count > MAX_ROWS or size > MAX_STREAM_BYTES:
                raise ValueError("Catalog probe stream exceeds its record/byte limits")
            stream.write(raw)
            digest.update(raw)
    return count, digest.hexdigest()


def source_hashes() -> dict:
    """Bind all backend Python source, including the fixed isolated bootstrap."""
    paths = sorted((PROJECT_ROOT / "commander").rglob("*.py"))
    paths.extend(
        PROJECT_ROOT / name
        for name in (
            "tools/catalog_probe_worker.py",
            "tools/companion_worker.py",
            "tools/printing_worker.py",
        )
    )
    if len(paths) > 256:
        raise ValueError("Catalog probe source registry exceeds its file limit")
    result = {}
    for path in paths:
        with path.open("rb") as stream:
            raw = stream.read(1024 * 1024 + 1)
        if len(raw) > 1024 * 1024:
            raise ValueError("Catalog probe source exceeds its file size limit")
        result[path.relative_to(PROJECT_ROOT).as_posix()] = hashlib.sha256(
            raw
        ).hexdigest()
    return result


def _unique_fields(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate catalog probe field")
        result[key] = value
    return result
