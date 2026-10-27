"""Private child-process entry point for the fixed controlled interaction suite."""

import importlib
import json
import sys
import traceback
from importlib import metadata
from pathlib import Path

from commander.engine.forge_config import EngineSettings
from commander.engine.interactions.fixtures import observe_case
from commander.engine.interactions.suite import (
    CASE_IDS,
    MAX_RECEIPT_BYTES,
    read_bounded_json,
)


def main() -> None:
    """Load only generated parent inputs and exercise all fixed cases in one bounded JVM."""
    path = Path(sys.argv[1]).resolve()
    inputs = read_bounded_json(path)
    settings = EngineSettings(Path(inputs["home"]), Path(inputs["java"]))
    lock = settings.verify()
    java = importlib.import_module("jpype")
    if metadata.version("JPype1") != inputs["bridge_version"]:
        raise ValueError("Interaction bridge changed before worker startup")
    java.startJVM(
        inputs["jvm"],
        "-Xmx2048m",
        "-XX:ActiveProcessorCount=2",
        "-Djava.awt.headless=true",
        "-Dfile.encoding=UTF-8",
        classpath=[str(settings.home / lock["jar_name"])],
        convertStrings=True,
    )
    java.JClass("forge.gui.GuiBase").setInterface(java.JClass("forge.GuiDesktop")())
    java.JClass("forge.model.FModel").initialize(None, None)
    cases = []
    for case in CASE_IDS:
        try:
            cases.append(observe_case(java, inputs["cards"], case))
        except (
            java.JException,
            RuntimeError,
            ValueError,
            TypeError,
            AttributeError,
        ) as error:
            traceback.print_exc()
            cases.append({"id": case, "error": str(error)[:1000]})
    result = {
        "bridge_version": metadata.version("JPype1"),
        "schema_version": 1,
        "suite_hash": inputs["suite_hash"],
        "engine": lock,
        "java_version": str(
            java.JClass("java.lang.System").getProperty("java.version")
        ),
        "cases": cases,
    }
    save_observations(path.with_name("observations.json"), result)


def save_observations(path: Path, result: dict) -> None:
    """Reject excessive state output before creating an exclusive receipt file."""
    serialized = json.dumps(result, indent=2, ensure_ascii=True)
    if len(serialized.encode("utf-8")) > MAX_RECEIPT_BYTES:
        raise ValueError("Interaction observations exceed the receipt size limit")
    with path.open("x", encoding="utf-8") as stream:
        stream.write(serialized)


if __name__ == "__main__":
    main()
