"""Catalog construction evidence is bounded and never a claim of complete gameplay."""

import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

from commander.engine.catalog_probe import _read_result, evaluate_observations
from commander.engine.catalog_probe_io import read_rows, stream_hash, write_rows
from commander.engine.interactions.suite import write_bounded_json

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class CatalogProbeVerifierTests(unittest.TestCase):
    """Every row must have the exact identity, order and native construction metrics."""

    def setUp(self):
        self.proposal = {
            "card_id": "fixture-identity",
            "name": "Fixture Card",
            "engine_name": "Fixture Card",
            "script_status": "script_present",
            "gap_category": None,
        }
        self.observed = {
            key: self.proposal[key] for key in ("card_id", "name", "engine_name")
        }
        self.observed.update(
            status="constructed",
            pool="common",
            native_name="Fixture Card",
            states=1,
            abilities=1,
            statics=0,
            triggers=0,
            replacements=0,
        )

    def _evaluate(self, observed):
        return evaluate_observations([self.proposal], [observed], 1)

    def test_complete_native_constructor_evidence_passes(self):
        self.assertEqual(self._evaluate(self.observed)["status"], "passed")

    def test_unknown_missing_duplicate_and_reordered_evidence_cannot_pass(self):
        for row in (
            {},
            {**self.observed, "card_id": "other"},
            {**self.observed, "status": "unknown"},
        ):
            with self.subTest(row=row), self.assertRaises(ValueError):
                self._evaluate(row)
        for rows in ([], [self.observed, self.observed]):
            with self.subTest(count=len(rows)), self.assertRaises(ValueError):
                evaluate_observations([self.proposal], rows, 1)
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            evaluate_observations([self.proposal] * 2, [self.observed] * 2, 2)

    def test_missing_and_false_integer_native_metrics_cannot_pass(self):
        for key in ("states", "abilities", "statics", "triggers", "replacements"):
            for value in (None, True, -1, 10001, "1"):
                with (
                    self.subTest(field=key, value=value),
                    self.assertRaises(ValueError),
                ):
                    self._evaluate({**self.observed, key: value})

    def test_real_native_failures_are_failures_and_unavailable_cards_are_gaps(self):
        row = {key: self.observed[key] for key in ("card_id", "name", "engine_name")}
        for status in (
            "missing_native_card",
            "native_identity_mismatch",
            "construction_error",
        ):
            with self.subTest(status=status):
                result = self._evaluate(
                    {
                        **row,
                        "status": status,
                        "error": "native failure",
                        "native_name": "Wrong Card",
                    }
                )
                self.assertEqual(result["status"], "failed")
        self.proposal.update(
            script_status="missing_script",
            engine_name=None,
            gap_category="missing_implementation",
        )
        row.update(status="script_unavailable", engine_name=None)
        self.assertEqual(self._evaluate(row)["status"], "gaps_remain")
        self.proposal["gap_category"] = "non_starting_unreviewed"
        self.assertEqual(self._evaluate(row)["status"], "gaps_remain")
        self.proposal["gap_category"] = "non_starting_meld_result"
        self.assertEqual(self._evaluate(row)["status"], "passed")

    def test_empty_boolean_or_wrong_expected_inventory_cannot_pass(self):
        for count in (0, True, -1, 100001, 2):
            with self.subTest(count=count), self.assertRaises(ValueError):
                evaluate_observations([self.proposal], [self.observed], count)


class CatalogProbeStreamTests(unittest.TestCase):
    """Streaming rows have independent line, record and total-byte limits."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.root = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        self.path = self.root / "rows.jsonl"

    def test_roundtrip_hash_and_existing_evidence_preservation(self):
        rows = [{"card_id": "one"}, {"card_id": "two"}]
        count, digest = write_rows(self.path, rows)
        self.assertEqual(count, 2)
        self.assertEqual(list(read_rows(self.path)), rows)
        self.assertEqual(stream_hash(self.path), digest)
        with self.assertRaises(FileExistsError):
            write_rows(self.path, [])

    def test_oversized_line_duplicate_fields_deep_and_nonobject_records_fail(self):
        for raw in (
            b"x" * 4097,
            b'{"id":1,"id":2}\n',
            b"[]\n",
            b'{"x":' + b"[" * 9 + b"0" + b"]" * 9 + b"}\n",
        ):
            self.path.write_bytes(raw)
            with self.subTest(size=len(raw)), self.assertRaises(ValueError):
                list(read_rows(self.path))

    def test_record_and_total_stream_limits_apply_before_unbounded_work(self):
        self.path.write_bytes(b"{}\n{}\n")
        with (
            patch("commander.engine.catalog_probe_io.MAX_ROWS", 1),
            self.assertRaises(ValueError),
        ):
            list(read_rows(self.path))
        with patch("commander.engine.catalog_probe_io.MAX_STREAM_BYTES", 3):
            with self.assertRaises(ValueError):
                list(read_rows(self.path))
            with self.assertRaises(ValueError):
                stream_hash(self.path)

    def test_generated_rows_are_bounded_before_writing_excessive_data(self):
        with self.assertRaises(ValueError):
            write_rows(self.path, [{"error": "x" * 4096}])
        self.assertEqual(self.path.stat().st_size, 0)


class CatalogProbeReceiptTests(unittest.TestCase):
    """Valid-looking observations cannot hide a failed process or changed receipt."""

    def setUp(self):
        fixture = CatalogProbeVerifierTests()
        fixture.setUp()
        self.proposal, self.observed = fixture.proposal, fixture.observed
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.run = Path(stack.enter_context(tempfile.TemporaryDirectory()))
        write_rows(self.run / "catalog-inputs.jsonl", [self.proposal])
        _, digest = write_rows(self.run / "catalog-observations.jsonl", [self.observed])
        self.plan = {"rows": 1, "engine": {"jar_sha256": "pinned"}}
        self.receipt = {
            "schema_version": 1,
            "plan_sha256": "frozen",
            "engine": self.plan["engine"],
            "rows": 1,
            "observations_sha256": digest,
            "java_version": "17.0.20",
        }

    def _result(self, **process):
        (self.run / "catalog-receipt.json").unlink(missing_ok=True)
        write_bounded_json(self.run / "catalog-receipt.json", self.receipt)
        return _read_result(
            self.run,
            self.plan,
            "frozen",
            {
                "status": "finished",
                "returncode": 0,
                **process,
            },
        )

    def test_complete_receipt_passes_but_failed_or_timed_out_process_does_not(self):
        self.assertEqual(self._result()["status"], "passed")
        for process in ({"returncode": 1}, {"status": "timed_out"}):
            with self.subTest(process=process):
                self.assertEqual(self._result(**process)["status"], "inconclusive")

    def test_changed_missing_or_false_integer_receipt_cannot_pass(self):
        for key, value in (
            ("schema_version", True),
            ("rows", True),
            ("rows", 2),
            ("plan_sha256", "changed"),
            ("engine", {}),
            ("observations_sha256", "changed"),
            ("java_version", "16.0.2"),
        ):
            original = dict(self.receipt)
            self.receipt[key] = value
            with self.subTest(field=key, value=value):
                self.assertEqual(self._result()["status"], "inconclusive")
            self.receipt = original
        (self.run / "catalog-receipt.json").unlink()
        self.assertEqual(
            _read_result(self.run, self.plan, "frozen", {})["status"], "inconclusive"
        )

    def test_mutated_observation_stream_invalidates_receipt(self):
        with (self.run / "catalog-observations.jsonl").open("ab") as stream:
            stream.write(b"{}\n")
        self.assertEqual(self._result()["status"], "inconclusive")
