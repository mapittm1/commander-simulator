"""Download Scryfall bulk exports and import them transactionally."""

import gzip
import json
import shutil
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener

from database import store_card

BULK_API = "https://api.scryfall.com/bulk-data"
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


def open_scryfall(url: str, timeout: int):
    """Open only an approved Scryfall HTTPS endpoint, including redirect targets."""
    validate_scryfall_url(url)
    opener = build_opener(ScryfallRedirectHandler())
    return opener.open(Request(url, headers=HEADERS), timeout=timeout)


def iter_cards(stream):
    """Read a JSON array incrementally, without loading the whole export in RAM."""
    decoder = json.JSONDecoder()
    buffer = ""
    eof = False

    def refill():
        nonlocal buffer, eof
        chunk = stream.read(65536)
        buffer += chunk
        eof = not chunk

    def whitespace():
        nonlocal buffer
        while True:
            buffer = buffer.lstrip()
            if buffer or eof:
                return
            refill()

    whitespace()
    if not buffer.startswith("["):
        raise ValueError("Expected a JSON array")
    buffer = buffer[1:]
    whitespace()
    if not buffer.startswith("]"):
        while True:
            while True:
                try:
                    value, end = decoder.raw_decode(buffer)
                    break
                except json.JSONDecodeError:
                    if eof:
                        raise
                    refill()
            if not isinstance(value, dict):
                raise ValueError("Each bulk record must be a card object")
            yield value
            buffer = buffer[end:]
            whitespace()
            if buffer.startswith("]"):
                break
            if not buffer.startswith(","):
                raise ValueError("Expected a comma between card records")
            buffer = buffer[1:]
            whitespace()
    if not buffer.startswith("]"):
        raise ValueError("Unterminated JSON array")
    buffer = buffer[1:]
    whitespace()
    if buffer:
        raise ValueError("Unexpected content after JSON array")


def download_bulk(destination, kind="oracle_cards"):
    """Oracle export covers unique cards; all_cards includes all printings/languages."""
    with open_scryfall(BULK_API, timeout=60) as response:
        metadata = json.load(response)
    selected = next(item for item in metadata["data"] if item["type"] == kind)
    url = selected.get("jsonl_download_uri") or selected["download_uri"]
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname != "data.scryfall.io":
        raise ValueError("Unexpected Scryfall download URL")
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(".partial")
    size = selected.get("compressed_size", selected.get("size", 0))
    print(f"Downloading {kind}: {size / 1024**2:.1f} MB", flush=True)
    try:
        with open_scryfall(url, timeout=120) as response:
            with temporary.open("wb") as output:
                shutil.copyfileobj(response, output)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return selected


def import_bulk(connection, path, source="local", updated_at=None):
    """Roll back the entire import on invalid input; repeat imports update records."""
    count = 0
    with connection:
        path = Path(path)
        with path.open("rb") as raw:
            compressed = raw.read(2) == b"\x1f\x8b"
        opener = gzip.open if compressed else open
        with opener(path, "rt", encoding="utf-8-sig") as stream:
            first = stream.read(1)
            while first and first.isspace():
                first = stream.read(1)
            stream.seek(0)
            records = iter_cards(stream) if first == "[" else iter_json_lines(stream)
            for card in records:
                store_card(connection, card)
                count += 1
        if not count:
            raise ValueError("Refusing to import an empty card catalog")
        connection.execute(
            "INSERT INTO imports(source, source_updated_at, records) VALUES (?, ?, ?)",
            (source, updated_at, count),
        )
    return count


def iter_json_lines(stream):
    """Parse current Scryfall JSONL exports one card per line."""
    for line in stream:
        if line.strip():
            value = json.loads(line)
            if not isinstance(value, dict):
                raise ValueError("Each JSON Lines record must be a card object")
            yield value
