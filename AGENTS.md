# Repository workflow

- Follow CONTRIBUTING.md for readable, secure code and validation standards.
- Use this checkout as the single active source tree. Verify the working directory
  before editing; do not create project or temporary development files in Downloads.
- Put application modules in commander/, configuration in config/, tests in tests/,
  trusted maintenance tools in tools/, and reviewed native patches/tests in engine/.
- Keep temporary work in ignored data/development/ and preserved old workspaces in
  ignored data/local-archive/. Do not merge archived duplicate modules into source.
- Update README.md and CHANGELOG.md with behavior, validation and material limits.
- Run the pinned Black/isort/Pylint/Bandit/pytest gate through tools/check.py.
  Use separate opt-in native tests for changed engine behavior. Script presence or
  card construction alone does not establish full rules support.
