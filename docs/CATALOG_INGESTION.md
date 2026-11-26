# Catalog ingestion and latest refresh

Catalog maintenance treats provider data as input, never executable instructions.
Limits live in `commander/catalog/bulk_limits.py`; parsing, network transfer and
transactional storage have separate responsibilities. The CLI uses these defaults:

| Resource | Limit |
| --- | --- |
| Metadata | 2 MiB |
| Encoded download/local input | 8 GiB |
| Expanded bytes | 32 GiB |
| Decoded characters per record | 2,097,152 |
| JSON nesting per record | 64 |
| Records per import | 5,000,000 |
| Shared download deadline | 30 minutes, cooperative |
| Shared import deadline | 6 hours, cooperative |

Timing checks surround reads and record writes; they do not forcibly interrupt
blocked sockets or storage. Metadata/download socket timeouts remain 60/120
seconds. UTF-8 BOM, gzip, JSON arrays and JSON Lines are supported. Imported
records and freshness receipts share one transaction; existing caller transactions
are rejected without committing or rolling them back. A failed transfer preserves
the old cache. A completed transfer replaces it before content import, so malformed
content may replace the cache even though database changes roll back.

## Snapshot evidence

The normal CLI sync imported 39,412 records from the Oracle export timestamped
`2026-11-26T10:02:03.092+00:00`, versus 39,356 in the preserved prior export.
These are catalog records, not a count of playable Commander cards. Local backup,
raw CLI audit envelopes and comparison receipts remain ignored under
`data/development/catalog-ingestion-bounds/refresh-v1/`.

| Runtime | Audit ID | Sealed report SHA-256 |
| --- | --- | --- |
| Candidate | e7279ca1148e43b4a6fb3572a3952e40 | 997edfe3bb0d45dc31fb5cdbf55ad58d31060e6350c5014399ea1e2055ed880c |
| Selected | ec2f7e9d460a4584b5b95cc96e83f024 | aec2e8738a63d5666b75ecbd7aa67facfcfc11dcaaa44a3529faf31fb1cc5288 |

Both reports count 32,643 Commander-legal identities: 32,636 script matches and
seven non-starting meld results without standalone scripts. Neither report
verifies card construction, interaction correctness or AI competence. The selected
runtime and candidate binaries were not changed or activated by this work.

A comparison against the backup found no added/removed Commander-legal IDs.
Only two records changed among the explicitly compared Oracle/game fields:

- **Hit It!** now says “reveal” instead of “look at” the top five cards. The
  candidate script already says reveal. Existing `StarTrekRevealTest` controls
  observe the exact five identities revealed to every opponent, with and without
  a land, land entry tapped and graveyard movement without a Zellix mill trigger.
  See [preserved native evidence](../engine/public_reveal_evidence.json) and
  [the assertions](../engine/tests/StarTrekRevealTest.java). In DigEffect,
  `Reveal$ True` reveals the initial group, while `NoReveal$ True` suppresses a
  later chosen-card reveal; those parameters do not cancel each other. This
  refresh did not rerun native tests or extend their scope.
- **B'Elanna Torres, Scrapper** now explicitly conditions drawing on an artifact
  having been revealed this way. The candidate definition remembers revealed
  cards and conditions drawing on a remembered artifact, but retains old display
  wording. Optional reveal, draw replacement and counter outcomes need interaction
  validation before claiming conformance to the updated text.

The comparison covers identity, name, mana cost/value, types, Oracle text,
colors/identity/indicator, power/toughness, loyalty, defense, keywords, layout,
hand/life modifiers, produced mana, Commander legality and corresponding face
fields. Printing metadata and other fields are outside that comparison. The
initial `after.json` summary incorrectly read counts from the outer CLI envelope;
`after-v2.json` corrects the nesting and preserves the original raw audits. No
import or audit was rerun to hide that analysis error.

The final offline gate passes Black, isort, Pylint, Bandit and 490 tests, including
26 new resource/rollback tests (845 subtests; 45 opt-in integration skips). This
is ingestion validation, not proof of full Magic rules support. The native timed
gates and broader gameplay validation described in CARD_COVERAGE.md remain open.
