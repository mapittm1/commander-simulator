"""Private child-process entry point for the fixed controlled interaction suite."""

import importlib
import sys
import traceback
from importlib import metadata
from pathlib import Path

from commander.engine.forge_config import EngineSettings
from commander.engine.interactions.fixtures import observe_case
from commander.engine.interactions.native_bridge import class_hash
from commander.engine.interactions.ordering import ORDER_CASES
from commander.engine.interactions.ordering_fixtures import observe_ordering_case
from commander.engine.interactions.suite import (
    PARTITIONS,
    definition_fingerprint,
    load_suite,
    read_bounded_json,
    write_bounded_json,
)


def main() -> None:
    """Load only generated parent inputs and exercise all fixed cases in one bounded JVM."""
    path = Path(sys.argv[1]).resolve()
    inputs = read_bounded_json(path)
    suite = load_suite()
    if (
        inputs.get("schema_version") != 2
        or inputs.get("suite_id") != suite["suite_id"]
        or inputs.get("partitions") != list(PARTITIONS)
        or inputs.get("definition_hashes") != suite["definition_hashes"]
        or inputs.get("suite_hash")
        != definition_fingerprint(suite["definition_hashes"])
    ):
        raise ValueError("Interaction definitions changed before worker startup")
    settings = EngineSettings(Path(inputs["home"]), Path(inputs["java"]))
    if class_hash(path.parent) != inputs["native_bridge"]["class_sha256"]:
        raise ValueError("Compiled test controller changed before worker startup")
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
        classpath=[str(settings.home / lock["jar_name"]), str(path.parent / "native")],
        convertStrings=True,
    )
    java.JClass("forge.gui.GuiBase").setInterface(java.JClass("forge.GuiDesktop")())
    java.JClass("forge.model.FModel").initialize(None, None)
    for name, case_ids in PARTITIONS.items():
        result = {
            "bridge_version": metadata.version("JPype1"),
            "schema_version": 2,
            "suite_id": inputs["suite_id"],
            "partition_id": name,
            "suite_hash": inputs["suite_hash"],
            "native_class_sha256": inputs["native_bridge"]["class_sha256"],
            "engine": lock,
            "java_version": str(
                java.JClass("java.lang.System").getProperty("java.version")
            ),
            "cases": _observe_partition(java, inputs["cards"], case_ids),
        }
        save_observations(path.with_name(f"observations-{name}.json"), result)


def _observe_partition(java, cards, case_ids) -> list:
    cases = []
    for case in case_ids:
        try:
            observer = observe_ordering_case if case in ORDER_CASES else observe_case
            cases.append(observer(java, cards, case))
        except (
            java.JException,
            RuntimeError,
            ValueError,
            TypeError,
            AttributeError,
        ) as error:
            traceback.print_exc()
            cases.append({"id": case, "error": str(error)[:1000]})
    return cases


def save_observations(path: Path, result: dict) -> None:
    """Reject excessive state output before creating an exclusive receipt file."""
    write_bounded_json(path, result)


if __name__ == "__main__":
    main()
