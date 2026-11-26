"""Bound individual card records while retaining JSON-array and JSONL support."""

import json
from typing import Iterator, TextIO

from commander.catalog.bulk_limits import (
    CHUNK_BYTES,
    DEFAULT_LIMITS,
    MAX_JSON_DEPTH,
    BulkLimits,
)
from commander.json_safety import JsonDepthGuard, require_json_depth


def iter_cards(
    stream: TextIO, *, limits: BulkLimits = DEFAULT_LIMITS, initial: str = ""
) -> Iterator[dict]:
    """Parse an array without retaining the whole export or an unbounded record."""
    decoder = json.JSONDecoder()
    guard = JsonDepthGuard(MAX_JSON_DEPTH + 1)
    buffer = initial
    guard.feed(initial)
    eof = False

    def refill():
        nonlocal buffer, eof
        allowance = limits.record_characters + 1 - len(buffer)
        if allowance <= 0:
            raise ValueError("Bulk record exceeds its character limit")
        chunk = stream.read(min(CHUNK_BYTES, allowance))
        guard.feed(chunk)
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
            if end > limits.record_characters:
                raise ValueError("Bulk record exceeds its character limit")
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


def iter_json_lines(
    stream: TextIO, *, limits: BulkLimits = DEFAULT_LIMITS, initial: str = ""
) -> Iterator[dict]:
    """Bound physical lines before allocating or parsing a JSONL record."""
    line = initial or stream.readline(limits.record_characters + 2)
    while line:
        if not line.endswith("\n") and len(line) < limits.record_characters + 2:
            line += stream.readline(limits.record_characters + 2 - len(line))
        record = line.removesuffix("\n").removesuffix("\r")
        if len(record) > limits.record_characters:
            raise ValueError("Bulk record exceeds its character limit")
        if record.strip():
            require_json_depth(record, MAX_JSON_DEPTH)
            value = json.loads(record)
            if not isinstance(value, dict):
                raise ValueError("Each JSON Lines record must be a card object")
            yield value
        line = stream.readline(limits.record_characters + 2)
