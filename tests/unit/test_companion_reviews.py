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
        engine = read_bounded_json(REVIEWS_PATH.parent / "engine_lock.json")
        self.registry = read_bounded_json(
            REVIEWS_PATH.with_suffix("") / f"{engine['cards_sha256']}.json"
        )
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

    def _archive_paths(self) -> tuple[Path, Path]:
        stack = ExitStack()
        self.addCleanup(stack.close)
        directory = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        policy = directory / "reviews.json"
        policy.write_text(
            json.dumps({"schema_version": 2, "layout": "per_archive"}),
            encoding="utf-8",
        )
        folder = policy.with_suffix("")
        folder.mkdir()
        selected = folder / f"{self.plan['engine']['cards_sha256']}.json"
        selected.write_text(json.dumps(self.registry), encoding="utf-8")
        return policy, selected

    def test_only_selected_archive_is_read_with_many_unrelated_archives(self):
        policy, selected = self._archive_paths()
        for number in range(40):
            unrelated = selected.parent / f"{number:064x}.json"
            self.assertNotEqual(unrelated, selected)
            unrelated.write_text("malformed unrelated archive", encoding="utf-8")
        with patch("commander.engine.companion_reviews.REVIEWS_PATH", policy):
            plan = {**self.plan, "condition_review": condition_review(self.plan)}
            self.assertTrue(condition_matches(plan, self.native))

    def test_missing_selected_archive_cannot_use_a_different_archives_review(self):
        policy, selected = self._archive_paths()
        selected.rename(selected.parent / f"{'0' * 64}.json")
        with patch("commander.engine.companion_reviews.REVIEWS_PATH", policy):
            self.assertIsNone(condition_review(self.plan))
            self.assertFalse(condition_matches(self.plan, self.native))

    def test_both_policy_and_selected_archive_bytes_bind_the_review(self):
        policy, selected = self._archive_paths()
        policy_bytes = policy.read_bytes()
        with patch("commander.engine.companion_reviews.REVIEWS_PATH", policy):
            plan = {**self.plan, "condition_review": condition_review(self.plan)}
            self.assertTrue(condition_matches(plan, self.native))
            policy.write_bytes(policy_bytes + b"\n")
            self.assertFalse(condition_matches(plan, self.native))
            policy.write_bytes(policy_bytes)
            selected.write_bytes(selected.read_bytes() + b"\n")
            self.assertFalse(condition_matches(plan, self.native))

    def test_invalid_archive_identifiers_cannot_select_paths(self):
        policy, _ = self._archive_paths()
        for value in ("../outside", "A" * 64, "0" * 63, None):
            with (
                self.subTest(archive=value),
                patch("commander.engine.companion_reviews.REVIEWS_PATH", policy),
                self.assertRaises(ValueError),
            ):
                condition_review({**self.plan, "engine": {"cards_sha256": value}})

    def test_archive_records_schema_and_entry_count_fail_closed(self):
        policy, selected = self._archive_paths()
        first = self.registry["reviews"][0]
        for data in (
            {"schema_version": 1, "reviews": [{**first, "cards_sha256": "0" * 64}]},
            {"schema_version": 1, "reviews": [first] * 33},
            {"schema_version": True, "reviews": []},
            {"schema_version": 2, "layout": "per_archive"},
            [],
        ):
            selected.write_text(json.dumps(data), encoding="utf-8")
            with (
                self.subTest(data=data),
                patch("commander.engine.companion_reviews.REVIEWS_PATH", policy),
                self.assertRaises(ValueError),
            ):
                condition_review(self.plan)

    def test_archive_policy_cannot_supply_a_storage_path(self):
        policy, _ = self._archive_paths()
        policy.write_text(
            json.dumps(
                {"schema_version": 2, "layout": "per_archive", "directory": "../"}
            ),
            encoding="utf-8",
        )
        with (
            patch("commander.engine.companion_reviews.REVIEWS_PATH", policy),
            self.assertRaises(ValueError),
        ):
            condition_review(self.plan)

    def test_selected_archive_keeps_the_existing_byte_budget(self):
        policy, selected = self._archive_paths()
        with patch("commander.engine.companion_reviews.REVIEWS_PATH", policy):
            selected.write_text(
                json.dumps({"schema_version": 1, "reviews": [], "padding": "x"}),
                encoding="utf-8",
            )
            self.assertIsNone(condition_review(self.plan))
            selected.write_text(
                json.dumps(
                    {"schema_version": 1, "reviews": [], "padding": "x" * (32 * 1024)}
                ),
                encoding="utf-8",
            )
            with self.assertRaises(ValueError):
                condition_review(self.plan)

    def test_symbolic_link_archive_is_rejected_before_reading(self):
        policy, selected = self._archive_paths()
        original = Path.is_symlink

        def redirected(path):
            return path == selected or original(path)

        with (
            patch("commander.engine.companion_reviews.REVIEWS_PATH", policy),
            patch.object(Path, "is_symlink", autospec=True, side_effect=redirected),
            self.assertRaises(ValueError),
        ):
            condition_review(self.plan)

    def test_legacy_registry_can_still_supply_an_exact_review(self):
        policy, _ = self._archive_paths()
        policy.write_text(json.dumps(self.registry), encoding="utf-8")
        with patch("commander.engine.companion_reviews.REVIEWS_PATH", policy):
            plan = {**self.plan, "condition_review": condition_review(self.plan)}
            self.assertIsNotNone(plan["condition_review"])
            self.assertTrue(condition_matches(plan, self.native))
