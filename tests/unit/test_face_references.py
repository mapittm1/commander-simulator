"""Explicit copied faces must resolve without ambiguous starting-name substitutions."""

import tempfile
import unittest
import zipfile
from pathlib import Path

from commander.engine.forge_decks import card_index

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class FaceReferenceTests(unittest.TestCase):
    """Test real script grammar and canonical-name precedence in either archive order."""

    def _index(self, scripts):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "cards.zip"
            with zipfile.ZipFile(path, "w") as archive:
                for number, script in enumerate(scripts):
                    archive.writestr(f"{number}.txt", script)
            return card_index(path)

    def test_preparation_reference_exports_the_preparation_front(self):
        index = self._index(
            [
                "Name:Prepared Creature\nAlternateMode:Prepare\nALTERNATE\nCopyFaceFrom:Spell\n",
                "Name:Spell\n",
            ]
        )
        self.assertEqual(index["Prepared Creature // Spell"], "Prepared Creature")
        self.assertEqual(index["Spell"], "Spell")

    def test_split_copied_front_does_not_override_standalone_card(self):
        scripts = [
            "Name:Bind\n",
            "Name:Seek\n",
            "CopyFaceFrom:Bind\nAlternateMode:Split\nALTERNATE\nCopyFaceFrom:Seek\n",
        ]
        for order in (scripts, list(reversed(scripts))):
            index = self._index(order)
            self.assertEqual(index["Bind"], "Bind")
            self.assertEqual(index["Bind // Seek"], "Bind // Seek")

    def test_specialize_only_exposes_the_explicit_starting_face(self):
        index = self._index(
            ["Name:Original\nAlternateMode:Specialize\nSPECIALIZEW\nName:Specialized\n"]
        )
        self.assertEqual(index["Original"], "Original")
        self.assertNotIn("Specialized", index)

    def test_malformed_or_missing_references_fail_closed(self):
        for scripts in (
            ["Name:Front\nCopyFaceFrom:Other\n"],
            ["Name:Front\nALTERNATE\nCopyFaceFrom:Missing\n"],
        ):
            with self.assertRaises(ValueError):
                self._index(scripts)
