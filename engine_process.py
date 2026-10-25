"""Bounded process execution for trusted internal engine commands, without a shell."""

import math
import os

# Process creation is limited to internally constructed Java/tool arguments.
import subprocess  # nosec B404
import threading
import time
from dataclasses import dataclass
from pathlib import Path

MAX_LOG_BYTES = 8 * 1024 * 1024


@dataclass(frozen=True)
class ProcessLimits:
    """Internal resource limits and optional minimal child-process environment."""

    timeout: float
    log_bytes: int = MAX_LOG_BYTES
    environment: dict | None = None


def _drain(process, output, state: dict, limit: int) -> None:
    """Consume a pipe in bounded chunks and kill the process if logging overflows."""
    try:
        while chunk := process.stdout.read(65536):
            remaining = limit - state["bytes"]
            output.write(chunk[:remaining])
            state["bytes"] += min(len(chunk), remaining)
            if len(chunk) > remaining:
                state["status"] = "log_limit"
                if process.poll() is None:
                    process.kill()
                return
    except OSError as error:
        state["status"] = "log_error"
        state["error"] = str(error)
        if process.poll() is None:
            process.kill()


def run_bounded(
    command: list[str], cwd: Path, log: Path, limits: ProcessLimits
) -> dict:
    """Run trusted argv only, with a wall timeout, no stdin, and capped output."""
    if (
        not isinstance(limits.timeout, (int, float))
        or isinstance(limits.timeout, bool)
        or not math.isfinite(limits.timeout)
        or not 0 < limits.timeout <= 600
    ):
        raise ValueError("Invalid process timeout")
    if (
        not isinstance(limits.log_bytes, int)
        or isinstance(limits.log_bytes, bool)
        or not 0 < limits.log_bytes <= MAX_LOG_BYTES
    ):
        raise ValueError("Invalid process log limit")
    if (
        not isinstance(command, list)
        or not command
        or any(not isinstance(arg, str) or "\0" in arg for arg in command)
    ):
        raise ValueError("Expected a trusted argument list")
    state = {"status": "finished", "bytes": 0}
    started = time.monotonic()
    with log.open("xb") as output:
        # argv is built by the adapter, never by a decklist or a remote request.
        with subprocess.Popen(  # nosec B603
            command,
            cwd=cwd,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            shell=False,
            env=limits.environment,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        ) as process:
            reader = threading.Thread(
                target=_drain,
                args=(process, output, state, limits.log_bytes),
                daemon=True,
            )
            reader.start()
            try:
                process.wait(timeout=limits.timeout)
            except subprocess.TimeoutExpired:
                state["status"] = "timeout"
                process.kill()
                process.wait(timeout=10)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=10)
                reader.join(timeout=10)
            if reader.is_alive():
                raise RuntimeError("Engine log reader did not stop")
            state["returncode"] = process.returncode
    state["elapsed_seconds"] = round(time.monotonic() - started, 3)
    return state
