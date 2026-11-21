"""Exact display-wording reviews; no approximate companion condition matching."""

import hashlib
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


def _load_review_registry(archive_hash: str) -> tuple[dict | None, str, bool]:
    """Read only the selected archive's bounded reviews, or a legacy registry."""
    if not isinstance(archive_hash, str) or not re.fullmatch(
        r"[a-f0-9]{64}", archive_hash
    ):
        raise ValueError("Companion review requires an exact archive checksum")
    if REVIEWS_PATH.is_symlink():
        raise ValueError("Companion review policy must not be a symbolic link")
    data, digest = read_hashed_json(REVIEWS_PATH, 32 * 1024)
    if not isinstance(data, dict):
        raise ValueError("Invalid companion condition review registry")
    if (
        isinstance(data.get("schema_version"), int)
        and not isinstance(data.get("schema_version"), bool)
        and data["schema_version"] == 2
    ):
        if data != {"schema_version": 2, "layout": "per_archive"}:
            raise ValueError("Invalid companion review archive policy")
        directory = REVIEWS_PATH.with_suffix("")
        path = directory / f"{archive_hash}.json"
        if (
            directory.is_symlink()
            or path.is_symlink()
            or directory.resolve().parent != REVIEWS_PATH.parent.resolve()
            or path.resolve().parent != directory.resolve()
        ):
            raise ValueError("Companion review archive path is redirected")
        if not path.exists():
            return None, digest, True
        data, archive_digest = read_hashed_json(path, 32 * 1024)
        digest = hashlib.sha256(
            f"{digest}:{archive_digest}".encode("ascii")
        ).hexdigest()
        return data, digest, True
    return data, digest, False


def condition_review(plan):
    """Return only an identity/archive/Oracle-bound review, including registry hash."""
    data, digest, archive_scoped = _load_review_registry(plan["engine"]["cards_sha256"])
    if data is None:
        return None
    if not isinstance(data, dict):
        raise ValueError("Invalid companion condition review registry")
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
        if archive_scoped and review["cards_sha256"] != plan["engine"]["cards_sha256"]:
            raise ValueError("Companion review belongs to a different engine archive")
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
