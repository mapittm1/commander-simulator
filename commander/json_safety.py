"""Small shared guards for bounded JSON parsing across backend domains."""

from dataclasses import dataclass


@dataclass
class JsonDepthGuard:
    """Maintain lexical depth when input arrives in separate bounded chunks."""

    maximum: int
    depth: int = 0
    quoted: bool = False
    escaped: bool = False

    def feed(self, text: str) -> None:
        """Check new text once, respecting strings and escapes across chunk boundaries."""
        for character in text:
            if self.quoted:
                if self.escaped:
                    self.escaped = False
                elif character == "\\":
                    self.escaped = True
                elif character == '"':
                    self.quoted = False
            elif character == '"':
                self.quoted = True
            elif character in "[{":
                self.depth += 1
                if self.depth > self.maximum:
                    raise ValueError(f"JSON nesting exceeds {self.maximum} levels")
            elif character in "]}":
                self.depth -= 1


def require_json_depth(text: str, maximum: int) -> None:
    """Reject excessive structural nesting before parsing, respecting escaped strings."""
    JsonDepthGuard(maximum).feed(text)
