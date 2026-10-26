"""Strict, bounded metadata contract for operator-supplied opponent collections."""

import json
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlparse

MAX_MANIFEST_BYTES = 262144
TARGET_OPPONENTS = 10


def positive_integer(value, label: str) -> int:
    """Reject booleans, coercions, and nonpositive counts or identifiers."""
    if not isinstance(value, int) or isinstance(value, bool) or value < 1:
        raise ValueError(f"{label} must be a positive integer")
    return value


def text(value, label: str, limit=500) -> str:
    """Require bounded printable metadata, independent of a future UI."""
    if (
        not isinstance(value, str)
        or not value.strip()
        or len(value) > limit
        or not value.isprintable()
    ):
        raise ValueError(f"Invalid {label}")
    return value.strip()


def source_url(value) -> str:
    """Validate an attribution link; this module never fetches submitted URLs."""
    value = text(value, "source URL", 2000)
    parsed = urlparse(value)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username
        or parsed.password
        or parsed.port not in (None, 443)
    ):
        raise ValueError("Source attribution must be an HTTPS URL without credentials")
    return value


def collection_time(value) -> datetime:
    """Require an aware observation timestamp rather than inventing collection dates."""
    parsed = datetime.fromisoformat(text(value, "collected_at", 100))
    if parsed.tzinfo is None or parsed > datetime.now(timezone.utc) + timedelta(
        minutes=5
    ):
        raise ValueError(
            "collected_at must include a timezone and cannot be in the future"
        )
    return parsed


def validate_manifest(manifest: dict) -> dict:
    """Accept only a source-scoped deck-count sample, never a global-ranking claim."""
    required = {
        "schema_version",
        "bracket_basis",
        "name",
        "bracket",
        "source_name",
        "source_url",
        "collected_at",
        "time_window",
        "sample_size",
        "scope",
        "entries",
    }
    if not isinstance(manifest, dict) or set(manifest) != required:
        raise ValueError("Opponent manifest fields do not match the documented schema")
    if positive_integer(manifest["schema_version"], "schema_version") != 1:
        raise ValueError("Unsupported opponent manifest schema version")
    if manifest["bracket_basis"] not in {"source_user_declared", "source_estimated"}:
        raise ValueError("bracket_basis must identify source declarations or estimates")
    for field in ("name", "source_name", "time_window"):
        text(manifest[field], field)
    source_url(manifest["source_url"])
    collection_time(manifest["collected_at"])
    if positive_integer(manifest["bracket"], "bracket") > 5:
        raise ValueError("Bracket must be between 1 and 5")
    positive_integer(manifest["sample_size"], "sample_size")
    if manifest["scope"] != "source_sample":
        raise ValueError(
            "This importer supports source_sample only; global ranks are unverified"
        )
    entries = manifest["entries"]
    if not isinstance(entries, list) or not 1 <= len(entries) <= TARGET_OPPONENTS:
        raise ValueError(
            "A collection must contain between one and ten representative lists"
        )
    entry_fields = {"deck_id", "version", "observed_decks", "deck_source_url", "author"}
    for entry in entries:
        if not isinstance(entry, dict) or set(entry) != entry_fields:
            raise ValueError("Opponent entry fields do not match the documented schema")
        for field in ("deck_id", "version", "observed_decks"):
            positive_integer(entry[field], field)
        source_url(entry["deck_source_url"])
        text(entry["author"], "deck author", 200)
    if sum(entry["observed_decks"] for entry in entries) > manifest["sample_size"]:
        raise ValueError(
            "Commander counts exceed the declared deduplicated sample size"
        )
    return manifest


def read_manifest(path: Path) -> dict:
    """Load a bounded local JSON manifest without downloading external content."""
    with path.open("rb") as stream:
        raw = stream.read(MAX_MANIFEST_BYTES + 1)
    if len(raw) > MAX_MANIFEST_BYTES:
        raise ValueError("Opponent manifest exceeds 256 KiB")
    return validate_manifest(json.loads(raw.decode("utf-8-sig")))
