"""Bracket screening and human attestation, independent of format legality."""

import hashlib
import json
from pathlib import Path
from sqlite3 import Connection

from database import get_card_details
from deck_store import get_revision
from legality import require_legal_deck

POLICY_PATH = Path(__file__).resolve().with_name("bracket_policy.json")
MAX_REVIEW_BYTES = 32768

SCHEMA = """
    CREATE TABLE IF NOT EXISTS bracket_reviews (
        id INTEGER PRIMARY KEY,
        revision_id INTEGER NOT NULL REFERENCES deck_revisions(id),
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        report_json TEXT NOT NULL
    );
    CREATE TRIGGER IF NOT EXISTS immutable_bracket_review_update
    BEFORE UPDATE ON bracket_reviews BEGIN
        SELECT RAISE(ABORT, 'Bracket reviews are immutable');
    END;
    CREATE TRIGGER IF NOT EXISTS immutable_bracket_review_delete
    BEFORE DELETE ON bracket_reviews BEGIN
        SELECT RAISE(ABORT, 'Bracket reviews are immutable');
    END;
"""


def load_policy() -> dict:
    """Read versioned policy data and fingerprint its entire contents."""
    raw = POLICY_PATH.read_bytes()
    policy = json.loads(raw)
    canonical = json.dumps(policy, sort_keys=True, separators=(",", ":")).encode(
        "utf-8"
    )
    policy["hash"] = hashlib.sha256(canonical).hexdigest()
    return policy


def read_review(path: Path) -> dict:
    """Read a bounded local JSON attestation; never fetch or execute it."""
    with path.open("rb") as stream:
        raw = stream.read(MAX_REVIEW_BYTES + 1)
    if len(raw) > MAX_REVIEW_BYTES:
        raise ValueError("Bracket review file exceeds 32 KiB")
    review = json.loads(raw.decode("utf-8-sig"))
    if not isinstance(review, dict):
        raise ValueError("Bracket review must be a JSON object")
    return review


def _review_blockers(review: dict | None, policy: dict) -> list[str]:
    if review is None:
        return [
            "Human bracket review is required; passing Game Changer caps is not certification"
        ]
    if not isinstance(review, dict) or set(review) != {
        "reviewer",
        "rationale",
        "checks",
    }:
        raise ValueError("Review must contain exactly reviewer, rationale, and checks")
    for field, limit in (("reviewer", 100), ("rationale", 2000)):
        value = review[field]
        if (
            not isinstance(value, str)
            or not value.strip()
            or len(value) > limit
            or any(ord(char) < 32 for char in value)
        ):
            raise ValueError(
                f"Invalid review {field}: expected 1–{limit} printable characters"
            )
    checks = review["checks"]
    if not isinstance(checks, dict) or set(checks) != set(policy["review_checks"]):
        raise ValueError("Review checks must match the policy checklist exactly")
    if any(not isinstance(value, bool) for value in checks.values()):
        raise ValueError("Each review check must be a JSON boolean")
    return [
        policy["review_checks"][name]
        for name, accepted in checks.items()
        if not accepted
    ]


def _game_changers(connection: Connection, entries: list) -> tuple[list, list]:
    found = []
    missing = []
    for entry in entries:
        card_id = entry["card"]["card_id"]
        card = get_card_details(connection, card_id)
        flag = card.get("game_changer")
        if not isinstance(flag, bool):
            missing.append(card["name"])
        elif flag:
            found.append(
                {
                    "card_id": card_id,
                    "name": card["name"],
                    "quantity": entry["quantity"],
                    "zone": entry["zone"],
                }
            )
    return found, missing


