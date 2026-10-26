"""Evidence-based engineering triage; never automatic approval of unmatched cards."""

from collections import Counter
from sqlite3 import Connection

from card_structure import meld_role
from engine_audit_store import (
    MAX_AUDIT_CARDS,
    ensure_audit_schema,
    iter_gap_cards,
    read_audit,
)
from forge_decks import safe_engine_name

CLASSIFIER_VERSION = 2
PUNCTUATION_REVIEW = str.maketrans({"\ua789": ":", "\u2019": "'"})


def classify_gap(card: dict, index: dict, script_status: str) -> dict:
    """Use explicit card structure and lexical hints; do not infer rules implementations."""
    role = meld_role(card)
    structural = _structural_gap(card, role)
    if structural is not None:
        return structural
    return _implementation_gap(card, index, script_status)


def _structural_gap(card, role) -> dict | None:
    if card.get("type_line") == "Stickers":
        return _category(
            "non_starting_sticker_sheet",
            0,
            "Track supplementary sticker support separately from starting-card scripts.",
        )
    if role == "meld_result":
        return _category(
            "non_starting_meld_result",
            0,
            "Verify through the meld-front interaction; do not export this as a starting card.",
        )
    if role == "unknown":
        return {
            **_category(
                "metadata_review",
                1,
                "Resolve missing/conflicting meld role metadata before any approval.",
            ),
            "metadata_evidence": _meld_evidence(card),
        }
    return None


def _meld_evidence(card) -> dict:
    parts = card.get("all_parts")
    entries = parts if isinstance(parts, list) else []
    return {
        "own_printing_id": card.get("id"),
        "oracle_id": card.get("oracle_id"),
        "exact_self_reference_count": sum(
            isinstance(part, dict) and part.get("id") == card.get("id")
            for part in entries
        ),
        "related_printings": [
            {key: part.get(key) for key in ("id", "component", "name")}
            for part in entries[:10]
            if isinstance(part, dict)
        ],
        "role_approved": False,
        "reason": "An exact self-printing role could not be established; names are not identity proof.",
    }


def _implementation_gap(card, index, script_status) -> dict:
    if script_status == "unsafe_export":
        return _category(
            "serialization_review",
            1,
            "Correct unsafe engine export metadata before execution.",
        )
    candidate = card["name"].translate(PUNCTUATION_REVIEW)
    if (
        candidate != card["name"]
        and candidate in index
        and safe_engine_name(index[candidate])
    ):
        return {
            **_category(
                "name_review",
                1,
                "Review this spelling candidate manually; it remains blocked.",
            ),
            "candidate_engine_name": index[candidate],
            "candidate_is_approved": False,
        }
    oracle = "\n".join(
        [
            card.get("oracle_text") or "",
            *(face.get("oracle_text") or "" for face in card.get("card_faces", [])),
        ]
    ).casefold()
    hints = [term for term in ("sticker", "{tk}", "attraction") if term in oracle]
    if hints:
        return {
            **_category(
                "supplementary_mechanic_hint",
                3,
                "Investigate script availability and supplementary-state behavior; hints are lexical, not proof.",
            ),
            "oracle_terms": hints,
        }
    return _category(
        "script_implementation_review",
        2,
        "Investigate the unmatched starting-card export and add interaction tests.",
    )


def _category(name, priority, action) -> dict:
    return {
        "classifier_version": CLASSIFIER_VERSION,
        "category": name,
        "priority": priority,
        "action": action,
    }


def audit_gap_backlog(
    connection: Connection, audit_id: str, offset: int = 0, limit: int = 100
) -> dict:
    """Page frozen triage evidence; old audits require a new snapshot, not reinterpretation."""
    if (
        not isinstance(offset, int)
        or isinstance(offset, bool)
        or not 0 <= offset <= MAX_AUDIT_CARDS
    ):
        raise ValueError("Invalid gap backlog offset")
    if not isinstance(limit, int) or isinstance(limit, bool) or not 1 <= limit <= 1000:
        raise ValueError("Gap backlog page size must be from 1 through 1000")
    ensure_audit_schema(connection)
    stored = read_audit(connection, audit_id)
    if stored["report"]["auditor_version"] < 2:
        raise ValueError("Audit lacks frozen gap classification; create a fresh audit")
    categories = Counter()
    versions = set()
    actionable = []
    for card in iter_gap_cards(connection, audit_id):
        context = card["gap"]
        versions.add(context["classifier_version"])
        categories[context["category"]] += 1
        if context["priority"]:
            actionable.append(card)
    actionable.sort(
        key=lambda card: (
            card["gap"]["priority"],
            card["name"].casefold(),
            card["card_id"],
        )
    )
    return {
        "audit_id": audit_id,
        "report_hash": stored["report_hash"],
        "classifier_version": next(iter(versions)) if len(versions) == 1 else None,
        "historical_snapshot_only": True,
        "categories": dict(categories),
        "total_gap_records": sum(categories.values()),
        "actionable_records": len(actionable),
        "non_starting_records": sum(categories.values()) - len(actionable),
        "offset": offset,
        "limit": limit,
        "cards": actionable[offset : offset + limit],
        "limitation": "Engineering priorities are not popularity, power ratings, or card-support approval.",
    }
