# Engineering standards

Readable, maintainable, secure code is a project requirement. These standards
apply to every change, including generated code and future engine integrations.

## Readability and design

- Use descriptive names and conventional Python formatting. Prefer explicit
  steps over clever expressions, nested comprehensions, or hidden side effects.
- Keep parsing, validation, persistence, networking, simulation, and presentation
  separate. Break large routines into named operations as they grow.
- Document public interfaces and non-obvious decisions. Comments explain why;
  avoid comments that merely repeat the code.
- Add type hints to new public interfaces and expand coverage when changing
  existing interfaces. Keep configuration and domain data outside gameplay logic.
- Preserve stable identifiers and explicit units. Distinguish declared brackets,
  validated rules, unsupported mechanics, and simulation results.
- Prefer the standard library where it fits. Justify new dependencies and pin
  the versions used for reproducible simulation runs.

## Input and persistence

- Treat uploads, deck names, external decklists, API responses, and rules text
  as data. Never execute them or use eval, exec, pickle, or shell commands to
  interpret them.
- Validate at boundaries. Bound file size, line count, name length, quantities,
  network transfers, decompression, and job duration as each input path is built.
- Use SQL parameters for values. SQL identifiers must come from fixed internal
  definitions, never uploaded values. Use foreign keys and atomic transactions.
- Resolve cards by stable identifiers. Reject ambiguous names and preserve the
  source of imported data. Do not silently substitute unknown cards.
- A failed import must not leave a partly updated catalog or deck.

## Networking and engine execution

- Verify TLS. Use timeouts and approved provider endpoints. Validate redirects
  and final destinations before building any user-supplied URL importer.
- Do not fetch arbitrary upload URLs or local filesystem paths on behalf of
  remote users. Use provider-specific adapters with explicit access controls.
- Keep credentials out of code, Git, logs, deck files, and URLs.
- Execute the engine using an argument list with shell=False. Use a
  pinned engine version, isolated run directories, resource limits, and a timeout.
- Never derive executable commands or output paths directly from deck names.
- Future web uploads must use generated storage identifiers, escaped rendering,
  per-user authorization, and job limits before the service is exposed.

## Testing and review

Keep README.md and CHANGELOG.md current with project changes. Document affected
commands, behavior, limits, validation, and known gaps in the same change.
Add changelog notes under Unreleased until a release is explicitly made; do not
invent release versions or dates. Preserve earlier history.

Use Python 3.12 for development tooling and CI (the application targets Python
3.10+). Install the pinned tools and commit hook once per checkout:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pre_commit install
```

Run the full gate before committing:

```powershell
.\.venv\Scripts\python.exe tools/check.py
```

Apply Black/isort formatting, then run the same gate:

```powershell
.\.venv\Scripts\python.exe tools/check.py --fix
```

The gate checks all project Python files recursively, excluding `.git`, `.venv`,
`data`, and bytecode caches. It runs isort with the Black profile, Black, Pylint,
Bandit, and the offline unit tests through pytest. Any failed step returns a nonzero exit code.
The installed pre-commit hook runs the same gate in an isolated environment;
GitHub Actions runs it on every push and pull request. The hook must be installed
in each new checkout. Requiring the CI check for merges additionally requires a
GitHub branch protection/ruleset setting; this has not been changed.

Keep suppressions narrow and explain them. Black owns line wrapping; test method
names describe behavior instead of duplicating names in docstrings. The urllib
redirect callback retains its required inherited signature. Targeted Bandit
annotations cover the trusted quality runner and engine adapter's internal
subprocess arguments. Engine paths are trusted operator configuration;
deck contents cannot supply commands or output locations.
No blanket security-check suppression or Pylint score threshold is used.

Add meaningful tests for changed behavior: valid inputs, malformed inputs, limits, rollback, and stable identifiers.
Keep ordinary tests offline; separate live provider and engine checks.

Review diffs for readability, unintended file changes, secrets, input validation,
transaction boundaries, and error handling. Report what was tested and what
remains unverified. Do not claim a security audit or full rules compliance from
passing unit tests.

The CLI currently accepts local text files, not remote HTTP uploads. The database
is local, and there is no public server yet. The external Forge adapter uses
bounded logs/time, a minimal environment, and generated run directories.
It is not an OS sandbox. Server isolation, hard quotas, authentication, and
remote-upload requirements remain deployment gates.
Automated formatting, linting, security scanning, and CI are configured;
static type checking remains a future addition.

## Running unit tests with pytest

```powershell
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe -m pytest -v
.\.venv\Scripts\python.exe -m pytest tests/unit/test_legality.py
.\.venv\Scripts\python.exe -m pytest -k stale
```

Pytest discovers the existing unittest.TestCase suites, including their setup,
teardown, and cleanup behavior. They do not need to be rewritten to use the new
runner. New tests can use pytest fixtures or parametrization where useful.

Discovery, ignored data/environment directories, and strict marker/config checks
are configured in pyproject.toml. Mark future live provider/engine tests with
`integration` and run them separately; ordinary tests must remain offline.
The shared quality runner, installed commit hook, and GitHub Actions all use the
same pinned pytest version from the development dependencies.
