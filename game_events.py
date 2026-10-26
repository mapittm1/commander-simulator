"""Bounded parsing of observed events in the pinned Forge text-log dialect."""

import re
from collections import Counter

from engine_process import MAX_LOG_BYTES

PARSER_VERSION = 1
MAX_EVENTS = 10000
MAX_EVENT_LINE = 4096
MAX_ISSUE_SAMPLES = 20
PLAYER = r"Ai\((?P<seat>[1-4])\)-seat-(?P=seat)"
PATTERNS = {
    "turn": re.compile(rf"Turn: Turn (?P<turn>[1-9][0-9]{{0,5}}) \({PLAYER}\)"),
    "phase": re.compile(
        rf"Phase: {PLAYER}'s (?P<phase>Main phase, precombat|Main phase, postcombat|Cleanup step)"
    ),
    "land": re.compile(
        rf"Land: {PLAYER} played (?P<card>.{{1,300}}) \((?P<object_id>[0-9]{{1,10}})\)"
    ),
    "cast": re.compile(rf"Add To Stack: {PLAYER} cast (?P<cast_text>.{{1,3500}})"),
    "attack": re.compile(
        rf"(?:Combat: )?{PLAYER} assigned (?P<attackers>.{{1,3000}}) to attack Ai\((?P<target>[1-4])\)-seat-(?P=target)\."
    ),
}


def _candidate(line: str) -> str | None:
    if line.startswith("Turn:"):
        return "turn"
    if line.startswith("Land:"):
        return "land"
    if line.startswith("Phase:") and any(
        word in line for word in ("Main phase", "Cleanup")
    ):
        return "phase"
    if line.startswith("Add To Stack:") and " cast " in line:
        return "cast"
    if line.startswith(("Combat:", "Ai(")) and " to attack " in line:
        return "attack"
    return None


def _decode(kind: str, line: str, number: int) -> dict | None:
    match = PATTERNS[kind].fullmatch(line)
    if match is None:
        return None
    fields = match.groupdict()
    if any(value is not None and not value.isprintable() for value in fields.values()):
        return None
    if kind == "attack" and fields["seat"] == fields["target"]:
        return None
    if kind == "cast":
        card, marker, target = fields.pop("cast_text").partition(" targeting ")
        if not card or len(card) > 300 or (marker and not target) or len(target) > 3000:
            return None
        fields["card"] = card
        if marker:
            fields["targets_text"] = target
    event = {"kind": kind, "line": number}
    for key, value in fields.items():
        if value is None:
            continue
        event[key] = (
            int(value) if key in {"seat", "turn", "object_id", "target"} else value
        )
    if kind == "attack":
        attackers = _attackers(fields["attackers"])
        if attackers is None:
            return None
        event["attackers"] = attackers
    return event


def _attackers(text: str) -> list | None:
    """Split Forge's homogeneous card list at object IDs, preserving commas in names."""
    cards = []
    while text and len(cards) < 100:
        match = re.match(r"(.{1,300}?) \(([0-9]{1,10})\)", text)
        if match is None:
            return None
        cards.append({"card": match[1], "object_id": int(match[2])})
        text = text[match.end() :]
        if not text:
            return cards
        separator = next(
            (item for item in (", and ", " and ", ", ") if text.startswith(item)), None
        )
        if separator is None:
            return None
        text = text[len(separator) :]
        if not text:
            return None
    return None


def extract_events(text: str) -> dict:
    """Parse only bounded supported events; report malformed candidates and truncation."""
    if not isinstance(text, str) or len(text.encode("utf-8")) > MAX_LOG_BYTES:
        raise ValueError("Diagnostic input must be text within the engine log limit")
    events = []
    unparsed = Counter()
    samples = []
    limit_reached = False
    for number, line in enumerate(text.split("\n"), 1):
        line = line.removesuffix("\r")
        kind = _candidate(line)
        if kind is None:
            continue
        if len(events) == MAX_EVENTS:
            limit_reached = True
            break
        event = _decode(kind, line, number) if len(line) <= MAX_EVENT_LINE else None
        if event is None:
            unparsed[kind] += 1
            if len(samples) < MAX_ISSUE_SAMPLES:
                samples.append({"line": number, "kind": kind})
        else:
            events.append(event)
    return {
        "schema_version": 1,
        "parser_version": PARSER_VERSION,
        "events": events,
        "unparsed_candidates": dict(unparsed),
        "issue_samples": samples,
        "event_limit_reached": limit_reached,
    }
