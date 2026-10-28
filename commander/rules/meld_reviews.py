"""Deny-only starting-card reviews backed by exact Scryfall printing identity links."""

import hashlib
import json
import re
from uuid import UUID

from commander.json_safety import require_json_depth
from commander.paths import CONFIG_ROOT

REVIEWS_PATH = CONFIG_ROOT / "meld_result_reviews.json"


def printing_fingerprint(card: dict) -> str:
    """Bind identities and complete relationship metadata, rather than a similar name."""
    projection = {
        key: card.get(key) for key in ("id", "oracle_id", "name", "layout", "all_parts")
    }
    raw = json.dumps(
        projection, sort_keys=True, separators=(",", ":"), ensure_ascii=True
    )
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def reviewed_meld_result(card: dict) -> str:
    """Only exclude an exactly reviewed meld result; never approve a starting meld part."""
    return "meld_result" if meld_result_review(card) is not None else "unknown"


def meld_result_review(card: dict) -> dict | None:
    """Expose the exact accepted printing proof for immutable audit evidence."""
    for review in _reviews():
        if printing_fingerprint(card) != review["card_sha256"]:
            continue
        related = review["related_printing"]
        if (
            related["oracle_id"] != card.get("oracle_id")
            or related["layout"] != "meld"
            or related["name"] != card.get("name")
        ):
            raise ValueError("Reviewed meld printing identity conflict")
        references = card.get("all_parts", [])
        if not any(
            isinstance(part, dict)
            and part.get("id") == related["id"]
            and part.get("component") == "meld_result"
            for part in references
        ):
            raise ValueError("Reviewed meld result relationship is missing")
        return review
    return None


def _reviews():
    with REVIEWS_PATH.open("rb") as stream:
        raw = stream.read(65537)
    if len(raw) > 65536:
        raise ValueError("Meld result reviews exceed 64 KiB")
    text = raw.decode("utf-8-sig")
    require_json_depth(text, 16)
    manifest = json.loads(text, object_pairs_hook=_unique_fields)
    if (
        not isinstance(manifest, dict)
        or set(manifest) != {"schema_version", "reviews"}
        or manifest["schema_version"] != 1
    ):
        raise ValueError("Unknown meld result review manifest")
    reviews = manifest["reviews"]
    if not isinstance(reviews, list) or len(reviews) > 32:
        raise ValueError("Meld result reviews require at most 32 entries")
    seen = set()
    for review in reviews:
        _validate(review)
        if review["card_sha256"] in seen:
            raise ValueError("Duplicate meld result review")
        seen.add(review["card_sha256"])
    return reviews


def _validate(review):
    if (
        not isinstance(review, dict)
        or set(review)
        != {
            "review_id",
            "role",
            "card_sha256",
            "related_sha256",
            "related_printing",
            "sources",
        }
        or review["role"] != "meld_result"
    ):
        raise ValueError(
            "Meld reviews may only exclude results, never approve starting parts"
        )
    if not isinstance(review["review_id"], str) or not re.fullmatch(
        r"[a-z0-9-]{1,100}", review["review_id"]
    ):
        raise ValueError("Invalid meld review identifier")
    for key in ("card_sha256", "related_sha256"):
        if not isinstance(review[key], str) or not re.fullmatch(
            r"[a-f0-9]{64}", review[key]
        ):
            raise ValueError("Meld reviews require exact printing fingerprints")
    related = review["related_printing"]
    if not isinstance(related, dict) or set(related) != {
        "id",
        "oracle_id",
        "name",
        "layout",
        "all_parts",
    }:
        raise ValueError("Malformed related meld printing proof")
    for key in ("id", "oracle_id"):
        if not isinstance(related[key], str):
            raise ValueError("Meld proof requires printing and Oracle UUID strings")
        UUID(related[key])
    if printing_fingerprint(related) != review["related_sha256"]:
        raise ValueError("Related meld printing proof fingerprint changed")
    parts = related["all_parts"]
    if not isinstance(parts, list) or len(parts) != 3:
        raise ValueError("Meld proof must identify the three related objects")
    roles = [
        part.get("component")
        for part in parts
        if isinstance(part, dict) and part.get("id") == related["id"]
    ]
    if roles != ["meld_result"]:
        raise ValueError("Related printing does not identify itself as a meld result")
    sources = review["sources"]
    if (
        not isinstance(sources, list)
        or not 1 <= len(sources) <= 4
        or any(
            not isinstance(url, str)
            or not url.startswith("https://api.scryfall.com/cards/")
            or not url.isprintable()
            or len(url) > 300
            for url in sources
        )
    ):
        raise ValueError(
            "Meld result reviews require bounded Scryfall source references"
        )


def _unique_fields(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate meld review field")
        result[key] = value
    return result
