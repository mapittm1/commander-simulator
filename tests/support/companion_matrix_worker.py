"""Fixed opt-in companion-condition matrix worker; no production deck admission."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

# Fixed test-only checkout bootstrap.
# pylint: disable=wrong-import-position
from commander.engine.companion_worker import observe_admission
from commander.engine.forge_config import file_hash
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from tests.support.native_worker import initialize_test_engine


def main():
    """Share one bounded JVM while giving each fixed case a fresh native game state."""
    run = Path(sys.argv[1]).resolve()
    java, engine = initialize_test_engine()
    path = run / "matrix-inputs.json"
    cases = read_bounded_json(path)["cases"]
    results = [
        {"id": case["id"], **observe_admission(java, case["plan"])} for case in cases
    ]
    write_bounded_json(
        run / "matrix-receipt.json",
        {
            "engine": engine,
            "inputs_sha256": file_hash(path),
            "cases": results,
        },
        indent=2,
    )


if __name__ == "__main__":
    main()
