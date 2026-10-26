"""Disposable fixtures keep ordinary legality gates and reject malformed basic counts."""

import unittest
from contextlib import closing

from database import connect
from engine_fixtures import legal_fixture_pod

# Test names express behavior.
# pylint: disable=missing-function-docstring


class FixtureBoundaryTests(unittest.TestCase):
    """Malformed counts and active source transactions are never accepted as fixtures."""

    def test_invalid_basic_configurations_are_rejected(self):
        with closing(connect(":memory:")) as source:
            for lands in (
                {},
                {"Forest": True},
                {"Forest": 98},
                {"Forest": "99"},
                {"Sol Ring": 99},
                {"Forest": 100},
                {"Forest": 98, "Island": -1},
            ):
                with (
                    self.subTest(lands=lands),
                    self.assertRaisesRegex(ValueError, "99 basic"),
                ):
                    with legal_fixture_pod(source, {"Leader": lands}):
                        self.fail("Invalid fixture was admitted")

    def test_active_source_transaction_is_preserved(self):
        with closing(connect(":memory:")) as source:
            source.execute("BEGIN")
            with self.assertRaisesRegex(ValueError, "active transaction"):
                with legal_fixture_pod(source, {"Leader": "Forest"}):
                    self.fail("Active transaction was accepted")
            self.assertTrue(source.in_transaction)

    def test_missing_catalog_cannot_be_promoted_by_fixture_metadata(self):
        with closing(connect(":memory:")) as source:
            with self.assertRaises(ValueError):
                with legal_fixture_pod(source, {"Leader": "Forest"}):
                    self.fail("Missing source catalog was accepted")
            self.assertEqual(
                source.execute("SELECT COUNT(*) FROM imports").fetchone()[0], 0
            )
