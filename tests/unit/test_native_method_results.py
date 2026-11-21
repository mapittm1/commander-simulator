"""Native receipt identifiers must distinguish methods in different test classes."""

import unittest
from unittest.mock import Mock

from tests.support.draw_ending_worker import _results


class NativeMethodResultTests(unittest.TestCase):
    """Keep duplicate short method names and all failure/skip outcomes visible."""

    def test_same_method_name_in_different_classes_keeps_distinct_results(self):
        """Class-qualified IDs preserve every result without weakening the totals."""
        first = self._result("forge.ai.FirstTest", "sameMethod")
        second = self._result("forge.ai.SecondTest", "sameMethod")
        failed = self._result("forge.ai.FailingTest", "sameMethod")
        skipped = self._result("forge.ai.SkippedTest", "sameMethod")
        listener = Mock()
        listener.getPassedTests.return_value = [first, second]
        listener.getFailedTests.return_value = [failed]
        listener.getSkippedTests.return_value = [skipped]

        counts, rows = _results(listener)

        self.assertEqual(counts, {"total": 4, "passed": 2, "failed": 1, "skipped": 1})
        self.assertEqual(
            {row["method"]: row["status"] for row in rows},
            {
                "forge.ai.FirstTest.sameMethod": "PASS",
                "forge.ai.SecondTest.sameMethod": "PASS",
                "forge.ai.FailingTest.sameMethod": "FAIL",
                "forge.ai.SkippedTest.sameMethod": "SKIP",
            },
        )
        self.assertEqual(len({row["method"] for row in rows}), 4)
        self.assertEqual(rows, sorted(rows, key=lambda row: row["method"]))

    @staticmethod
    def _result(class_name: str, method_name: str) -> Mock:
        result = Mock()
        result.getTestClass.return_value.getName.return_value = class_name
        result.getMethod.return_value.getMethodName.return_value = method_name
        return result
