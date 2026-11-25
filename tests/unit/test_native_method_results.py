"""Native receipt identifiers must distinguish methods in different test classes."""

import unittest
from pathlib import Path
from unittest.mock import Mock

from tests.support.draw_ending_worker import _results
from tests.support.packaged_rules import class_origin


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


class NativeClassOriginTests(unittest.TestCase):
    """Resolve private binary identities directly and reject unverifiable origins."""

    def setUp(self):
        self.java = Mock()
        self.loaded = Mock()
        self.loaded.getName.return_value = "forge.ai.Owner$PrivateHelper"
        self.reflection = Mock()
        self.reflection.forName.return_value = self.loaded
        self.file_type = Mock()
        self.file_type.return_value.getCanonicalPath.return_value = str(
            Path("verified-engine.jar").absolute()
        )
        self.java.JClass.side_effect = {
            "java.lang.Class": self.reflection,
            "java.io.File": self.file_type,
        }.__getitem__

    def test_private_binary_uses_its_own_reflected_class(self):
        """A bridge wrapper cannot substitute its class_ attribute for the binary."""
        observed = class_origin(self.java, "forge.ai.Owner$PrivateHelper")
        self.assertEqual(observed, Path("verified-engine.jar").absolute())
        self.reflection.forName.assert_called_once_with("forge.ai.Owner$PrivateHelper")

    def test_different_reflected_identity_is_rejected(self):
        """A valid-looking archive cannot compensate for the wrong loaded class."""
        self.loaded.getName.return_value = "forge.ai.Owner"
        with self.assertRaisesRegex(ValueError, "identity"):
            class_origin(self.java, "forge.ai.Owner$PrivateHelper")
        self.file_type.assert_not_called()

    def test_missing_code_source_is_rejected(self):
        """Classes without an archive/directory origin cannot satisfy verification."""
        self.loaded.getProtectionDomain.return_value.getCodeSource.return_value = None
        with self.assertRaisesRegex(ValueError, "code source"):
            class_origin(self.java, "forge.ai.Owner$PrivateHelper")
        self.file_type.assert_not_called()
