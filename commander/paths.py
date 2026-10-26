"""Stable checkout locations shared by CLI and backend services."""

from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
CONFIG_ROOT = PROJECT_ROOT / "config"
DATA_ROOT = PROJECT_ROOT / "data"
