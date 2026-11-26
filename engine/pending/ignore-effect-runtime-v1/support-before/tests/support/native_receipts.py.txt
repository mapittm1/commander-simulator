"""Hash-bound receipt I/O for fixed native witnesses; uploads never select names."""

from pathlib import Path
from unittest import TestCase

from commander.engine.forge_config import EngineSettings, file_hash, read_settings
from commander.engine.interactions.suite import (
    read_bounded_json,
    read_hashed_json,
    write_bounded_json,
)

CASES_PER_RECEIPT_PART = 500


def _chunk_name(name: str, index: int) -> str:
    stem = name.removesuffix(".json")
    return f"{stem}-{index:04d}.json"


def write_test_receipts(
    run: Path, engine: dict, manifest_name: str, parts: dict[str, list[dict]]
) -> None:
    """Write fixed cases in bounded parts with engine and unchanged-profile identity."""
    receipts = []
    for name, rows in parts.items():
        if len(rows) <= CASES_PER_RECEIPT_PART:
            path = run / name
            write_bounded_json(path, {"cases": rows})
            receipts.append(
                {"name": name, "sha256": file_hash(path), "cases": len(rows)}
            )
            continue
        chunks = []
        for offset in range(0, len(rows), CASES_PER_RECEIPT_PART):
            selected = rows[offset : offset + CASES_PER_RECEIPT_PART]
            chunk_name = _chunk_name(name, offset // CASES_PER_RECEIPT_PART)
            path = run / chunk_name
            write_bounded_json(path, {"cases": selected})
            chunks.append(
                {"name": chunk_name, "sha256": file_hash(path), "cases": len(selected)}
            )
        path = run / name
        write_bounded_json(path, {"chunks": chunks})
        receipts.append(
            {
                "name": name,
                "sha256": file_hash(path),
                "cases": len(rows),
                "chunked": True,
            }
        )
    write_bounded_json(
        run / manifest_name,
        {
            "engine": engine,
            "parts": receipts,
            "default_profile_sha256": file_hash(
                read_settings().home / "res/ai/Default.ai"
            ),
        },
    )


def read_test_receipts(
    settings: EngineSettings, run: Path, manifest_name: str, expected: dict[str, int]
) -> list[dict]:
    """Validate identities and read only caller-selected fixed receipt paths."""
    manifest = read_bounded_json(run / manifest_name)
    if manifest["engine"] != settings.verify() or manifest[
        "default_profile_sha256"
    ] != file_hash(settings.home / "res/ai/Default.ai"):
        raise ValueError("Native witness engine or profile identity changed")
    if [part["name"] for part in manifest["parts"]] != list(expected):
        raise ValueError("Native witness receipt names differ from the fixed selection")
    cases = []
    for part, (name, count) in zip(manifest["parts"], expected.items()):
        cases.extend(_read_receipt_part(run, part, name, count))
    return cases


def _read_case_part(run: Path, name: str, digest: str, count: int) -> list[dict]:
    document, actual_digest = read_hashed_json(run / name)
    if (digest, count) != (actual_digest, len(document["cases"])):
        raise ValueError("Native witness receipt digest or case count changed")
    return document["cases"]


def _read_receipt_part(run: Path, part: dict, name: str, count: int) -> list[dict]:
    if part["cases"] != count:
        raise ValueError("Native witness receipt digest or case count changed")
    if not part.get("chunked", False):
        return _read_case_part(run, name, part["sha256"], count)
    index_document, digest = read_hashed_json(run / name)
    if part["sha256"] != digest:
        raise ValueError("Native witness receipt digest or case count changed")
    chunks = index_document["chunks"]
    sizes = [
        min(CASES_PER_RECEIPT_PART, count - offset)
        for offset in range(0, count, CASES_PER_RECEIPT_PART)
    ]
    # Validate every physical name against caller-selected names before reading files.
    if (
        count <= CASES_PER_RECEIPT_PART
        or len(chunks) != len(sizes)
        or any(
            chunk["name"] != _chunk_name(name, index) or chunk["cases"] != size
            for index, (chunk, size) in enumerate(zip(chunks, sizes))
        )
    ):
        raise ValueError(
            "Native witness chunk names or counts differ from the fixed selection"
        )
    cases = []
    for chunk, size in zip(chunks, sizes):
        cases.extend(_read_case_part(run, chunk["name"], chunk["sha256"], size))
    return cases


def assert_paid_outcome(case, row):
    """Check shared preview isolation, source admission and paid-resolution invariants."""
    case.assertEqual(row["before"], row["after_choice"])
    case.assertTrue(row["world_unchanged"] and row["rng_restored"])
    selected = row["selected"]
    source = row["before"]["cards"]["source"]
    if row["control"] != "positive":
        if selected is not None:
            case.assertNotEqual(selected["host_id"], source["id"])
        return False
    case.assertIsNotNone(selected)
    case.assertEqual((selected["host_id"], selected["actor"]), (source["id"], 0))
    case.assertTrue(selected["in_world"])
    case.assertEqual(row["paid_action"]["host_id"], source["id"])
    case.assertEqual(row["cast_counts"], [1, 0, 0, 0])
    case.assertEqual(row["life"], [40, 40, 40, 40])
    case.assertEqual(row["final"]["source"]["zone"], "Graveyard")
    for tag, card in row["final"].items():
        before = row["before"]["cards"][tag]
        case.assertEqual((card["id"], card["owner"]), (before["id"], before["owner"]))
    return True


def assert_rule_summary(
    case: TestCase, summary: dict, count: int, native_classes: tuple[str, ...]
) -> None:
    """Require complete native results, successful setup, and the exact packaged class origins."""
    case.assertEqual(
        summary["counts"],
        {"total": count, "passed": count, "failed": 0, "skipped": 0},
    )
    for key in (
        "testng_status",
        "configuration_failures",
        "configuration_skips",
        "partial_successes",
    ):
        case.assertEqual(summary[key], 0)
    case.assertEqual(
        set(summary["production_classes_from_packaged_jar"]), set(native_classes)
    )
