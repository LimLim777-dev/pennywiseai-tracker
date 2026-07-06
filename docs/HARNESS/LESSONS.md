# LESSONS — Append-Only Incident Log

Format: `date | what went wrong | root cause | rule changed`

- 2026-06 | backup import script wrote snake_case field names for transactions | backup JSON mixes snake_case wrappers with camelCase entity fields; contract was undocumented at point of use | DOMAIN_MODEL §1 + TP-4 point to docs/backup-format.md before any serialization work
- 2026-06 | Boost incoming self-transfers recorded as INCOME | Boost income notifications omit sender name; name-based detector couldn't fire | app-layer cross-bank window added (code); edge case documented in DOMAIN_MODEL §1
- 2026-06 | `SupportedBanksDocTest` left permanently red | doc-sync step wasn't part of any parser checklist | W1 step 7 makes catalogue regeneration (update-supported-banks.sh) part of parser addition
- 2026-06 | Malaysian parser tests landed in one root-level file | parser-test-standards.md existed but wasn't referenced by the working process | W1 step 6 names the exact path + standard
- 2026-07-03 | AI memory said Boost bug was unfixed; it had been fixed weeks earlier | live state was kept in AI memory + external handover doc instead of the repo | STATE.md created; EVOLUTION_PROTOCOL "STATE.md wins" rule
- 2026-07-06 | one malformed user regex could silently drop all future ingested transactions; scale-sensitive TEXT amount equality broke cross-channel dedup | pipeline had zero unit tests, so contradictory behaviors lived for months | full review written to docs/reviews/; pipeline regression tests now required with every pipeline fix (W5 row enforced)
- 2026-07-06 | PowerShell bulk regex replace corrupted UTF-8 chars (—, ••) in a Kotlin file, incl. a user-visible string | Get-Content/Set-Content round-trip without explicit encoding mangles multi-byte chars | rule: never bulk-edit source files via PowerShell text round-trips; use the Edit tool per occurrence
- 2026-07-07 | Boost/Ryt cross-channel dedup + Boost self-transfer reclassification silently dead: T-Bank canHandle substring-matched "TBANK" inside "BOOSTBANK"/"RYTBANK" and registers earlier in the factory | canHandle contracts use naive substring matching; nothing tests alias→parser resolution for the fork's senders | T-Bank fixed to word-boundary; listener pre-pass now content-aware; rule: a new parser's canHandle must be checked against ALL existing Malaysian aliases (factory resolution test)
