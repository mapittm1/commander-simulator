"""Fail-closed script coverage and Forge deck serialization."""

import re
import zipfile
from pathlib import Path


def card_index(cards_archive: Path) -> dict[str, str]:
    """Map canonical/face names to the pinned script's deck name; do not guess cards."""
    index = {}
    with zipfile.ZipFile(cards_archive) as archive:
        for member in archive.infolist():
            if not member.filename.endswith(".txt"):
                continue
            if member.file_size > 1024 * 1024:
                raise ValueError("Unexpected oversized engine card script")
            script = archive.read(member).decode("utf-8-sig")
            names = re.findall(r"^Name:(.+)$", script, re.MULTILINE)
            names = [name.strip() for name in names]
            if not names:
                continue
            combined = " // ".join(names)
            exported = combined if "AlternateMode:Split" in script else names[0]
            for alias in (combined, names[0]):
                if alias in index and index[alias] != exported:
                    raise ValueError(f"Ambiguous engine card name: {alias}")
                index[alias] = exported
    return index


def coverage(revision: dict, index: dict) -> dict:
    """Report script presence; this does not prove AI proficiency or rules correctness."""
    missing = [
        entry["card"]["name"]
        for entry in revision["cards"]
        if entry["card"]["name"] not in index
    ]
    return {
        "revision_id": revision["id"],
        "missing": sorted(missing),
        "status": "supported_scripts" if not missing else "unsupported",
        "basis": "pinned_script_presence_only",
    }


def serialize_deck(revision: dict, index: dict, seat: int) -> str:
    """Export generated seat metadata and exact indexed cards, never user commands."""
    report = coverage(revision, index)
    if report["missing"]:
        raise ValueError("Unsupported engine cards: " + ", ".join(report["missing"]))
    if not isinstance(seat, int) or isinstance(seat, bool) or seat not in range(1, 5):
        raise ValueError("Expected one of four seats")
    lines = ["[metadata]", f"Name=seat-{seat}"]
    for zone, heading in (("commander", "[Commander]"), ("mainboard", "[Main]")):
        lines.append(heading)
        for entry in revision["cards"]:
            if entry["zone"] == zone:
                name = index[entry["card"]["name"]]
                if not name.isprintable() or "|" in name:
                    raise ValueError("Unsafe engine card serialization")
                lines.append(f"{entry['quantity']} {name}")
    return "\n".join(lines) + "\n"
