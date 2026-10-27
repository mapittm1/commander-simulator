"""Small shared guards for bounded JSON parsing across backend domains."""


def require_json_depth(text: str, maximum: int) -> None:
    """Reject excessive structural nesting before parsing, respecting escaped strings."""
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
            if depth > maximum:
                raise ValueError(f"JSON nesting exceeds {maximum} levels")
        elif character in "]}":
            depth -= 1
