"""Conservative result parsing for the pinned Forge one-game CLI protocol."""

import re


def parse_game_result(text: str, process: dict) -> dict:
    """Never count an exception, missing result, resource failure, or timeout as a win."""
    if process["status"] != "finished":
        return {"status": process["status"], "winner_seat": None}
    if process["returncode"] != 0:
        return {"status": "engine_error", "winner_seat": None}
    if re.search(r"^\s*at [\w.$]+\([^\n]*\.java:\d+\)", text, re.MULTILINE):
        return {"status": "engine_error", "winner_seat": None}
    if "Stopping slow match as draw" in text:
        return {"status": "engine_timeout", "winner_seat": None}
    results = [line for line in text.splitlines() if line.startswith("Game Result:")]
    if len(results) != 1 or "Game Outcome:" not in text:
        return {"status": "unrecognized_result", "winner_seat": None}
    return _completed_result(text, results[0])


def _completed_result(text: str, result_line: str) -> dict:
    """Recognize only the pinned engine's corroborated single-game outcomes."""
    win = re.fullmatch(
        r"Game Result: Game 1 ended in (\d+) ms\. Ai\(([1-4])\)-seat-\2 has won!",
        result_line,
    )
    if win:
        seat = int(win[2])
        if f"Game Outcome: Ai({seat})-seat-{seat} has won" not in text:
            return {"status": "unrecognized_result", "winner_seat": None}
        return {
            "status": "completed",
            "outcome": "win",
            "winner_seat": seat,
            "engine_elapsed_ms": int(win[1]),
        }
    draw = re.fullmatch(
        r"Game Result: Game 1 ended in a Draw! Took (\d+) ms\.", result_line
    )
    if draw:
        return {
            "status": "completed",
            "outcome": "draw",
            "winner_seat": None,
            "engine_elapsed_ms": int(draw[1]),
        }
    return {"status": "unrecognized_result", "winner_seat": None}