def _screen_revision(connection: Connection, revision: dict, review=None) -> dict:
    """Require legality first, then distinguish count conflicts from human review."""
    legality = require_legal_deck(connection, revision["deck_id"], revision["version"])
    policy = load_policy()
    blockers = _review_blockers(review, policy)
    found, missing = _game_changers(connection, revision["cards"])
    count = sum(card["quantity"] for card in found)
    cap = policy["game_changer_caps"][str(revision["declared_bracket"])]
    conflicts = []
    if cap is not None and count > cap:
        conflicts.append(f"Declared bracket allows {cap} Game Changers; found {count}")
    if missing:
        blockers.append("Missing Game Changer flags: " + ", ".join(missing))
    return {
        "status": (
            "conflict" if conflicts else "needs_review" if blockers else "reviewed"
        ),
        "basis": (
            "automated_count_and_human_attestation"
            if review
            else "automated_count_only"
        ),
        "deck_id": revision["deck_id"],
        "revision_id": revision["id"],
        "version": revision["version"],
        "snapshot_hash": revision["snapshot_hash"],
        "declared_bracket": revision["declared_bracket"],
        "policy_version": policy["version"],
        "policy_hash": policy["hash"],
        "sources": policy["sources"],
        "catalog": legality["catalog"],
        "validator_version": legality["validator_version"],
        "rules_version": legality["rules_version"],
        "game_changers": found,
        "game_changer_count": count,
        "game_changer_cap": cap,
        "conflicts": conflicts,
        "blockers": blockers,
        "review": review,
        "checked_at": legality["checked_at"],
        "checklist": policy["review_checks"],
        "limitation": "Human attestation is not an automated proof of power level or bracket fit.",
    }


def check_bracket(connection: Connection, deck_id: int, version=None) -> dict:
    """Inspect automatic findings without saving or approving a bracket review."""
    return _screen_revision(connection, get_revision(connection, deck_id, version))


def _ensure_schema(connection: Connection) -> None:
    if connection.in_transaction:
        raise ValueError(
            "Finish the active transaction before initializing bracket reviews"
        )
    connection.executescript(SCHEMA)


def save_bracket_review(
    connection: Connection, deck_id: int, review: dict, version=None
) -> int:
    """Persist an immutable human assessment after legality and count checks pass."""
    revision = get_revision(connection, deck_id, version)
    _ensure_schema(connection)
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        report = _screen_revision(connection, revision, review)
        if report["status"] != "reviewed":
            raise ValueError(
                "Bracket review blocked: "
                + "; ".join(report["conflicts"] + report["blockers"])
            )
        cursor = connection.execute(
            "INSERT INTO bracket_reviews(revision_id, report_json) VALUES (?, ?)",
            (revision["id"], json.dumps(report)),
        )
    return cursor.lastrowid


def review_history(connection: Connection, deck_id: int, version=None) -> list[dict]:
    """List recorded attestations for exactly the selected revision."""
    revision = get_revision(connection, deck_id, version)
    _ensure_schema(connection)
    return [
        {
            "id": row["id"],
            "created_at": row["created_at"],
            "report": json.loads(row["report_json"]),
        }
        for row in connection.execute(
            "SELECT * FROM bracket_reviews WHERE revision_id = ? ORDER BY id",
            (revision["id"],),
        )
    ]


def require_bracket_review(connection: Connection, deck_id: int, version=None) -> dict:
    """Recheck legality and invalidate reviews after revision, policy, or catalog changes."""
    revision = get_revision(connection, deck_id, version)
    _ensure_schema(connection)
    history = review_history(connection, deck_id, revision["version"])
    if not history:
        raise ValueError("No bracket review exists for this revision")
    previous = history[-1]
    with connection:
        connection.execute("BEGIN")
        report = _screen_revision(connection, revision, previous["report"]["review"])
    same_policy = previous["report"]["policy_hash"] == report["policy_hash"]
    same_catalog = previous["report"]["catalog"] == report["catalog"]
    if report["status"] != "reviewed" or not same_policy or not same_catalog:
        raise ValueError(
            "Bracket review is stale or conflicts with current data; review again"
        )
    return {**report, "review_id": previous["id"]}
