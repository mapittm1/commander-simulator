"""Shared native-worker source identity must survive every bounded invocation."""

import unittest
from contextlib import contextmanager, nullcontext
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from commander.engine.forge_config import file_hash
from tests.support.native_worker import WorkerInvocation, run_test_worker


@contextmanager
def temporary_workspace():
    """Keep the temporary workspace alive through the unittest cleanup context."""
    with TemporaryDirectory() as directory:
        yield Path(directory)


class NativeWorkerIdentityTests(unittest.TestCase):
    """Detect changed shared execution code as well as changed entry-point code."""

    def setUp(self):
        self.run_directory = self.enterContext(temporary_workspace())
        self.worker = self.run_directory / "worker.py"
        self.support = self.run_directory / "support.py"
        self.worker.write_text("# fixed worker", encoding="utf-8")
        self.support.write_text("# fixed support", encoding="utf-8")
        patch(
            "tests.support.native_worker._engine_profile", return_value=nullcontext()
        ).start()
        self.enterContext(
            patch("tests.support.native_worker._environment", return_value={})
        )
        self.process = self.enterContext(
            patch("tests.support.native_worker.run_bounded")
        )
        self.process.return_value = {"status": "finished", "returncode": 0}

    def execute(self, support=()):
        """Use temporary trusted fixtures without starting a JVM."""
        settings = type("Settings", (), {"home": self.run_directory})()
        return run_test_worker(
            settings, self.run_directory, self.worker, "native.log", support
        )

    def test_unchanged_shared_sources_are_recorded(self):
        """Record the shared execution-source identities for a completed worker."""
        result, worker_hash = self.execute((self.support,))
        self.assertEqual(worker_hash, file_hash(self.worker))
        self.assertEqual(
            result["support_source_hashes"],
            {str(self.support): file_hash(self.support)},
        )

    def test_changed_support_source_is_rejected(self):
        """Reject changes to execution code even when the entry point stays fixed."""

        def change_support(*_args):
            self.support.write_text("# changed support", encoding="utf-8")
            return {"status": "finished", "returncode": 0}

        self.process.side_effect = change_support
        with self.assertRaisesRegex(ValueError, "support source changed"):
            self.execute((self.support,))

    def test_changed_entry_point_is_rejected(self):
        """Retain the existing entry-point identity guard."""

        def change_worker(*_args):
            self.worker.write_text("# changed worker", encoding="utf-8")
            return {"status": "finished", "returncode": 0}

        self.process.side_effect = change_worker
        with self.assertRaisesRegex(ValueError, "worker changed"):
            self.execute((self.support,))

    def test_existing_callers_can_omit_shared_sources(self):
        """Keep the previous caller contract when no extra sources are selected."""
        result, worker_hash = self.execute()
        self.assertEqual(result["returncode"], 0)
        self.assertEqual(worker_hash, file_hash(self.worker))
        self.assertEqual(result["support_source_hashes"], {})

    def test_fixed_worker_arguments_keep_existing_timeout(self):
        """Forward trusted group selection as argv with the same process budget."""
        settings = type("Settings", (), {"home": self.run_directory})()
        run_test_worker(
            settings,
            self.run_directory,
            WorkerInvocation(self.worker, ("information",)),
            "native.log",
        )
        command, _, _, limits = self.process.call_args.args
        self.assertEqual(command[-2:], [str(self.run_directory), "information"])
        self.assertEqual(limits.timeout, 90)
