"""Readable CLI JSON with lossless fallback for legacy terminal encodings."""

import json
import sys


def write_text(value: str, stream=None) -> None:
    """Print progress safely even when a legacy terminal cannot encode a card name."""
    output = sys.stdout if stream is None else stream
    encoding = getattr(output, "encoding", None)
    if encoding:
        value = value.encode(encoding, errors="backslashreplace").decode(encoding)
    print(value, file=output, flush=True)


def write_json(value, stream=None) -> None:
    """Use native Unicode where representable; otherwise emit standard JSON escapes."""
    output = sys.stdout if stream is None else stream
    text = json.dumps(value, indent=2, ensure_ascii=False)
    encoding = getattr(output, "encoding", None)
    if encoding:
        try:
            text.encode(encoding)
        except UnicodeEncodeError:
            text = json.dumps(value, indent=2, ensure_ascii=True)
    print(text, file=output)
