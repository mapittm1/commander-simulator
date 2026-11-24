"""Fail closed when packaged rule partitions lose, duplicate or invent test classes."""

import unittest
from unittest.mock import patch

from tests.support.draw_ending_partitions import (
    EXPECTED_COUNTS,
    PARTITIONS,
    select_partition,
    validate_partitions,
)
from tests.support.draw_ending_worker import TEST_CLASSES


class DrawEndingPartitionTests(unittest.TestCase):
    """Keep every registered fixture in exactly one fixed worker group."""

    def test_current_registry_is_exhaustive_and_disjoint(self):
        """Validate the actual complete registry and total expected case count."""
        validate_partitions(TEST_CLASSES)
        self.assertEqual(sum(EXPECTED_COUNTS.values()), 2742)
        for name, classes in PARTITIONS.items():
            self.assertEqual(select_partition(name, TEST_CLASSES), classes)

    def test_omitted_class_is_rejected(self):
        """A smaller class list cannot become an accepted passing gate."""
        name = next(iter(PARTITIONS))
        with patch.dict(PARTITIONS, {name: PARTITIONS[name][1:]}):
            with self.assertRaisesRegex(ValueError, "exactly once"):
                validate_partitions(TEST_CLASSES)

    def test_duplicate_class_is_rejected(self):
        """A repeated class cannot hide a lost case behind a total count."""
        first, second = list(PARTITIONS)[:2]
        repeated = PARTITIONS[second] + (PARTITIONS[first][0],)
        with patch.dict(PARTITIONS, {second: repeated}):
            with self.assertRaisesRegex(ValueError, "exactly once"):
                validate_partitions(TEST_CLASSES)

    def test_unregistered_substitution_is_rejected(self):
        """Replacing a fixture with a different class preserves count but fails identity."""
        name = next(iter(PARTITIONS))
        substituted = ("unregistered.Test",) + PARTITIONS[name][1:]
        with patch.dict(PARTITIONS, {name: substituted}):
            with self.assertRaisesRegex(ValueError, "exactly once"):
                validate_partitions(TEST_CLASSES)

    def test_empty_group_is_rejected(self):
        """Do not launch an apparently successful empty TestNG group."""
        name = next(iter(PARTITIONS))
        with patch.dict(PARTITIONS, {name: ()}):
            with self.assertRaisesRegex(ValueError, "exactly once"):
                validate_partitions(TEST_CLASSES)

    def test_repeated_registry_entry_is_rejected(self):
        """Reject corruption in the source registry as well as in its partition."""
        with self.assertRaisesRegex(ValueError, "exactly once"):
            validate_partitions(TEST_CLASSES + (TEST_CLASSES[0],))

    def test_arbitrary_group_or_class_selection_is_rejected(self):
        """The worker accepts only reviewed internal group names."""
        for name in ("", "forge.ai.SomeOtherTest", "../other"):
            with self.subTest(name=name):
                with self.assertRaisesRegex(ValueError, "Unknown fixed"):
                    select_partition(name, TEST_CLASSES)
