"""Prevent known rules errors from silently entering otherwise legal simulations."""

import re
from uuid import UUID

from commander.engine.interactions.suite import read_hashed_json
from commander.paths import CONFIG_ROOT

ISSUES_PATH = CONFIG_ROOT / "engine_rules_issues.json"
ISSUE_FIELDS = {
    "issue_id",
    "card_id",
    "card_name",
    "reason",
    "evidence",
    "reviewed_builds",
}
BUILD_FIELDS = {"jar_sha256", "cards_sha256", "evidence", "evidence_sha256"}


def _bounded_text(value, maximum: int) -> bool:
    """Validate trusted policy text before using it in receipts or errors."""
    return isinstance(value, str) and 0 < len(value) <= maximum


def _checksum(value) -> bool:
    """Accept only canonical SHA-256 strings, never fuzzy version labels."""
    return isinstance(value, str) and re.fullmatch(r"[a-f0-9]{64}", value) is not None


def _validate_issue(issue: dict) -> None:
    """Validate one bounded identity-bound issue and its exact build reviews."""
    if not isinstance(issue, dict) or set(issue) != ISSUE_FIELDS:
        raise ValueError("Malformed engine rules issue")
    if not _bounded_text(issue["issue_id"], 64) or not re.fullmatch(
        r"[a-z][a-z0-9_]*", issue["issue_id"]
    ):
        raise ValueError("Invalid engine rules issue identifier")
    try:
        canonical = str(UUID(issue["card_id"]))
    except (ValueError, TypeError, AttributeError) as error:
        raise ValueError("Invalid engine rules card identity") from error
    if canonical != issue["card_id"]:
        raise ValueError("Engine rules card identity must be canonical")
    if any(
        not _bounded_text(issue[key], maximum)
        for key, maximum in (("card_name", 200), ("reason", 1000), ("evidence", 300))
    ):
        raise ValueError("Engine rules issue text must be bounded")
    builds = issue["reviewed_builds"]
    if not isinstance(builds, list) or len(builds) > 16:
        raise ValueError("Invalid engine rules build reviews")
    seen = set()
    for build in builds:
        if (
            not isinstance(build, dict)
            or set(build) != BUILD_FIELDS
            or any(not _checksum(build[key]) for key in BUILD_FIELDS - {"evidence"})
            or not _bounded_text(build["evidence"], 300)
        ):
            raise ValueError("Engine rules reviews require exact checksums")
        pair = (build["jar_sha256"], build["cards_sha256"])
        if pair in seen:
            raise ValueError("Duplicate engine rules build review")
        seen.add(pair)


def _issues() -> tuple[list[dict], str]:
    """Load a fixed local policy, hashing the same bounded bytes that are parsed."""
    if ISSUES_PATH.is_symlink():
        raise ValueError("Engine rules issue policy must not be a symbolic link")
    data, digest = read_hashed_json(ISSUES_PATH, 64 * 1024)
    if (
        set(data) != {"schema_version", "issues"}
        or not isinstance(data["schema_version"], int)
        or isinstance(data["schema_version"], bool)
        or data["schema_version"] != 1
    ):
        raise ValueError("Invalid engine rules issue policy")
    if not isinstance(data["issues"], list) or len(data["issues"]) > 64:
        raise ValueError("Invalid engine rules issue list")
    seen = set()
    for issue in data["issues"]:
        _validate_issue(issue)
        if issue["issue_id"] in seen:
            raise ValueError("Duplicate engine rules issue identifier")
        seen.add(issue["issue_id"])
    return data["issues"], digest


def check_rules_admission(engine: dict, revisions: list[dict]) -> dict | None:
    """Check stable card identities against exact reviewed engine/resource pairs.

    Fingerprints must come from EngineSettings.verify(), not uploaded deck data.
    This clears listed known issues only; it is not universal rules certification.
    """
    issues, digest = _issues()
    identities = {
        entry["card"].get("card_id")
        for revision in revisions
        for entry in revision["cards"]
    }
    reviews = []
    for issue in issues:
        if issue["card_id"] not in identities:
            continue
        matching = next(
            (
                build
                for build in issue["reviewed_builds"]
                if build["jar_sha256"] == engine.get("jar_sha256")
                and build["cards_sha256"] == engine.get("cards_sha256")
            ),
            None,
        )
        if matching is None:
            raise ValueError(
                f"Known engine rules issue ({issue['issue_id']}): "
                f"{issue['card_name']}: {issue['reason']} "
                "This engine/card archive pair has no matching review."
            )
        reviews.append(
            {
                "issue_id": issue["issue_id"],
                "card_id": issue["card_id"],
                "evidence": matching["evidence"],
                "evidence_sha256": matching["evidence_sha256"],
            }
        )
    if not reviews:
        return None
    return {"policy_sha256": digest, "reviewed_issues": reviews}


def verify_rules_admission(
    engine: dict, revisions: list[dict], expected: dict | None
) -> None:
    """Recheck policy immediately before launch, including newly applicable issues."""
    if check_rules_admission(engine, revisions) != expected:
        raise ValueError(
            "Engine rules issue policy changed; prepare the simulation again"
        )
