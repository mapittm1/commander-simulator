"""Preserve bounded event evidence beside an engine run's original log."""

import json
from pathlib import Path

from commander.diagnostics.game_diagnostics import summarize_events
from commander.diagnostics.game_events import extract_events
from commander.engine.forge_config import file_hash


def record_diagnostics(run: Path, text: str, game_status: str) -> dict:
    """Create event/report files without overwriting existing evidence."""
    parsed = extract_events(text)
    report = summarize_events(parsed, game_status)
    report["log_sha256"] = file_hash(run / "forge.log")
    report["artifacts"] = {"events": "events.jsonl", "report": "diagnostics.json"}
    with (run / "events.jsonl").open("x", encoding="utf-8") as stream:
        for event in parsed["events"]:
            stream.write(json.dumps(event, ensure_ascii=False) + "\n")
    with (run / "diagnostics.json").open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2, ensure_ascii=False)
    return report
