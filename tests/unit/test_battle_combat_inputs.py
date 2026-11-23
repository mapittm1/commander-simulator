"""Verify the Battle harness admits only fixed, hash-reviewed local inputs."""

import json
import unittest
from contextlib import contextmanager
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from commander.engine.forge_config import file_hash
from tests.support import battle_combat_worker


@contextmanager
def reviewed_workspace():
    """Retain temporary review inputs until unittest closes its cleanup context."""
    with TemporaryDirectory() as directory:
        yield Path(directory)


class BattleCombatInputTests(unittest.TestCase):
    """Reject changed Java sources, changed dependencies and alternate dependency paths."""

    def setUp(self):
        self.root = self.enterContext(reviewed_workspace())
        self.base = self.root / "engine/tests/AITest.java"
        self.fixture = self.root / "engine/tests/CommanderBattleCombatTest.java"
        self.restoration = (
            self.root / "engine/tests/CommanderDefenderRestorationTest.java"
        )
        self.jar = (
            self.root
            / "data/engine-builds/m2/org/testng/testng/7.10.2/testng-7.10.2.jar"
        )
        self.base.parent.mkdir(parents=True)
        self.jar.parent.mkdir(parents=True)
        self.base.write_bytes(b"fixed test helper")
        self.fixture.write_bytes(b"fixed Battle fixture")
        self.restoration.write_bytes(b"fixed restoration fixture")
        self.jar.write_bytes(b"fixed dependency")
        self.recipe = {
            "packaged_ai_base_sha256": file_hash(self.base),
            "packaged_testng": {
                "path": self.jar.relative_to(self.root).as_posix(),
                "sha256": file_hash(self.jar),
            },
        }
        self.review = {
            "fixture_sha256": file_hash(self.fixture),
            "restoration_fixture_sha256": file_hash(self.restoration),
        }
        self.write_reviews()
        self.enterContext(patch.object(battle_combat_worker, "PROJECT", self.root))

    def write_reviews(self):
        """Write temporary operator review files without compiling or executing Java."""
        for name, value in (
            ("sticker_recipe.json", self.recipe),
            ("battle_combat_tests.json", self.review),
        ):
            (self.root / "engine" / name).write_text(
                json.dumps(value), encoding="utf-8"
            )

    def test_valid_review_returns_only_the_fixed_sources_and_dependency(self):
        """A valid review admits the three canonical source paths and pinned dependency."""
        sources, dependency = battle_combat_worker.reviewed_inputs()
        self.assertEqual(sources, [self.base, self.fixture, self.restoration])
        self.assertEqual(dependency, self.jar)

    def test_changed_battle_fixture_is_rejected(self):
        """An altered native fixture cannot pass its review digest."""
        self.fixture.write_bytes(b"changed Battle fixture")
        with self.assertRaisesRegex(ValueError, "source changed"):
            battle_combat_worker.reviewed_inputs()

    def test_changed_shared_java_helper_is_rejected(self):
        """The shared base class must retain its independently pinned digest."""
        self.base.write_bytes(b"changed test helper")
        with self.assertRaisesRegex(ValueError, "source changed"):
            battle_combat_worker.reviewed_inputs()

    def test_changed_dependency_is_rejected(self):
        """Replacing the dependency bytes invalidates admission."""
        self.jar.write_bytes(b"changed dependency")
        with self.assertRaisesRegex(ValueError, "dependency changed"):
            battle_combat_worker.reviewed_inputs()

    def test_alternate_dependency_path_is_rejected(self):
        """A review cannot redirect compilation to another dependency path."""
        self.recipe["packaged_testng"]["path"] = "../../outside.jar"
        self.write_reviews()
        with self.assertRaisesRegex(ValueError, "dependency changed"):
            battle_combat_worker.reviewed_inputs()

    def test_missing_fixture_review_cannot_approve_a_source(self):
        """An absent fixture digest fails closed before compilation."""
        self.review.clear()
        self.write_reviews()
        with self.assertRaisesRegex(ValueError, "source changed"):
            battle_combat_worker.reviewed_inputs()

    def test_changed_restoration_fixture_is_rejected(self):
        """The supplemental class also requires its own reviewed digest."""
        self.restoration.write_bytes(b"changed restoration fixture")
        with self.assertRaisesRegex(ValueError, "source changed"):
            battle_combat_worker.reviewed_inputs()

    def test_missing_restoration_review_is_rejected(self):
        """The older one-fixture review cannot approve an additional executable source."""
        del self.review["restoration_fixture_sha256"]
        self.write_reviews()
        with self.assertRaisesRegex(ValueError, "source changed"):
            battle_combat_worker.reviewed_inputs()
