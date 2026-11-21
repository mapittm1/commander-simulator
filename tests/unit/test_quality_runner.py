"""Verify source discovery excludes generated trees before visiting them."""

import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.check import _source_files


class QualitySourceDiscoveryTests(unittest.TestCase):
    """Generated archives must not slow or expand the source quality checks."""

    def test_discovers_nested_source_without_entering_excluded_directories(self):
        """Discover nested Python files while pruning every excluded tree."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in (
                "app.py",
                "commander/nested/rules.py",
                "tests/unit/test_rules.py",
                "notes.txt",
                "data/build/source/hidden.py",
                ".venv/hidden.py",
                ".git/hooks/hidden.py",
                "commander/data/hidden.py",
                "commander/__pycache__/hidden.py",
            ):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.touch()
            visited = []
            walk = os.walk

            def observe_walk(path):
                for entry in walk(path):
                    visited.append(Path(entry[0]).relative_to(root))
                    yield entry

            with patch("tools.check.os.walk", side_effect=observe_walk):
                files = _source_files(root)

            self.assertEqual(
                files,
                sorted(
                    str(Path(name))
                    for name in (
                        "app.py",
                        "commander/nested/rules.py",
                        "tests/unit/test_rules.py",
                    )
                ),
            )
            self.assertTrue(visited)
            excluded = {".git", ".venv", "data", "__pycache__"}
            self.assertTrue(
                all(not excluded.intersection(path.parts) for path in visited)
            )
