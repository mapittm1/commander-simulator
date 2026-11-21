"""Run the same quality gate locally and in CI; --fix formats source first."""

import argparse
import os

# Only fixed quality-tool commands are executed.
import subprocess  # nosec B404
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent


def _source_files(project_root: Path) -> list[str]:
    """Collect Python source without traversing generated or archived trees."""
    excluded = {".git", ".venv", "data", "__pycache__"}
    source_files = []
    for directory, subdirectories, filenames in os.walk(project_root):
        subdirectories[:] = [name for name in subdirectories if name not in excluded]
        for name in filenames:
            if name.endswith(".py"):
                path = Path(directory) / name
                source_files.append(str(path.relative_to(project_root)))
    return sorted(source_files)


def main() -> None:
    """Stop at the first failing formatter, linter, security check, or test."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--fix", action="store_true", help="Apply isort and Black first"
    )
    args = parser.parse_args()
    source_files = _source_files(PROJECT_ROOT)
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
