"""Run the same quality gate locally and in CI; --fix formats source first."""

import argparse

# Only fixed quality-tool commands are executed.
import subprocess  # nosec B404
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent


def main() -> None:
    """Stop at the first failing formatter, linter, security check, or test."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--fix", action="store_true", help="Apply isort and Black first"
    )
    args = parser.parse_args()
    excluded = {".git", ".venv", "data", "__pycache__"}
    source_files = sorted(
        str(path.relative_to(PROJECT_ROOT))
        for path in PROJECT_ROOT.rglob("*.py")
        if not excluded.intersection(path.relative_to(PROJECT_ROOT).parts)
    )
    sort_options = [] if args.fix else ["--check-only", "--diff"]
    format_options = [] if args.fix else ["--check", "--diff"]
    commands = [
        ["isort", *sort_options, *source_files],
        ["black", *format_options, *source_files],
        ["pylint", *source_files],
        ["bandit", "-c", "pyproject.toml", *source_files],
        ["pytest", "-q"],
    ]
    for command in commands:
        print(f"Running: {' '.join(command)}", flush=True)
        # Executable and arguments are internal; no shell or uploaded commands.
        result = subprocess.run(  # nosec B603
            [sys.executable, "-m", *command],
            cwd=PROJECT_ROOT,
            check=False,
            shell=False,
            timeout=300,
        )
        if result.returncode:
            raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
