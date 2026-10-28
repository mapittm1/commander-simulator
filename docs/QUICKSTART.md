# Run the Commander Simulator CLI

Run these commands from the repository root in PowerShell. The existing local
installation includes `.venv`, a populated catalog, and the pinned engine.
For a fresh checkout, follow the engine/catalog installation sections in
[README](../README.md) first. No web server is part of this milestone.

## 1. Verify readiness and run a real game

```powershell
.\.venv\Scripts\python.exe commander_simulator.py doctor
.\.venv\Scripts\python.exe commander_simulator.py demo --runs 1
```

`doctor` independently checks catalog freshness, engine/script hashes, and Java.
Missing prerequisites return exit code 1 and a next step; it does not download
assets automatically. Refresh stale catalog data with `sync`.

`demo` defaults to one real game with a 60-second engine clock. It uses four
diagnostic decks containing one commander and 99 Forests each. These are software
fixtures, not representative competitive opponents or bracket recommendations.
The main deck library is untouched. Its database and report are preserved under
`data/demos/GENERATED_ID/`; game logs remain under `data/runs/`.

The console prints results, report path, database path, and batch ID. JSON reports
include wins by original pod order, failure/interruption counts, exact engine
evidence, observed diagnostic coverage, and links to the first 20 attempt artifacts.
The database retains all attempts; `batch-results` pages the rest.

## 2. Prepare your deck and three opponents

Save four UTF-8 text files. Each must include its Commander section; partner
commanders appear on separate lines. Quantities include commanders in the
100-card deck total. Plain quantity/name text is supported; other deck-site file
formats need conversion to this supported format.

```text
Commander
1 Your Commander Name

Mainboard
1 Sol Ring
...the rest of your real deck...
```

The placeholder above is not a valid runnable deck. See
[`examples/decklist-format.txt`](../examples/decklist-format.txt) for a complete,
clearly labeled diagnostic-format example. Your deck and all opponents must pass
current Commander legality, a shared bracket review, and engine export coverage.
Unsupported cards cause a clear error; they are never silently substituted.

Copy `examples/bracket_review.example.json` to your own `pod-review.json`.
Enter your reviewer name and rationale, read the bracket guidance, and set a
check to `true` only when you affirm it for **each of the four decks**. The
template deliberately contains false checks and cannot approve a pod unchanged.
If decks need different reviewers or rationales, use the existing `deck-add` and
`bracket-review` commands individually, then use `simulate` below.

Start with one game:

```powershell
.\.venv\Scripts\python.exe commander_simulator.py play --deck my-deck.txt --deck opponent-a.txt --deck opponent-b.txt --deck opponent-c.txt --bracket 3 --review-file pod-review.json --runs 1
```

Your deck is first in the pod. Each named file must be a complete legal list.
All four legality and script checks run before importing any deck. Imported legal
revisions can remain in the library if a subsequent bracket/count/freshness check
fails; no simulation starts until every gate passes. A supplied review is an
explicit human attestation, not an automated certification of power level.

The command prints the saved `deck_id:version` references. To reuse them without
importing duplicate decks, substitute your actual references into:

```powershell
.\.venv\Scripts\python.exe commander_simulator.py simulate --seat 1:1 --seat 2:1 --seat 3:1 --seat 4:1 --runs 100
```

The numbers above are examples, not guaranteed IDs. `decks` lists your saved
decks. `play` and new `simulate` batches default to 100 attempts; `demo` defaults
to one. Use `--runs` to choose 1–100,000; actual duration depends on decks, engine,
and game clocks. Seats rotate and seeds are retained. These commands run the
whole remaining batch in the foreground, with progress after each saved attempt.

## 3. Stop, resume, and inspect

Ctrl+C stops the foreground engine safely and preserves the job and attempts.
It returns exit code 130 and saves a report when interruption occurs during the
run. An interrupted attempt remains counted as interrupted; resume does not
retry it or hide it from the final summary.

```powershell
.\.venv\Scripts\python.exe commander_simulator.py simulate --resume JOB_ID
.\.venv\Scripts\python.exe commander_simulator.py batch-show JOB_ID
.\.venv\Scripts\python.exe commander_simulator.py batch-results JOB_ID --offset 0 --limit 20
.\.venv\Scripts\python.exe commander_simulator.py batch-diagnostics JOB_ID
```

For a demo, put `--database "PATH_PRINTED_BY_DEMO"` before the subcommand. The
same rule applies to any custom database. Resume keeps frozen run count, seed,
clock, deck revisions, reviews, catalog, and engine evidence; do not pass new
`--runs`, `--seed`, or `--game-clock` options. Stale evidence can block resume
and requires a fresh valid batch. An already completed batch never reruns games.

From a second terminal, `batch-cancel JOB_ID` requests cooperative cancellation.
The active game can finish within its limits. Hard-crash claim recovery remains
pending; jobs with an existing worker claim are never automatically stolen.

Default reports use generated filenames. `--output PATH` selects another local
report file; existing files are never overwritten. `--json` prints only the final
JSON on stdout and sends progress to stderr, so output can be redirected. Exit
code 0 means the requested batch completed without failures or interruptions;
otherwise the command returns nonzero. A report-write failure leaves persisted
batch history inspectable through the database.

## What the results currently mean

To inspect the 36 controlled Equipment, damage, countered-trigger, target-legality,
protection/hexproof/shroud, replacement-order, and paid-casting cases, install
`requirements-interactions.txt` and provide a trusted local JDK 17+ compiler.
`javac` is discovered on PATH; set `COMMANDER_JAVAC` to its executable if needed
(for example, `$env:COMMANDER_JAVAC = 'C:\Path\To\JDK\bin\javac.exe'`).
Run `engine-interactions`. The command runs all seven fixture families and saves `observations-equipment.json`,
`observations-damage.json`, `observations-countering.json`,
`observations-targeting.json`, `observations-protection.json`, and
`observations-replacement_order.json`, plus `observations-casting.json`. The combined
`interaction-report.json` includes every check and a `partition_results` summary.
This is a separate direct-state diagnostic, not a pod simulation or a full card-support
certificate. Casting checks pay costs natively, but set phase/priority snapshots
explicitly; they do not certify the full priority-pass loop. See
[INTERACTION_TESTS.md](INTERACTION_TESTS.md).

Wins and observations measure this fixed pod under Forge's AI pilots. Reported
land plays, turns without logged land plays, casts, and attack assignments are
observations. They do not yet diagnose hidden mana problems or explain why human
players would focus a deck. EDHREC salt analysis remains available separately
through `deck-salt`, with source coverage and unknowns preserved.

Automatic acquisition/ranking of the requested 50 representative opponents,
comprehensive card/rules conformance, deeper optimization diagnostics, crash
recovery, and the self-hosted web UI remain future milestones.


## Sticker sheets and Attractions

The same import file can include `Stickers` and `Attractions` sections. List one
exact catalog name per line, prefixed with `1`. These selections are outside the
100 starting cards; each selected section requires at least ten unique entries.
Use only Commander-legal Attractions. The engine randomly selects three of the
listed sticker sheets each game and shuffles the Attraction deck. Lists are
frozen into the deck revision and its hash. See [card coverage](CARD_COVERAGE.md)
for native test evidence, explicit-printing limitations and remaining pregame
options. A Sideboard heading does not select a companion.
