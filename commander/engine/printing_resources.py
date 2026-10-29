"""Bind native printing metadata to its actual bounded edition resource inventory."""

import hashlib
import re
from itertools import islice


def edition_fingerprint(home):
    """Hash filenames and contents; added, deleted or edited editions invalidate checks."""
    root = home / "res/editions"
    if root.is_symlink():
        raise ValueError("Native edition directory must not be a symbolic link")
    files = sorted(
        islice(root.rglob("*.txt"), 4097),
        key=lambda path: path.relative_to(root).as_posix(),
    )
    if not 1 <= len(files) <= 4096:
        raise ValueError("Native edition inventory exceeds its file bound or is empty")
    digest, total = hashlib.sha256(), 0
    for path in files:
        if path.is_symlink():
            raise ValueError("Native edition resources must not be symbolic links")
        with path.open("rb") as stream:
            raw = stream.read(512 * 1024 + 1)
        total += len(raw)
        if len(raw) > 512 * 1024 or total > 64 * 1024 * 1024:
            raise ValueError("Native edition resource exceeds its byte bounds")
        digest.update(
            (
                path.relative_to(root).as_posix()
                + "\0"
                + hashlib.sha256(raw).hexdigest()
                + "\n"
            ).encode()
        )
    return {"files": len(files), "sha256": digest.hexdigest()}


def validate_edition_pin(value):
    """Locks may pin the complete edition inventory in addition to native scripts."""
    if not isinstance(value, dict) or set(value) != {"files", "sha256"}:
        raise ValueError("Invalid native edition inventory pin")
    if (
        not isinstance(value["files"], int)
        or isinstance(value["files"], bool)
        or not 1 <= value["files"] <= 4096
    ):
        raise ValueError("Invalid native edition inventory file count")
    if not isinstance(value["sha256"], str) or not re.fullmatch(
        r"[a-f0-9]{64}", value["sha256"]
    ):
        raise ValueError("Invalid native edition inventory SHA-256")
