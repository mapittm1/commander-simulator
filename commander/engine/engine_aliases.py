"""Explicit identity reviews for a pinned script archive; never fuzzy card lookup."""

import hashlib
import json
import re
import zipfile
from pathlib import Path
from uuid import UUID

from commander.engine.forge_config import file_hash
from commander.paths import CONFIG_ROOT

REVIEWS_PATH = CONFIG_ROOT / "engine_alias_reviews.json"


class ReviewedCardIndex(dict):
    """Keep reviewed mappings with the identity guard required to use each alias."""

    def __init__(self, entries: dict):
        super().__init__(entries)
        self.reviews: dict[str, dict] = {}


def apply_alias_reviews(archive_path: Path, entries: dict) -> ReviewedCardIndex:
    """Apply repository-owned exact mappings only to the reviewed archive/script."""
    with REVIEWS_PATH.open("rb") as stream:
        raw = stream.read(65537)
    if len(raw) > 65536:
        raise ValueError("Engine alias review file exceeds 64 KiB")
    manifest = json.loads(raw, object_pairs_hook=_unique_fields)
    if not isinstance(manifest, dict) or manifest.get("schema_version") != 1:
        raise ValueError("Unsupported engine alias review schema")
    reviews = manifest.get("reviews")
    if not isinstance(reviews, list) or len(reviews) > 64:
        raise ValueError("Engine alias reviews must be a list of at most 64 entries")
    index = ReviewedCardIndex(entries)
    archive_hash = file_hash(archive_path)
    seen_names, seen_ids = set(), set()
    with zipfile.ZipFile(archive_path) as archive:
        for review in reviews:
            _validate_review(review)
            name, identity = review["name"], review["oracle_id"]
            if name in seen_names or identity in seen_ids:
                raise ValueError("Duplicate engine alias review identity or name")
            seen_names.add(name)
            seen_ids.add(identity)
            if review["archive_sha256"] != archive_hash:
                continue
            _apply_review(archive, index, review)
    return index


def _apply_review(archive, index, review) -> None:
    name, target = review["name"], review["engine_name"]
    if target not in index or index[target] != target:
        raise ValueError("Reviewed alias target is not an exact engine card")
    if name in index:
        raise ValueError("Reviewed alias conflicts with an existing engine name")
    member = archive.getinfo(review["script_path"])
    if member.file_size > 1024 * 1024:
        raise ValueError("Oversized reviewed engine script")
    digest = hashlib.sha256(archive.read(member)).hexdigest()
    if digest != review["script_sha256"]:
        raise ValueError("Reviewed engine script hash changed")
    index[name] = target
    index.reviews[name] = review


def _unique_fields(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate engine alias review field")
        result[key] = value
    return result


def _validate_review(review) -> None:
    fields = {
        "review_id",
        "name",
        "oracle_id",
        "engine_name",
        "archive_sha256",
        "script_sha256",
        "script_path",
        "sources",
        "scope",
    }
    if not isinstance(review, dict) or set(review) != fields:
        raise ValueError("Invalid engine alias review fields")
    for key in fields - {"sources"}:
        value = review[key]
        if (
            not isinstance(value, str)
            or not 0 < len(value) <= 300
            or not value.isprintable()
        ):
            raise ValueError("Invalid engine alias review value: " + key)
    UUID(review["oracle_id"])
    if any(
        not re.fullmatch(r"[a-f0-9]{64}", review[key])
        for key in ("archive_sha256", "script_sha256")
    ):
        raise ValueError("Invalid engine alias evidence hash")
    if any("|" in review[key] for key in ("name", "engine_name")):
        raise ValueError("Unsafe reviewed engine name")
    sources = review["sources"]
    if (
        not isinstance(sources, list)
        or not 1 <= len(sources) <= 8
        or any(
            not isinstance(url, str)
            or len(url) > 2048
            or not url.startswith("https://")
            or not url.isprintable()
            for url in sources
        )
    ):
        raise ValueError("Invalid engine alias source references")
