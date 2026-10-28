"""Private bounded worker: build each matched card through Forge's native factory."""

import importlib
import sys
from pathlib import Path

from commander.engine.catalog_probe_io import (
    read_rows,
    source_hashes,
    stream_hash,
    write_rows,
)
from commander.engine.forge_config import EngineSettings, file_hash
from commander.engine.interactions.fixtures import _new_game
from commander.engine.interactions.native_bridge import (
    fixture_errors,
    initialize_native_engine,
)
from commander.engine.interactions.runner import BRIDGE_VERSION, require_bridge
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json


def main() -> None:
    """Use only a generated frozen plan; no uploaded code, scripts or game states."""
    plan_path = Path(sys.argv[1]).resolve()
    plan = read_bounded_json(plan_path)
    run = plan_path.parent
    if plan.get("schema_version") != 1 or plan.get("source_hashes") != source_hashes():
        raise ValueError("Catalog probe source/protocol changed before startup")
    settings = EngineSettings(
        Path(plan["home"]),
        Path(plan["java"]),
        Path(plan["engine_lock"]) if plan.get("engine_lock") else None,
    )
    engine = settings.verify()
    require_bridge()
    if engine != plan["engine"] or plan["bridge_version"] != BRIDGE_VERSION:
        raise ValueError("Catalog probe engine/bridge differs from its frozen plan")
    if stream_hash(run / "catalog-inputs.jsonl") != plan["inputs_sha256"]:
        raise ValueError("Catalog probe inputs changed before startup")
    java = importlib.import_module("jpype")
    initialize_native_engine(
        java, plan["jvm"], [str(settings.home / engine["jar_name"])]
    )
    count, digest = write_rows(
        run / "catalog-observations.jsonl", _observations(java, run)
    )
    if count != plan["rows"]:
        raise ValueError("Catalog probe row inventory changed")
    write_bounded_json(
        run / "catalog-receipt.json",
        {
            "schema_version": 1,
            "plan_sha256": file_hash(plan_path),
            "rows": count,
            "observations_sha256": digest,
            "engine": engine,
            "java_version": str(
                java.JClass("java.lang.System").getProperty("java.version")
            ),
        },
        indent=2,
    )


def _observations(java, run):
    game = None
    for index, proposal in enumerate(read_rows(run / "catalog-inputs.jsonl")):
        if index % 64 == 0:
            game = _new_game(java)
            print(f"Catalog native construction: {index} checked", flush=True)
        row = {
            "card_id": proposal["card_id"],
            "name": proposal["name"],
            "engine_name": proposal["engine_name"],
        }
        if proposal["script_status"] != "script_present":
            yield {**row, "status": "script_unavailable"}
            continue
        try:
            yield {**row, **_construct(java, game, proposal["engine_name"])}
        except fixture_errors(java) as error:
            yield {**row, "status": "construction_error", "error": str(error)[:500]}


def _construct(java, game, name):
    database = java.JClass("forge.model.FModel").getMagicDb()
    paper = database.getCommonCards().getCard(name)
    pool = "common"
    if paper is None:
        paper = database.getVariantCards().getCard(name)
        pool = "variant"
    if paper is None:
        return {"status": "missing_native_card"}
    if str(paper.getName()) != name:
        return {
            "status": "native_identity_mismatch",
            "native_name": str(paper.getName())[:300],
        }
    owner = game.getPlayers().get(java.JInt(0))
    card = java.JClass("forge.game.card.Card").fromPaperCard(paper, owner)
    return {
        "status": "constructed",
        "pool": pool,
        "native_name": str(card.getName())[:300],
        "states": len(card.getStates()),
        "abilities": len(card.getAllSpellAbilities()),
        "statics": len(card.getStaticAbilities()),
        "triggers": len(card.getTriggers()),
        "replacements": len(card.getReplacementEffects()),
    }


if __name__ == "__main__":
    main()
