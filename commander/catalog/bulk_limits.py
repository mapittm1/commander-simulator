"""Explicit resource policy and bounded binary decoding for catalog maintenance."""

import gzip
import io
from contextlib import ExitStack, contextmanager
from dataclasses import dataclass, fields
from pathlib import Path
from time import monotonic
from typing import BinaryIO, Iterator, TextIO

MAX_JSON_DEPTH = 64
CHUNK_BYTES = 65536


@dataclass(frozen=True)
class BulkLimits:
    """Operator policy; uploads and provider metadata cannot increase these bounds."""

    metadata_bytes: int = 2 * 1024**2
    download_bytes: int = 8 * 1024**3
    expanded_bytes: int = 32 * 1024**3
    record_characters: int = 2 * 1024**2
    records: int = 5_000_000
    download_seconds: int = 1800
    import_seconds: int = 21600

    def __post_init__(self):
        for field in fields(self):
            value = getattr(self, field.name)
            if not isinstance(value, int) or isinstance(value, bool) or value <= 0:
                raise ValueError("Bulk limits must be positive integers")


DEFAULT_LIMITS = BulkLimits()


@dataclass(frozen=True)
class BulkDeadline:
    """Cooperative deadline checked around reads and between imported records."""

    expires_at: float
    action: str

    @classmethod
    def after(cls, seconds: int, action: str) -> "BulkDeadline":
        """Start a single budget shared by the entire maintenance operation."""
        return cls(monotonic() + seconds, action)

    def check(self) -> None:
        """Reject expired work without resetting the budget at each record or chunk."""
        if monotonic() >= self.expires_at:
            raise ValueError(f"Bulk {self.action} time limit exceeded")


class BoundedReader(io.RawIOBase):
    """Count bytes before text decoding; the caller owns the wrapped stream."""

    def __init__(self, source: BinaryIO, maximum: int, deadline: BulkDeadline):
        super().__init__()
        self.source = source
        self.maximum = maximum
        self.deadline = deadline
        self.consumed = 0

    def readable(self) -> bool:
        """Allow BufferedReader/TextIOWrapper to request only bounded chunks."""
        return True

    def readinto(self, buffer: bytearray | memoryview) -> int:
        """Read at most one byte beyond the remaining allowance to detect overflow."""
        self.deadline.check()
        if not buffer:
            return 0
        chunk = self.source.read(min(len(buffer), self.maximum - self.consumed + 1))
        self.deadline.check()
        self.consumed += len(chunk)
        if self.consumed > self.maximum:
            raise ValueError("Bulk input exceeds its byte limit")
        buffer[: len(chunk)] = chunk
        return len(chunk)


@contextmanager
def open_bulk_text(
    path: Path, limits: BulkLimits, deadline: BulkDeadline
) -> Iterator[TextIO]:
    """Bound both compressed input and expanded bytes without rewinding decompression."""
    if path.stat().st_size > limits.download_bytes:
        raise ValueError("Bulk file exceeds its input byte limit")
    with path.open("rb") as raw, ExitStack() as resources:
        compressed = raw.read(2) == b"\x1f\x8b"
        raw.seek(0)
        encoded = resources.enter_context(
            io.BufferedReader(BoundedReader(raw, limits.download_bytes, deadline))
        )
        decoded = (
            resources.enter_context(gzip.GzipFile(fileobj=encoded))
            if compressed
            else encoded
        )
        text = resources.enter_context(
            io.TextIOWrapper(
                io.BufferedReader(
                    BoundedReader(decoded, limits.expanded_bytes, deadline)
                ),
                encoding="utf-8-sig",
            )
        )
        try:
            yield text
        except (EOFError, UnicodeError) as error:
            raise ValueError("Invalid or truncated bulk data") from error
