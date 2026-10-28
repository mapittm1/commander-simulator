"""Exact source printing links can establish a meld front without approving a deck."""

import json
import re
from uuid import UUID

from commander.json_safety import require_json_depth
from commander.paths import CONFIG_ROOT
from commander.rules.meld_reviews import printing_fingerprint

REVIEWS_PATH = CONFIG_ROOT / "meld_part_reviews.json"
PROJECTION = {"id", "oracle_id", "name", "layout", "all_parts"}


def meld_part_review(card):
    """Return only an exact reviewed printing graph; changed metadata stays unverified."""
    if card.get("layout") != "meld":
        return None
    fingerprint = printing_fingerprint(card)
    reviews = _read_reviews()
    return next(
        (review for review in reviews if review["card_sha256"] == fingerprint), None
    )


def _read_reviews():
    with REVIEWS_PATH.open("rb") as stream:
        raw = stream.read(65537)
    if len(raw) > 65536:
        raise ValueError("Meld front proofs exceed 64 KiB")
    text = raw.decode("utf-8-sig")
    require_json_depth(text, 16)
    manifest = json.loads(text, object_pairs_hook=_distinct)
    if (
        not isinstance(manifest, dict)
        or set(manifest) != {"schema_version", "reviews"}
        or manifest["schema_version"] != 1
    ):
        raise ValueError("Unsupported meld front proof manifest")
    reviews = manifest["reviews"]
    if not isinstance(reviews, list) or len(reviews) > 32:
        raise ValueError("Meld front proofs require at most 32 entries")
    seen = set()
    for review in reviews:
        _validate(review)
        if review["card_sha256"] in seen:
            raise ValueError("Duplicate meld front proof")
        seen.add(review["card_sha256"])
    return reviews


def _validate(review):
    fields = {
        "review_id",
        "card_sha256",
        "current_printing",
        "related_printing",
        "sources",
    }
    if not isinstance(review, dict) or set(review) != fields:
        raise ValueError("Malformed meld front proof")
    if not isinstance(review["review_id"], str) or not re.fullmatch(
        r"[a-z0-9-]{1,100}", review["review_id"]
    ):
        raise ValueError("Invalid meld front review identifier")
    current, related = review["current_printing"], review["related_printing"]
    for printing in (current, related):
        _validate_printing(printing)
    if review["card_sha256"] != printing_fingerprint(current):
        raise ValueError("Meld front proof fingerprint changed")
    if (
        any(
            current[key] != related[key]
            for key in ("oracle_id", "name", "layout", "all_parts")
        )
        or current["id"] == related["id"]
    ):
        raise ValueError(
            "Meld front printings do not share exact identity and relationships"
        )
    parts = related["all_parts"]
    self_roles = [part["component"] for part in parts if part["id"] == related["id"]]
    if self_roles != ["meld_part"]:
        raise ValueError("Related printing must identify itself as a meld front")
    if any(part["id"] == current["id"] for part in parts):
        raise ValueError("Meld front proof must resolve a missing self-printing link")
    if review["sources"] != [
        "https://api.scryfall.com/cards/" + printing["id"]
        for printing in (current, related)
    ]:
        raise ValueError("Meld front proof source identities differ")


def _validate_printing(printing):
    if (
        not isinstance(printing, dict)
        or set(printing) != PROJECTION
        or printing["layout"] != "meld"
    ):
        raise ValueError("Malformed meld printing projection")
    for key in ("id", "oracle_id"):
        if not isinstance(printing[key], str):
            raise ValueError("Meld printing identities must be UUID strings")
        UUID(printing[key])
    if (
        not isinstance(printing["name"], str)
        or not 1 <= len(printing["name"]) <= 300
        or not printing["name"].isprintable()
    ):
        raise ValueError("Invalid meld printing name")
    parts = printing["all_parts"]
    if (
        not isinstance(parts, list)
        or len(parts) != 3
        or any(
            not isinstance(part, dict) or not {"id", "name", "component"} <= set(part)
            for part in parts
        )
    ):
        raise ValueError("Meld front proof must contain three related identities")
    for part in parts:
        _validate_part(part)
    if (
        sorted(part["component"] for part in parts)
        != ["meld_part", "meld_part", "meld_result"]
        or len({part["id"] for part in parts}) != 3
    ):
        raise ValueError("Invalid meld relationship graph")


def _validate_part(part):
    if not isinstance(part["id"], str):
        raise ValueError("Related printing identity must be a UUID string")
    UUID(part["id"])
    if (
        not isinstance(part["name"], str)
        or not 1 <= len(part["name"]) <= 300
        or not part["name"].isprintable()
    ):
        raise ValueError("Invalid related printing name")
    if part["component"] not in ("meld_part", "meld_result"):
        raise ValueError("Invalid related printing component")


def _distinct(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate meld front proof field")
        result[key] = value
    return result
