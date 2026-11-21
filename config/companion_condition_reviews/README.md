# Companion wording reviews

The sibling companion_condition_reviews.json file selects the per-archive layout.
The reader derives one filename from the selected engine's exact card-archive
SHA-256 checksum. It does not enumerate the other archives.

Each checksum-named JSON file uses the bounded version-1 review format. Every
record in that file must identify the same archive and one exact Oracle card
identity. Oracle text and native condition text must match the reviewed strings;
these records do not authorize approximate predicates or general card support.

Keep each file within 32 KiB and 32 entries. Missing reviews do not approve wording
differences. Redirected paths, duplicate identities, foreign archive checksums and
malformed records fail closed.

Approval provenance binds both the policy bytes and selected file bytes. Updating
either invalidates the old approval. Unrelated archive files are not part of the
selected engine's approval.

When preparing a new engine, verify its companion scripts and conditions before
adding its review file. Copying a previous approval requires checking the relevant
script bytes and exact condition; an unchanged card name is insufficient.
