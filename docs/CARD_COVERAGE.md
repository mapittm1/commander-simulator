# Card support and evidence

The sticker-capable runtime uses the official Forge source snapshot
[`3e6c036e71b05b4d9d035df08b7a0c0dfa30cc76`](https://github.com/Card-Forge/forge/commit/3e6c036e71b05b4d9d035df08b7a0c0dfa30cc76),
the reviewed Wicker Picker implementation from
[upstream PR 12129](https://github.com/Card-Forge/forge/pull/12129) at immutable
head `470d983bea1dd40b06ea81c6e061b4b14c31f933`, and two local corrections.
The corrections account for already-added costs when multiple Wicker Pickers
grant kicker, and count all generic kicker payments without satisfying an
unpaid, linked printed kicker ability.

The repository includes the exact patches, regression source, source/archive
checksums, build instructions and Forge's GPL license under [engine/](../engine/).
Compiled engines and the card database remain local, ignored data. The build
tool preserves a separate runtime, its exact lock, logs and test results. It
does not silently replace the selected engine. Rebuilding may change the JAR
hash because upstream packaging embeds build metadata; every build needs its
own lock and validation.

## Coverage levels

These checks answer different questions:

| Evidence | What it establishes | What it does not establish |
| --- | --- | --- |
| Versioned Commander legality | The selected list satisfies implemented construction rules against the current local catalog | Bracket appropriateness, strategy or every pregame option |
| Engine audit | Every selected identity has an exact script or an explicitly reviewed mapping | Successful native parsing or gameplay |
| `engine-card-probe` | The native card factory can construct every matched identity and enumerate its states and abilities | Every ability resolves correctly, all rules interactions or competent AI play |
| Controlled interactions | Named, independently checked native state transitions pass | Untested combinations or general AI decisions |
| CLI/game integration | Import, review, four-player games, diagnostics and resumable batches work together | Competitive realism or statistical accuracy |

On the frozen local catalog tested on October 27, 2026, the new runtime constructs
**32,109 identities out of 32,116 Commander-format records**, with zero native
construction failures. The remaining seven are meld-result objects that cannot
occupy starting-deck slots. This is not seven unsupported starting cards. Their
gameplay depends on their native front-card scripts and requires separate meld
interaction evidence. The inventory includes supplementary objects, so the
constructed count is not a count of legal starting-deck cards.

Ragnarok's representative printing points to a different printing of its own
meld result. A deny-only review pins both complete printing records and their
Oracle identity. It excludes only this exact result from starting cards;
matching names cannot approve an unknown meld front. Validator version 4,
auditor version 3 and gap-classifier version 3 preserve the changed interpretation
without rewriting historical audits or catalog import timestamps.

## Run the catalog construction probe

Install `requirements-interactions.txt`, configure a trusted Java 17+ runtime,
then run:

```powershell
.\.venv\Scripts\python.exe commander_simulator.py engine-card-probe
# A separate candidate can be evaluated without changing the default:
.\.venv\Scripts\python.exe commander_simulator.py engine-card-probe --engine-config PATH_TO_BUILD/engine-config.json
```

Each run preserves an immutable audit, frozen input identities, native observations,
a receipt, a log and the final report under `data/runs/`. Identities, order, count,
engine/source hashes and the native Java version are checked. Missing, changed,
duplicate or malformed evidence cannot pass; neither can a timed-out or failed
worker. Seven explicitly classified meld results are acceptable exclusions;
unavailable starting cards produce `gaps_remain` and a failing CLI exit status.

Plans and receipts remain bounded JSON. Catalog rows use a separate protocol:
4 KiB per row, at most 100,000 rows and 32 MiB per stream. Native construction
runs in one process with a 600-second deadline, a 2 GiB JVM heap and an 8 MiB log
limit. These are application limits, not an operating-system security sandbox or
a total process memory quota. Card scripts and build plugins are trusted engine
code; users cannot upload them through a decklist.

## Gameplay work still required

The controlled suite currently covers 41 four-player interaction scenarios.
The patched build additionally passes all 23 upstream sticker tests and 10 local
Wicker Picker tests, with no skips. Those ten tests cover paid/declined kicker,
ticket and sticker timing before resolution, no selected sheets, affordability,
multiple grantors and interaction with printed kicker.

The CLI now supports supplementary sticker and Attraction sections through
validated immutable revisions. Native pregame integration verifies all 48 catalog
sticker sheets and all 22 Commander-legal Attractions, retaining every selection
and revealing exactly three distinct selected sheets. Empty selections produce
no supplementary objects. Attraction light variants currently use Forge's default
printing selection; an explicit printing-selection interface remains work.
Companion conditions and commander color choices still require backend support.
Construction counts must not conceal that limitation. Further work includes
meld transitions, catalog-wide characteristic comparisons and broader native
rules regressions. An implementation claim should name its tested behavior;
script presence and successful games alone do not certify full card coverage.


## Supplementary decklist sections

Add `Stickers` (or `Sticker Sheets`) and `Attractions` headings to the same plain
text import used for starting cards. Each following line uses `1 Exact Name`.
Neither section is part of the 100 starting cards. If selected, either section
requires at least ten unique selections, one each. There is no rules-imposed
maximum; normal upload/line limits still apply. Authoritative catalog types and
Commander legality are checked before saving or simulation. Attractions and
sticker sheets cannot replace starting cards.

These rules follow CR 123.2a and 717.2a in the project's pinned
[Comprehensive Rules](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf).
Forge performs the random three-sheet selection and Attraction shuffle during
native pregame setup. Saved revisions hash all supplementary selections and
retain their Oracle records. Changes require a new revision/review. The latest
legacy 100-card projection stays separate from the supplementary projection;
immutable revision snapshots remain authoritative. Validator version 5 adds
these construction checks.

The legacy two-counter `parse_decklist` API rejects supplementary selections
instead of silently discarding them. Use `prepare_deck` for resolved, validated
imports or `parse_deck_sections` when only parsing is needed. Ordinary Sideboard
and Maybeboard sections retain their existing ignored behavior and do not select
a companion.
