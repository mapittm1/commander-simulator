"""Bounded Scryfall Attraction printing enrichment without replacing Oracle records."""

import hashlib
import json
import math
import time
from datetime import datetime, timezone
from urllib.parse import parse_qs, urlencode, urlparse

from commander.catalog.database import store_printing
from commander.catalog.printing_choices import PRINTING_FIELDS, require_printing_choice
from commander.catalog.scryfall import open_scryfall, validate_scryfall_url
from commander.json_safety import require_json_depth

QUERY = "type:attraction legal:commander"
SOURCE = "https://api.scryfall.com/cards/search?" + urlencode(
    {"q": QUERY, "unique": "prints"}
)
MAX_PAGE_BYTES = 2 * 1024 * 1024
MAX_PAGES = 4


def sync_attraction_printings(connection):
    """Update only matching printings and separately attribute the observed source."""
    cards, digest = _download_printings()
    observed = datetime.now(timezone.utc).isoformat()
    with connection:
        connection.execute("BEGIN IMMEDIATE")
        connection.execute("""CREATE TABLE IF NOT EXISTS printing_syncs (
            id INTEGER PRIMARY KEY, source TEXT NOT NULL, observed_at TEXT NOT NULL,
            records INTEGER NOT NULL, source_sha256 TEXT NOT NULL
        )""")
        for card in cards:
            store_printing(connection, card)
        cursor = connection.execute(
            "INSERT INTO printing_syncs(source,observed_at,records,source_sha256) VALUES (?,?,?,?)",
            (SOURCE, observed, len(cards), digest),
        )
    return {
        "printing_sync_id": cursor.lastrowid,
        "source": SOURCE,
        "observed_at": observed,
        "records": len(cards),
        "oracle_identities": len({card["oracle_id"] for card in cards}),
        "source_sha256": digest,
        "oracle_catalog_freshness_updated": False,
    }


def _download_printings():
    url, cards, seen = SOURCE, [], set()
    digest = hashlib.sha256()
    total = None
    for page in range(MAX_PAGES):
        payload, raw = _read_page(url)
        digest.update(len(raw).to_bytes(8, "big") + raw)
        if total is not None and payload["total_cards"] != total:
            raise ValueError(
                "Attraction printing inventory changed between pages; retry"
            )
        total = payload["total_cards"]
        for card in payload["data"]:
            _validate_source(card)
            if card["id"] in seen:
                raise ValueError("Duplicate source Attraction printing")
            seen.add(card["id"])
            cards.append(card)
        if not payload["has_more"]:
            if len(cards) != total or not cards:
                raise ValueError("Incomplete or empty Attraction printing inventory")
            return cards, digest.hexdigest()
        url = _next_page(payload.get("next_page"))
        if page + 1 < MAX_PAGES:
            time.sleep(0.15)
    raise ValueError(
        "Attraction printing inventory exceeds the four-page process bound"
    )


def _read_page(url):
    with open_scryfall(url, timeout=30) as response:
        raw = response.read(MAX_PAGE_BYTES + 1)
    if len(raw) > MAX_PAGE_BYTES:
        raise ValueError("Attraction printing response exceeds 2 MiB")
    text = raw.decode("utf-8")
    require_json_depth(text, 32)
    payload = json.loads(
        text,
        object_pairs_hook=_unique_fields,
        parse_float=_finite_float,
        parse_constant=_reject_constant,
    )
    if (
        not isinstance(payload, dict)
        or payload.get("object") != "list"
        or not isinstance(payload.get("has_more"), bool)
    ):
        raise ValueError("Malformed Attraction printing page metadata")
    total, data = payload.get("total_cards"), payload.get("data")
    if (
        not isinstance(total, int)
        or isinstance(total, bool)
        or not 1 <= total <= MAX_PAGES * 175
        or not isinstance(data, list)
        or not 1 <= len(data) <= 175
    ):
        raise ValueError("Malformed or excessive Attraction printing inventory")
    return payload, raw


def _validate_source(card):
    if not isinstance(card, dict) or card.get("object") != "card":
        raise ValueError("Expected a source card printing")
    choice = {field: card.get(field) for field in PRINTING_FIELDS}
    require_printing_choice(choice, card.get("oracle_id"), card.get("name"))
    types, legality = card.get("type_line"), card.get("legalities")
    if (
        not isinstance(types, str)
        or "Artifact" not in types.partition(" — ")[0].split()
        or "Attraction" not in types.partition(" — ")[2].split()
        or not isinstance(legality, dict)
        or legality.get("commander") != "legal"
    ):
        raise ValueError("Source printing is not a legal Commander Attraction")


def _next_page(url):
    if not isinstance(url, str) or len(url) > 2048:
        raise ValueError("Invalid Attraction printing pagination URL")
    validate_scryfall_url(url)
    parsed = urlparse(url)
    query = parse_qs(parsed.query)
    if (
        parsed.hostname != "api.scryfall.com"
        or parsed.path != "/cards/search"
        or parsed.fragment
        or query.get("q") != [QUERY]
        or query.get("unique") != ["prints"]
    ):
        raise ValueError("Attraction pagination changed its fixed source query")
    return url


def _finite_float(token):
    if len(token) > 100 or not math.isfinite(value := float(token)):
        raise ValueError("Invalid source numeric value")
    return value


def _reject_constant(token):
    raise ValueError("Non-finite source numeric constant: " + token)


def _unique_fields(pairs):
    fields = {}
    for name, value in pairs:
        if name in fields:
            raise ValueError("Duplicate source card metadata field")
        fields[name] = value
    return fields
