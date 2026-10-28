"""Fail-closed script coverage and Forge deck serialization."""

import re
import zipfile
from pathlib import Path

from commander.engine.engine_aliases import ReviewedCardIndex, apply_alias_reviews


def card_index(cards_archive: Path) -> dict[str, str]:
    """Map canonical/face names to the pinned script's deck name; do not guess cards."""
    index, variants, fronts = {}, {}, {}
    declared_faces, references = set(), set()
    with zipfile.ZipFile(cards_archive) as archive:
        for member in archive.infolist():
            if not member.filename.endswith(".txt"):
                continue
            if member.file_size > 1024 * 1024:
                raise ValueError("Unexpected oversized engine card script")
            script = archive.read(member).decode("utf-8-sig")
            declared_faces.update(
                name.strip()
                for name in re.findall(r"^Name:(.+)$", script, re.MULTILINE)
            )
            references.update(
                name.strip()
                for name in re.findall(r"^CopyFaceFrom:(.+)$", script, re.MULTILINE)
            )
            names = _face_names(script)
            if not names:
                continue
            combined = " // ".join(names)
            exported = combined if "AlternateMode:Split" in script else names[0]
            _bind_alias(index, combined, exported)
            for alias in _variant_aliases(script, len(names)):
                _bind_alias(variants, alias, exported)
            if names[0] != combined:
                fronts.setdefault(names[0], set()).add(exported)
    if references - declared_faces:
        raise ValueError("Engine has unresolved copied-face references")
    for alias, exported in variants.items():
        _bind_alias(index, alias, exported)
    for alias, exports in fronts.items():
        if alias not in index and len(exports) == 1:
            index[alias] = next(iter(exports))
    return apply_alias_reviews(cards_archive, index)


def _bind_alias(index, alias, exported) -> None:
    if alias in index and index[alias] != exported:
        raise ValueError(f"Ambiguous engine card name: {alias}")
    index[alias] = exported


def _face_names(script: str) -> list[str]:
    """Read explicit face names or copied-face references in declared face order."""
    if "AlternateMode:Specialize" in script:
        names = re.findall(r"^Name:(.+)$", script, re.MULTILINE)
        if not names:
            raise ValueError("Specialize script has no starting face")
        return [names[0].strip()]
    names = []
    for face in re.split(r"^ALTERNATE\r?$", script, flags=re.MULTILINE):
        declared = re.findall(r"^(?:Name|CopyFaceFrom):(.+)$", face, re.MULTILINE)
        if len(declared) != 1:
            raise ValueError(
                "Engine face requires one unambiguous Name or CopyFaceFrom"
            )
        names.append(declared[0].strip())
    return names


def _variant_aliases(script: str, face_count: int) -> list[str]:
    """Use only explicit Universes Within metadata; never infer aliases from Oracle text."""
    faces = re.split(r"^ALTERNATE\r?$", script, flags=re.MULTILINE)
    if len(faces) != face_count:
        return []
    variants = [
        re.findall(r"^Variant:UniversesWithin:FlavorName:(.+)$", face, re.MULTILINE)
        for face in faces
    ]
    if not variants[0] or any(len(names) > 1 for names in variants):
        return []
    aliases = [variants[0][0].strip()]
    if all(variants):
        aliases.append(" // ".join(names[0].strip() for names in variants))
    return aliases


def coverage(revision: dict, index: dict) -> dict:
    """Report script presence; this does not prove AI proficiency or rules correctness."""
    missing = [
        entry["card"]["name"]
        for entry in revision["cards"]
        if script_match(entry["card"]["name"], index, entry["card"].get("card_id"))[
            "script_status"
        ]
        != "script_present"
    ]
    report = {
        "revision_id": revision["id"],
        "missing": sorted(missing),
        "status": "supported_scripts" if not missing else "unsupported",
        "basis": "pinned_script_presence_only",
    }
    if isinstance(index, ReviewedCardIndex):
        used = [
            index.reviews[entry["card"]["name"]]
            for entry in revision["cards"]
            if entry["card"]["name"] in index.reviews
        ]
        if used:
            report["alias_reviews"] = used
    return report


def safe_engine_name(name: str) -> bool:
    """Apply the same bounded name checks to exports and script-coverage diagnostics."""
    return (
        isinstance(name, str)
        and 0 < len(name) <= 300
        and name.isprintable()
        and "|" not in name
    )


def script_match(name: str, index: dict, card_id: str | None = None) -> dict:
    """Classify exact aliases without interpreting Oracle text as executable rules."""
    if name not in index:
        return {"script_status": "missing_script", "engine_name": None}
    review = index.reviews.get(name) if isinstance(index, ReviewedCardIndex) else None
    if review is not None and card_id != review["oracle_id"]:
        return {"script_status": "missing_script", "engine_name": None}
    exported = index[name]
    if not safe_engine_name(exported):
        return {"script_status": "unsafe_export", "engine_name": None}
    result = {"script_status": "script_present", "engine_name": exported}
    if review is not None:
        result["alias_review"] = review
    return result


def serialize_deck(revision: dict, index: dict, seat: int) -> str:
    """Export generated seat metadata and exact indexed cards, never user commands."""
    for entry in revision["cards"]:
        name = entry["card"]["name"]
        if name in index and not safe_engine_name(index[name]):
            raise ValueError("Unsafe engine card serialization")
    report = coverage(revision, index)
    if report["missing"]:
        raise ValueError("Unsupported engine cards: " + ", ".join(report["missing"]))
    if not isinstance(seat, int) or isinstance(seat, bool) or seat not in range(1, 5):
        raise ValueError("Expected one of four seats")
    lines = ["[metadata]", f"Name=seat-{seat}"]
    for zone, heading in (
        ("commander", "[Commander]"),
        ("mainboard", "[Main]"),
        ("stickers", "[Stickers]"),
        ("attractions", "[Attractions]"),
    ):
        lines.append(heading)
        for entry in revision["cards"]:
            if entry["zone"] == zone:
                name = index[entry["card"]["name"]]
                if not safe_engine_name(name):
                    raise ValueError("Unsafe engine card serialization")
                lines.append(f"{entry['quantity']} {name}")
    return "\n".join(lines) + "\n"
