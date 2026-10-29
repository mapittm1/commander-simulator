"""Private native printing preflight; it never plays a game or executes uploaded scripts."""

import importlib
import sys
from pathlib import Path

from commander.engine.catalog_probe_io import source_hashes
from commander.engine.forge_config import EngineSettings, file_hash
from commander.engine.interactions.native_bridge import initialize_native_engine
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.engine.native_runtime import require_bridge
from commander.engine.printing_resources import edition_fingerprint


def main():
    """Observe exact native collector identities and functional light variants."""
    path = Path(sys.argv[1]).resolve()
    plan = read_bounded_json(path)
    settings = EngineSettings(
        Path(plan["home"]),
        Path(plan["java"]),
        Path(plan["lock"]) if plan["lock"] else None,
    )
    if (
        plan["schema_version"] != 1
        or source_hashes() != plan["source_hashes"]
        or settings.verify() != plan["engine"]
        or edition_fingerprint(settings.home) != plan["editions"]
    ):
        raise ValueError("Native printing preflight inputs changed before startup")
    require_bridge()
    java = importlib.import_module("jpype")
    initialize_native_engine(
        java, plan["jvm"], [str(settings.home / plan["engine"]["jar_name"])]
    )
    rows = [observe_printing(java, printing) for printing in plan["printings"]]
    write_bounded_json(
        path.with_name("printing-receipt.json"),
        {
            "schema_version": 1,
            "plan_sha256": file_hash(path),
            "engine": plan["engine"],
            "editions": edition_fingerprint(settings.home),
            "printings": rows,
            "java_version": str(
                java.JClass("java.lang.System").getProperty("java.version")
            ),
        },
        indent=2,
    )


def observe_printing(java, printing):
    """A native fallback must expose its actual metadata so the parent can reject it."""
    paper = (
        java.JClass("forge.model.FModel")
        .getMagicDb()
        .getVariantCards()
        .getCard(
            printing["engine_name"],
            printing["set"].upper(),
            printing["collector_number"],
        )
    )
    if paper is None:
        raise ValueError("Native printing is absent: " + printing["name"])
    face = paper.getMainFace()
    return {
        "id": printing["id"],
        "name": str(paper.getName()),
        "set": str(paper.getEdition()).lower(),
        "collector_number": str(paper.getCollectorNumber()),
        "attraction_lights": sorted(int(light) for light in face.getAttractionLights()),
        "is_attraction": bool(
            face.getType().isArtifact() and face.getType().hasSubtype("Attraction")
        ),
    }


if __name__ == "__main__":
    main()
