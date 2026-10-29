# Reviewed native engine build

Forge is GPL-licensed. See [LICENSE.Forge](LICENSE.Forge). This directory retains
the source modifications and regression source used by the reviewed Commander
engine. The unmodified source is pinned by commit and archive SHA-256 in
[`sticker_recipe.json`](sticker_recipe.json); the Wicker Picker patch is pinned
to an immutable upstream pull-request head. Local regression fixes are a second
patch, applied after the upstream patch. Two further patches preserve chosen
commander colors through native/AI state and refresh companion pregame types.
A fifth patch preserves supplementary zones in AI game copies. A sixth preserves
applied sticker state in AI and last-known-information copies, maps sheets to
the copied game and tracks used slots across control changes. A seventh patch
preserves historical LKI and zone-entry records, independent damage history and
shared mapped combat bands. An eighth patch preserves damage provenance and
receipt histories. A ninth preserves registered participant mappings and active/lost
lists without replaying elimination, restoring life before loss outcomes.
A tenth patch preserves registered and live starting-life baselines. All ten
patches are pinned. No remote branch is used at build time.

Download the two archives from the exact URLs in the recipe, install a trusted
JDK 17+ and Java runtime, then run from the repository root:

```powershell
.\.venv\Scripts\python.exe tools/build_sticker_engine.py `
  --source-archive PATH_TO_FORGE_SOURCE_ZIP `
  --maven-archive PATH_TO_MAVEN_ZIP `
  --jdk PATH_TO_INSTALLED_JDK `
  --runtime PATH_TO_TRUSTED_JAVA_EXECUTABLE
```

The tool checks source, Maven, patch and regression-source hashes before building.
It extracts into a fresh generated directory under `data/engine-builds/`, rejects
archive traversal/symlinks and bounds extracted member counts and sizes. It runs
140 native sticker, chosen-color, companion, supplementary-copy, history,
damage, participant and starting-life tests.
It packages a standalone runtime only when all pass without skips. It disables only the optional Windows GUI launcher packaging;
the native engine is unchanged by that build setting. Maven needs network access
to resolve the pinned source project's dependencies; transitive plugin and
dependency artifacts are not independently locked by this Python recipe.

The build preserves the complete extracted source, logs, test reports, runtime,
license, exact `engine-lock.json` and `engine-config.json`. Maven runs in the
bounded build JVM with test forking disabled. Administrator-selected source and
plugins execute trusted code; this is not a web/upload feature or an OS sandbox.

Before selecting a newly built runtime, run:

```powershell
python commander_simulator.py engine-check --engine-config BUILD_DIRECTORY/engine-config.json
python commander_simulator.py engine-card-probe --engine-config BUILD_DIRECTORY/engine-config.json
python commander_simulator.py engine-interactions --engine-config BUILD_DIRECTORY/engine-config.json
```

Also run the opt-in engine/CLI integration tests with that candidate selected in
the local `config/engine_config.json`. The build output config uses paths relative
to its own directory; moving that config requires updating those paths. JARs can
differ across builds because of embedded metadata. Never substitute a checksum
from a different build. The deterministic card ZIP has no loose-script overrides;
engine verification rejects extra entries in `res/cardsfolder/`.

See [card support and evidence](../docs/CARD_COVERAGE.md) for the distinction
between constructor coverage, controlled rules checks and full gameplay support.


The recipe pins nine regression source files and exactly three card-script
overrides: Wicker Picker, The Prismatic Piper and Faceless One. The initial recipe
is saved with the build; inputs are rechecked after compilation and packaging.
Locks pin both the Java executable and the JVM library, in addition to the JAR
and card ZIP, plus the bounded complete edition metadata inventory. Edition
filenames and bytes use a portable ordered fingerprint; the default lock pins
686 files verified against immutable source. The broader 688 gameplay and 256
serialization checks are recorded
in `native_test_evidence.json`; the 140 focused tests are included in the 688.


The supplementary-zone regression checks real independent AI copies rather than
infer support from loaded card names. Explicit Attraction preflight and all 87
printing/export/AI-copy integration cases also pass. Runtime promotion preserves
historical locks. The current build's edition pin was independently verified
against the immutable source archive, retaining the original generated lock and
review receipt locally; new builds include this pin directly in their lock.

Applied-sticker regressions directly place stickers to isolate state-copy behavior
from legal placement, ticket payment and target selection. Normal Clone effects
must not acquire physical stickers; AI game snapshots and LKI records preserve
different information. The focused tests distinguish those cases and verify a
real paid spell through stack copy and resolution. Snapshot fidelity does not
certify every ability's effect, historical zone-change LKI or AI decision.

The packaged runtime also passes state-fidelity checks for all 480 stickers on
48 Commander-legal sheets in three public zones. Fixed workers preserve bounded
receipts, physical-record hashes and exact Java object identity. The aggregate
[`sticker_state_evidence.json`](sticker_state_evidence.json) records these checks
and the corrected fixture rerun without treating state fidelity as ability proof.

The history patch additionally passes fourteen native cases and packaged-runtime
witnesses for four historical zone transitions and two actual paid Tragic Slip
outcomes. It copies recorded old characteristics and turn-zone entry records,
retains distinct incarnations, isolates mutable damage history, preserves mapped
combat-band identity and resets caches before reusing a copier. Copy failures
propagate rather than dropping history silently. See
[`history_state_evidence.json`](history_state_evidence.json) for the bound evidence
and explicit limits; other historical registries, arbitrary linked abilities,
damage triggers and post-elimination states still require further validation.

The damage patch preserves immutable packet identity, historical source/target
records, live/global source-history sharing and independent current/previous-turn
receipts. Nineteen native regressions include paid freerunning casts with seven
eligibility controls and paid bloodthirst regression controls. Filtered queries
reject missing provenance rather than counting it without its predicates.

The packaged damage runtime passes all 14 live integrations and the complete
catalog construction check. Its
[`damage_state_evidence.json`](damage_state_evidence.json) records exact runtime,
source, before/after, Oracle and receipt hashes. The before probe demonstrates a
real lost freerunning window in the previous runtime; the new paid witnesses
verify normal admission, two-mana payment and drawing three cards. They cast
after the copy and do not certify arbitrary paid spell state already on the stack.

The participant patch preserves eliminated players as canonical historical
references while excluding them from active players and turn order. Fourteen
native cases include three loss reasons at every non-active Commander seat,
target rejection, damage/memory references, independent subsequent losses,
removed owned cards and a resolved Blood Tyrant trigger without replay. The
packaged witness records both the original resurrection/history failures and
the corrected state. See
[participant_state_evidence.json](participant_state_evidence.json) for exact
hashes and limits; active-turn departures and pending loss triggers are outside
these fixtures.

The starting-life patch preserves registration and live player baselines
separately, before restoring current life. Sixteen native regressions include
Righteous Valkyrie boundary cases and real paid Angel's Mercy/Chaplain's Blessing
casts, plus configured baselines, eliminated players and a live override.
[starting_life_evidence.json](starting_life_evidence.json) binds the before-fix
incorrect bonus, exact build, source hashes and packaged-runtime witnesses.
It does not certify every starting-life-dependent card or every registration
field.
