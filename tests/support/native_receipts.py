"""Hash-bound receipt I/O for fixed native witnesses; uploads never select names."""

from pathlib import Path

from commander.engine.forge_config import EngineSettings, file_hash, read_settings
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json


def write_test_receipts(
    run: Path, engine: dict, manifest_name: str, parts: dict[str, list[dict]]
) -> None:
    """Write fixed cases in bounded parts with engine and unchanged-profile identity."""
    receipts = []
    for name, rows in parts.items():
        path = run / name
        write_bounded_json(path, {"cases": rows})
        receipts.append({"name": name, "sha256": file_hash(path), "cases": len(rows)})
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
        path = run / name
        rows = read_bounded_json(path)["cases"]
        if (part["sha256"], part["cases"], len(rows)) != (
            file_hash(path),
            count,
            count,
        ):
            raise ValueError("Native witness receipt digest or case count changed")
        cases.extend(rows)
    return cases
