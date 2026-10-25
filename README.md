# Commander Simulator

A four-player Commander heuristic simulator, requiring Python 3.10 or newer.
It uses only Python's standard library.

## Run

From this project directory:

```powershell
python -m commander_simulator --games 3000 --seed 20261025 --output-dir results
```

The original `python b3_matchup_simulator_clean.py` command also works.
CSV exports go to the current working directory unless `--output-dir` is provided.
Existing CSV files in that output directory are overwritten.

Optional installation: `python -m pip install .`, then run `commander-simulator`.
The package includes its card configuration and deck lists.

If Windows' `python` command is unavailable, use an installed Python interpreter's
full path. The bundled interpreter used for validation in this workspace is:
`C:/Users/mapit/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`.
In PowerShell invoke a quoted executable path with `&`.

## Project layout

- `commander_simulator/data/`: JSON configuration and UTF-8 Moxfield deck lists.
- `settings.py`: loads named card classifications and configuration.
- `cards.py`: card cost heuristics.
- `decks.py`: deck loading and validation.
- `player.py`: player state, drawing, mana, and board evaluation.
- `phases.py`: mana development, casting, archetype engines, interaction, combat.
- `simulation.py`: game loop, elimination, metrics, and event capture.
- `cli.py`: command-line arguments, batch execution, and CSV exports.
- `test_b3_refactor.py`: regression and CLI checks.

Module paths above are relative to `commander_simulator/` unless otherwise noted.
The two original Python filenames remain compatibility entry points/imports.

## Configuration

Edit `commander_simulator/data/simulator_config.json` to change deck filenames,
commander names and costs, card classifications, special card groups, and output
filenames. Deck paths are relative to the data directory, independent of the
working directory. Adding an archetype also requires implementing its behavior
in `phases.py`.

## Tests

```powershell
python test_b3_refactor.py
```

No test dependency is required. The functions also work with pytest if installed.
All five checks pass, covering deck loading, card costs, invalid turn limits,
CSV exports from another directory, and exact regression hashes for 300 seeded
games including event logs. Those hashes were captured before the reorganization.
CLI help was checked for both entry points. Package installation has not been tested.

## Saved checkpoint

`.backups/before-project-rework.zip` preserves the source, configuration, tests,
documentation, and four deck lists as they were before the project reorganization.
This folder is not a Git repository.

## Model limitations

Mana, spell effects, targeting, combat, and death triggers are approximations.
This is not a full Magic rules engine, and results do not provide reliable
real-world Commander matchup win rates.
