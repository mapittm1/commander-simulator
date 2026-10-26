"""Bounded, attributed EDHREC salt sample imports; ratings are community opinions."""

import json
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path

SALT_SOURCE = "https://edhrec.com/top/salt"
MAX_SALT_BYTES = 1024 * 1024
MAX_SALT_CARDS = 100
MAX_SALT_DEPTH = 16


def _reject_constant(value):
    raise ValueError("Non-finite JSON number: " + value)


def _unique_object(pairs) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate salt JSON field")
        result[key] = value
    return result


def read_salt_manifest(path: Path) -> dict:
    """Read bounded local data only; no URLs, scripts, or credentials are executed."""
    with path.open("rb") as stream:
        raw = stream.read(MAX_SALT_BYTES + 1)
    if len(raw) > MAX_SALT_BYTES:
        raise ValueError("Salt manifest exceeds 1 MiB")
    text = raw.decode("utf-8-sig")
    _check_nesting(text)
    try:
        return json.loads(
            text,
            parse_float=_json_decimal,
            parse_int=_json_integer,
            parse_constant=_reject_constant,
            object_pairs_hook=_unique_object,
        )
    except RecursionError as error:
        raise ValueError("Salt JSON nesting exceeds supported limits") from error


def _json_decimal(value: str) -> Decimal:
    if len(value) > 64:
        raise ValueError("Salt JSON number exceeds supported precision")
    try:
        return Decimal(value)
    except InvalidOperation as error:
        raise ValueError("Salt JSON number exceeds supported precision") from error


def _json_integer(value: str) -> int:
    if len(value) > 10:
        raise ValueError("Salt JSON integer exceeds supported length")
    return int(value)


def _check_nesting(text: str) -> None:
    """Bound JSON structure before decoding, ignoring brackets inside escaped strings."""
    depth, quoted, escaped = 0, False, False
    for character in text:
        if quoted:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                quoted = False
        elif character == '"':
            quoted = True
        elif character in "[{":
            depth += 1
            if depth > MAX_SALT_DEPTH:
                raise ValueError("Salt JSON nesting exceeds supported limits")
        elif character in "]}":
            depth -= 1


def source_time(value: str) -> datetime:
    """Require an explicit timezone and bounded timestamp text."""
    if not isinstance(value, str) or len(value) > 64:
        raise ValueError("Invalid salt observation time")
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    observed = datetime.fromisoformat(normalized)
    if observed.tzinfo is None:
        raise ValueError("Salt observation time requires a timezone")
    return observed.astimezone(timezone.utc)


def score_hundredths(value) -> int:
    """Keep published 0–4 ratings as exact integer hundredths, excluding booleans."""
    if isinstance(value, bool) or not isinstance(value, (int, float, Decimal)):
        raise ValueError("Salt rating must be a numeric value from 0 through 4")
    number = Decimal(str(value))
    if not number.is_finite() or not 0 <= number <= 4:
        raise ValueError("Salt rating must be finite and within 0 through 4")
    try:
        if number.quantize(Decimal("0.01")) != number:
            raise ValueError("Salt ratings support at most two decimal places")
    except InvalidOperation as error:
        raise ValueError("Invalid salt rating precision") from error
    return int(number * 100)


def validate_salt_manifest(data: dict, now=None) -> dict:
    """Validate source attribution and an explicit sample; never claim all-card coverage."""
    now = now or datetime.now(timezone.utc)
    required = {"schema_version", "source_url", "voting_year", "observed_at", "cards"}
    if not isinstance(data, dict) or set(data) != required:
        raise ValueError("Salt manifest fields do not match schema version 1")
    if (
        not isinstance(data["schema_version"], int)
        or isinstance(data["schema_version"], bool)
        or data["schema_version"] != 1
    ):
        raise ValueError("Unsupported salt manifest schema")
    if data["source_url"] != SALT_SOURCE:
        raise ValueError("Salt data must be attributed to the configured EDHREC page")
    year = data["voting_year"]
    if (
        not isinstance(year, int)
        or isinstance(year, bool)
        or not 2019 <= year <= now.year
    ):
        raise ValueError("Invalid salt voting year")
    observed = source_time(data["observed_at"])
    if observed > now + timedelta(minutes=5) or year > observed.year:
        raise ValueError("Salt source evidence has an inconsistent or future date")
    cards = data["cards"]
    if not isinstance(cards, list) or not 1 <= len(cards) <= MAX_SALT_CARDS:
        raise ValueError("Salt samples require 1 through 100 card entries")
    normalized = [_entry(entry) for entry in cards]
    if len({entry["rank"] for entry in normalized}) != len(normalized):
        raise ValueError("Duplicate salt rank")
    if len({entry["source_name"].casefold() for entry in normalized}) != len(
        normalized
    ):
        raise ValueError("Duplicate salt card name")
    normalized.sort(key=lambda entry: entry["rank"])
    if any(
        left["score_hundredths"] < right["score_hundredths"]
        for left, right in zip(normalized, normalized[1:])
    ):
        raise ValueError("Salt ratings must be non-increasing in source rank order")
    return {
        "schema_version": 1,
        "source_url": SALT_SOURCE,
        "voting_year": year,
        "observed_at": observed.isoformat(),
        "scope": "top_salt_page_sample",
        "scale": {"minimum": 0, "maximum": 4},
        "cards": normalized,
    }


def _entry(entry: dict) -> dict:
    if not isinstance(entry, dict) or set(entry) != {"name", "rank", "score"}:
        raise ValueError("Salt card entries require exactly name, rank, and score")
    name, rank = entry["name"], entry["rank"]
    if not isinstance(name, str) or not 1 <= len(name) <= 300 or not name.isprintable():
        raise ValueError("Invalid salt card name")
    if not isinstance(rank, int) or isinstance(rank, bool) or not 1 <= rank <= 100:
        raise ValueError("Salt sample ranks must be integers from 1 through 100")
    return {
        "source_name": name,
        "rank": rank,
        "score_hundredths": score_hundredths(entry["score"]),
    }
