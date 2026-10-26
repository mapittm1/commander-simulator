"""Readable CLI JSON with lossless fallback for legacy terminal encodings."""

import json
import sys


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
