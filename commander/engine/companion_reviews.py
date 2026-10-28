"""Exact display-wording reviews; no approximate companion condition matching."""

import re

from commander.engine.interactions.suite import read_hashed_json
from commander.paths import CONFIG_ROOT

REVIEWS_PATH = CONFIG_ROOT / "companion_condition_reviews.json"
FIELDS = {
    "oracle_id",
    "cards_sha256",
    "oracle_condition",
    "native_condition",
    "reason",
    "sources",
}


def condition_review(plan):
    """Return only an identity/archive/Oracle-bound review, including registry hash."""
    data, digest = read_hashed_json(REVIEWS_PATH, 16384)
    reviews = data.get("reviews")
    if (
        not isinstance(data.get("schema_version"), int)
        or isinstance(data.get("schema_version"), bool)
        or data.get("schema_version") != 1
        or not isinstance(reviews, list)
        or len(reviews) > 32
    ):
        raise ValueError("Invalid companion condition review registry")
    selected = None
    seen = set()
    for review in reviews:
        if not isinstance(review, dict) or set(review) != FIELDS:
            raise ValueError("Malformed companion condition review")
        for key in FIELDS - {"sources"}:
            if not isinstance(review[key], str) or not 0 < len(review[key]) <= 2000:
                raise ValueError(
                    "Companion condition review fields must be bounded text"
                )
        if (
            not isinstance(review["sources"], list)
            or not 1 <= len(review["sources"]) <= 4
            or any(
                not isinstance(source, str)
                or not source.startswith("https://")
                or len(source) > 500
                for source in review["sources"]
            )
        ):
            raise ValueError(
                "Companion condition review requires bounded source references"
            )
        if not re.fullmatch(
            r"[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}", review["oracle_id"]
        ) or not re.fullmatch(r"[a-f0-9]{64}", review["cards_sha256"]):
            raise ValueError(
                "Companion review requires exact Oracle identity and archive checksum"
            )
        key = (review["oracle_id"], review["cards_sha256"])
        if key in seen:
            raise ValueError("Duplicate companion condition review identity")
        seen.add(key)
        if (
            key == (plan["companion"]["card_id"], plan["engine"]["cards_sha256"])
            and review["oracle_condition"] == plan["condition_text"]
        ):
            selected = {"registry_sha256": digest, **review}
    return selected


def condition_matches(plan, native):
    """Permit exact wording, or one explicit review of this exact native condition."""
    if native.rsplit(":", 1)[-1].replace("’", "'") == plan["condition_text"].replace(
        "’", "'"
    ):
        return True
    if not isinstance(plan.get("companion", {}).get("card_id"), str) or not isinstance(
        plan.get("engine", {}).get("cards_sha256"), str
    ):
        raise ValueError(
            "Companion wording review lacks exact identity/archive metadata"
        )
    review = condition_review(plan)
    return (
        review is not None
        and review == plan.get("condition_review")
        and review["native_condition"] == native
    )
