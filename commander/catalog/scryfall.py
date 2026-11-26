"""Download bounded Scryfall bulk exports and import them transactionally."""

import io
import json
import tempfile
from http.client import HTTPException, HTTPResponse
from pathlib import Path
from sqlite3 import Connection
from typing import BinaryIO, Iterator, TextIO
from urllib.parse import urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener

from commander.catalog.bulk_limits import (
    CHUNK_BYTES,
    DEFAULT_LIMITS,
    MAX_JSON_DEPTH,
    BulkDeadline,
    BulkLimits,
    open_bulk_text,
)
from commander.catalog.bulk_records import iter_cards, iter_json_lines
from commander.catalog.database import store_card
from commander.json_safety import require_json_depth

BULK_API = "https://api.scryfall.com/bulk-data"
BULK_KINDS = {"oracle_cards", "default_cards", "all_cards"}
HEADERS = {"User-Agent": "CommanderSimulator/0.1", "Accept": "application/json"}


def validate_scryfall_url(url: str) -> None:
    """Restrict downloads and redirects to the two HTTPS Scryfall data hosts."""
    parsed = urlparse(url)
    if (
        parsed.scheme != "https"
        or parsed.hostname not in {"api.scryfall.com", "data.scryfall.io"}
        or parsed.port not in (None, 443)
        or parsed.username
        or parsed.password
    ):
        raise ValueError("Unexpected Scryfall URL")


class ScryfallRedirectHandler(HTTPRedirectHandler):
    """Validate each redirect before urllib follows it."""

    # urllib requires this callback signature; its argument count is not ours to change.
    # pylint: disable=too-many-arguments,too-many-positional-arguments
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        validate_scryfall_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def open_scryfall(url: str, timeout: int) -> HTTPResponse:
    """Open only an approved Scryfall HTTPS endpoint, including redirect targets."""
    validate_scryfall_url(url)
    opener = build_opener(ScryfallRedirectHandler())
    return opener.open(Request(url, headers=HEADERS), timeout=timeout)


def _response_length(response, maximum: int) -> int | None:
    value = getattr(response, "headers", {}).get("Content-Length")
    if value is None:
        return None
    if (
        not isinstance(value, str)
        or len(value) > 20
        or not value.isascii()
        or not value.isdecimal()
    ):
        raise ValueError("Invalid Scryfall response length")
    length = int(value)
    if length > maximum:
        raise ValueError("Scryfall response exceeds its byte limit")
    return length


def _copy_response(
    response, output: BinaryIO, maximum: int, deadline: BulkDeadline
) -> None:
    expected = _response_length(response, maximum)
    read = getattr(response, "read1", response.read)
    consumed = 0
    while True:
        deadline.check()
        try:
            chunk = read(min(CHUNK_BYTES, maximum - consumed + 1))
        except HTTPException as error:
            raise ValueError("Incomplete Scryfall response") from error
        deadline.check()
        if not chunk:
            break
        consumed += len(chunk)
        if consumed > maximum:
            raise ValueError("Scryfall response exceeds its byte limit")
        output.write(chunk)
    if not consumed:
        raise ValueError("Refusing an empty Scryfall response")
    if expected is not None and expected != consumed:
        raise ValueError("Incomplete Scryfall response")


def _metadata(deadline: BulkDeadline, limits: BulkLimits) -> dict:
    # BytesIO retains at most the small metadata allowance, never the bulk export.
    with open_scryfall(BULK_API, timeout=60) as response, io.BytesIO() as output:
        _copy_response(response, output, limits.metadata_bytes, deadline)
        text = output.getvalue().decode("utf-8")
    require_json_depth(text, MAX_JSON_DEPTH)
    value = json.loads(text)
    if not isinstance(value, dict) or not isinstance(value.get("data"), list):
        raise ValueError("Invalid Scryfall bulk metadata")
    return value


def _select_bulk(metadata: dict, kind: str) -> dict:
    matches = [
        item
        for item in metadata["data"]
        if isinstance(item, dict) and item.get("type") == kind
    ]
    if len(matches) != 1:
        raise ValueError("Scryfall metadata must identify exactly one requested export")
    selected = matches[0]
    url = selected.get("jsonl_download_uri") or selected.get("download_uri")
    if not isinstance(url, str):
        raise ValueError("Missing Scryfall download URL")
    validate_scryfall_url(url)
    if urlparse(url).hostname != "data.scryfall.io":
        raise ValueError("Unexpected Scryfall download URL")
    if not isinstance(selected.get("updated_at"), str) or not selected["updated_at"]:
        raise ValueError("Missing Scryfall export timestamp")
    return selected


def download_bulk(
    destination: str | Path,
    kind: str = "oracle_cards",
    *,
    limits: BulkLimits = DEFAULT_LIMITS,
) -> dict:
    """Bound network work and atomically replace the cache only after a complete transfer."""
    if not isinstance(kind, str) or kind not in BULK_KINDS:
        raise ValueError("Unsupported Scryfall bulk kind")
    deadline = BulkDeadline.after(limits.download_seconds, "download")
    selected = _select_bulk(_metadata(deadline, limits), kind)
    url = selected.get("jsonl_download_uri") or selected["download_uri"]
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    print(f"Downloading {kind}", flush=True)
    try:
        deadline.check()
        with open_scryfall(url, timeout=120) as response:
            with tempfile.NamedTemporaryFile(
                dir=destination.parent,
                prefix=destination.name + ".",
                suffix=".partial",
                delete=False,
            ) as output:
                temporary = Path(output.name)
                _copy_response(response, output, limits.download_bytes, deadline)
        deadline.check()
        temporary.replace(destination)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
    return selected


def _records(stream: TextIO, limits: BulkLimits) -> Iterator[dict]:
    first = stream.readline(min(CHUNK_BYTES, limits.record_characters + 2))
    while first and not first.strip():
        first = stream.readline(min(CHUNK_BYTES, limits.record_characters + 2))
    if first.lstrip().startswith("["):
        yield from iter_cards(stream, limits=limits, initial=first)
    else:
        yield from iter_json_lines(stream, limits=limits, initial=first)


def import_bulk(
    connection: Connection,
    path: str | Path,
    source: str = "local",
    updated_at: str | None = None,
    *,
    limits: BulkLimits = DEFAULT_LIMITS,
) -> int:
    """Roll back every row and freshness receipt on malformed or excessive input."""
    if connection.in_transaction:
        raise ValueError("Finish the active transaction before importing a catalog")
    deadline = BulkDeadline.after(limits.import_seconds, "import")
    count = 0
    allow_token_reassignment = source in BULK_KINDS
    with connection, open_bulk_text(Path(path), limits, deadline) as stream:
        for card in _records(stream, limits):
            deadline.check()
            if count >= limits.records:
                raise ValueError("Bulk import exceeds its record limit")
            store_card(
                connection, card, allow_token_reassignment=allow_token_reassignment
            )
            count += 1
        deadline.check()
        if not count:
            raise ValueError("Refusing to import an empty card catalog")
        connection.execute(
            "INSERT INTO imports(source, source_updated_at, records) VALUES (?, ?, ?)",
            (source, updated_at, count),
        )
    return count
