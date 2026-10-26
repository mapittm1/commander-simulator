"""Local readiness diagnostics with actionable setup instructions."""

from pathlib import Path
from sqlite3 import Connection

from commander.engine.forge_config import read_settings
from commander.engine.forge_engine import engine_health
from commander.rules.legality import require_current_catalog


def readiness_report(connection: Connection, config: Path) -> dict:
    """Check catalog freshness and pinned runtime independently without playing a game."""
    checks = []
    for name, probe, action in (
        (
            "catalog",
            lambda: require_current_catalog(connection),
            "Run: python commander_simulator.py sync",
        ),
        (
            "engine",
            lambda: engine_health(read_settings(config)),
            "Check config/engine_config.json and the pinned installation instructions in README.md.",
        ),
    ):
        try:
            evidence = probe()
            checks.append({"name": name, "status": "ready", "evidence": evidence})
        except (OSError, ValueError, KeyError, RuntimeError) as error:
            checks.append(
                {
                    "name": name,
                    "status": "blocked",
                    "error": str(error),
                    "next_step": action,
                }
            )
    return {
        "status": (
            "ready"
            if all(check["status"] == "ready" for check in checks)
            else "needs_setup"
        ),
        "checks": checks,
        "next_command": "python commander_simulator.py demo --runs 1",
        "scope": "Local CLI readiness; not complete rules conformance or a production web deployment.",
    }
