"""Display wording differences require exact identity-bound review evidence."""

import json
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

from commander.engine.companion_reviews import (
    REVIEWS_PATH,
    condition_matches,
    condition_review,
)
from commander.engine.interactions.suite import read_bounded_json

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class CompanionWordingReviewTests(unittest.TestCase):
    """A grammatical review cannot approve changed predicates, identities or Oracle text."""

    def setUp(self):
        self.registry = read_bounded_json(REVIEWS_PATH)
        review = self.registry["reviews"][0]
        self.plan = {
            "engine": {"cards_sha256": review["cards_sha256"]},
            "companion": {"card_id": review["oracle_id"]},
            "condition_text": review["oracle_condition"],
        }
        self.native = review["native_condition"]
        self.plan["condition_review"] = condition_review(self.plan)

    def test_only_the_exact_reviewed_predicate_and_identity_can_match(self):
        self.assertTrue(condition_matches(self.plan, self.native))
        for value in (self.native + " ", self.native.replace("cmcM20", "cmcM21")):
            self.assertFalse(condition_matches(self.plan, value))
        for changed in (
            {**self.plan, "companion": {"card_id": "other"}},
            {**self.plan, "engine": {"cards_sha256": "0" * 64}},
            {**self.plan, "condition_text": "Changed condition."},
            {**self.plan, "condition_review": None},
        ):
            with self.subTest(changed=changed):
                self.assertFalse(condition_matches(changed, self.native))

    def test_registry_duplicates_false_schema_and_bad_checksums_fail_closed(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        path = Path(stack.enter_context(tempfile.TemporaryDirectory())) / "reviews.json"
        for registry in (
            {"schema_version": True, "reviews": []},
            {"schema_version": 1, "reviews": [self.registry["reviews"][0]] * 2},
            {
                "schema_version": 1,
                "reviews": [{**self.registry["reviews"][0], "cards_sha256": "wrong"}],
            },
        ):
            path.write_text(json.dumps(registry), encoding="utf-8")
            with (
                self.subTest(registry=registry),
                patch("commander.engine.companion_reviews.REVIEWS_PATH", path),
                self.assertRaises(ValueError),
            ):
                condition_review(self.plan)

    def test_changed_registry_bytes_invalidate_an_existing_review(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        path = Path(stack.enter_context(tempfile.TemporaryDirectory())) / "reviews.json"
        # Change the bytes while keeping even a near-limit registry within its read budget.
        path.write_text(
            json.dumps(self.registry, separators=(",", ":")), encoding="utf-8"
        )
        with patch("commander.engine.companion_reviews.REVIEWS_PATH", path):
            self.assertFalse(condition_matches(self.plan, self.native))
