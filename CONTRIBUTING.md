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
- Execute the future engine using an argument list with shell=False. Use a
  pinned engine version, isolated run directories, resource limits, and a timeout.
- Never derive executable commands or output paths directly from deck names.
- Future web uploads must use generated storage identifiers, escaped rendering,
  per-user authorization, and job limits before the service is exposed.

## Testing and review

Run `python -m unittest -v` before committing. Add meaningful tests for changed
behavior: valid inputs, malformed inputs, limits, rollback, and stable identifiers.
Keep ordinary tests offline; separate live provider and engine checks.

Review diffs for readability, unintended file changes, secrets, input validation,
transaction boundaries, and error handling. Report what was tested and what
remains unverified. Do not claim a security audit or full rules compliance from
passing unit tests.

The CLI currently accepts local text files, not remote HTTP uploads. The database
is local, and there is no public server or executable game engine yet. Security
requirements above are gates for those future features, not claims that they
already exist. Automated linting/type checking and CI remain to be configured.
