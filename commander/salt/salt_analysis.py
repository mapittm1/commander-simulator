"""Deck salt observations: attributed community opinion, not calibrated aggression."""

from datetime import datetime, timedelta, timezone

from commander.decks.deck_store import get_revision
from commander.rules.legality import require_legal_deck
from commander.salt.salt_data import source_time
from commander.salt.salt_store import ensure_salt_schema, read_salt_snapshot

SALT_FRESHNESS_DAYS = 30


def deck_salt(connection, deck_id: int, version: int, snapshot_id: str) -> dict:
    """Require current deck legality, then match immutable ratings by catalog identity."""
    if any(
        not isinstance(value, int) or isinstance(value, bool) or not 1 <= value < 2**63
        for value in (deck_id, version)
    ):
        raise ValueError(
            "Deck ID and explicit version must be positive 64-bit integers"
        )
    revision = get_revision(connection, deck_id, version)
    ensure_salt_schema(connection)
    with connection:
        connection.execute("BEGIN")
        legality = require_legal_deck(connection, deck_id, version)
        stored = read_salt_snapshot(connection, snapshot_id)
        return _profile(revision, legality, stored)


def _profile(revision, legality, stored) -> dict:
    snapshot = stored["snapshot"]
    ratings = {
        entry["card_id"]: entry
        for entry in snapshot["cards"]
        if entry["card_id"] is not None
    }
    cards = [_card_profile(entry, ratings) for entry in revision["cards"]]
    cards.sort(
        key=lambda entry: (
            entry["rating"] is None,
            -(entry["rating"] or 0),
            entry["name"],
        )
    )
    known_quantity = sum(
        card["quantity"] for card in cards if card["rating"] is not None
    )
    total = sum(card["quantity"] for card in cards)
    hundredths = sum(
        card["quantity"] * ratings[card["card_id"]]["score_hundredths"]
        for card in cards
        if card["card_id"] in ratings
    )
    age = datetime.now(timezone.utc) - source_time(snapshot["observed_at"])
    return {
        "status": "covered" if known_quantity == total else "partial",
        "deck_id": revision["deck_id"],
        "version": revision["version"],
        "revision_id": revision["id"],
        "deck_snapshot_hash": revision["snapshot_hash"],
        "current_catalog": legality["catalog"],
        "rating_resolution_catalog": snapshot["catalog"],
        "salt_snapshot_id": stored["snapshot_id"],
        "salt_snapshot_hash": stored["snapshot_hash"],
        "source": {
            key: snapshot[key]
            for key in (
                "source_url",
                "voting_year",
                "observed_at",
                "scope",
                "scale",
                "verification_basis",
                "unresolved_cards",
            )
        },
        "freshness": (
            "stale"
            if age > timedelta(days=SALT_FRESHNESS_DAYS)
            else "recently_observed"
        ),
        "freshness_policy_days": SALT_FRESHNESS_DAYS,
        "coverage": {
            "rated_quantity": known_quantity,
            "unrated_quantity": total - known_quantity,
            "total_quantity": total,
            "rated_fraction": known_quantity / total,
        },
        "known_rating_subtotal": hundredths / 100,
        "mean_rated_card_rating": (
            hundredths / (100 * known_quantity) if known_quantity else None
        ),
        "aggregation": "Quantity-weighted subtotal and mean over matched ratings only; not an official EDHREC deck score.",
        "cards": cards,
        "aggression_probability": None,
        "limitations": [
            "Salt ratings are community opinions, not measured attack probabilities or causal targeting evidence.",
            "Unrated cards are unknown, not zero-salt. The source sample does not cover every card.",
            "No AI targeting weights or game rules are changed by this advisory report.",
        ],
    }


def _card_profile(entry, ratings) -> dict:
    card_id = entry["card"]["card_id"]
    rating = ratings.get(card_id)
    return {
        "card_id": card_id,
        "name": entry["card"]["name"],
        "zone": entry["zone"],
        "quantity": entry["quantity"],
        "rating": rating["score_hundredths"] / 100 if rating else None,
        "source_rank": rating["rank"] if rating else None,
        "source_name": rating["source_name"] if rating else None,
        "rating_status": "rated_in_sample" if rating else "unrated_in_sample",
    }
