# Safety Rules — What AI May Change, and How

> Harness doc E. Zones are assigned per file/area. When one change spans two
> zones, the stricter zone governs the whole change.

## Zone table

### 🟢 Safe zone — modify autonomously, no confirmation needed

| Area | Notes |
|---|---|
| `parser-core/.../bank/*Parser.kt` (Malaysian parsers & new ones) | Core loop of this fork. Tests mandatory. |
| `parser-core/src/test/**` | Adding cases is always safe; deleting/weakening existing assertions is 🟡. |
| `app/.../receiver/BankNotificationConfig.kt` | Adding package↔alias entries. Removing entries is 🟡. |
| `docs/**` incl. `docs/HARNESS/**` | Keep in sync with code per W1/W7. `docs/supported-banks.json` is generated — regenerate, never hand-edit. |
| `STATE.md` / `LESSONS.md` updates | Required by the session protocol. |
| New UI strings, icon mappings (`BrandIcons.kt`), display-only tweaks | Compile + name the on-device check. |

### 🟡 Risky zone — plan first, get user OK before committing

| Area | Why |
|---|---|
| `app/.../data/database/entity/*`, `PennyWiseDatabase.kt`, DAOs, migrations | User's financial history lives here. Additive column changes may proceed (W6); anything destructive needs OK. |
| `app/.../data/backup/**` | Restore compatibility contract (`docs/backup-format.md`). |
| `SmsTransactionProcessor`, `BankNotificationListenerService`, `SmsBroadcastReceiver`, workers | One bug = silent data loss for every future message. |
| `SelfTransferDetector.kt` and the cross-bank TRANSFER window | Wrong change inflates or hides income/expense totals. |
| `RuleEngine.kt` and rule models | Rewrites every future transaction at ingestion. |
| `BankParserFactory` beyond appending a registration | Ordering affects sender resolution for all 55+ banks. |
| Upstream-owned parsers (Indian/UAE/etc.) | We don't have their samples; regressions invisible to us. |
| Gradle files, `build-my-apk.yml`, AndroidManifest | Breaks the user's only delivery pipeline. |
| Deleting/renaming categories, changing analytics filter chain | See DOMAIN_MODEL §2/§5 invariants. |

### 🔴 Critical zone — forbidden without an explicit, specific user instruction

1. **`transactionHash` computation** — changing it re-imports the past as duplicates.
2. **Backup-format contract** — removing a Kotlin default, renaming a
   serialized field, or changing case conventions of the backup JSON.
3. **Currency semantics** — the two money rules in `CLAUDE.md` (always format
   with currency; never sum across currencies) and entity currency defaults.
4. **Existing DB migrations / exported schema JSONs** (`app/schemas/**`) — history is immutable.
5. **Destructive git**: force-push, history rewrite, `git add -A` in a dirty
   tree, deleting the unmerged pre-existing local changes.
6. **PII**: never add real names, account numbers, or message bodies containing
   them to code/comments/docs/commits. *Single accepted exception*: the owner
   name constant inside `SelfTransferDetector.kt` — required for the feature,
   already approved. Do not replicate it elsewhere; reference the detector.
7. **Publishing beyond the fork**: PRs to upstream, releases, store metadata,
   crowdin — user-only decisions.
8. **Disabling tests** (deleting or `@Disabled`) to make a build pass. The
   known pre-existing `SupportedBanksDocTest` failure is tolerated, not silenced.

## Cross-cutting rules

- **Business-logic protection**: before changing any file in 🟡, list the
  DOMAIN_MODEL invariants it touches and state, in one sentence each, why the
  change preserves them. If you can't, treat as 🔴.
- **Blast-radius rule**: a fix for one bank must not modify shared base classes
  (`BankParser`, `CompiledPatterns`) unless at least two banks need it — and
  then it's 🟡.
- **Pre-existing dirty tree** (currently: rules/accounts/home/subscriptions
  edits, ~33 files): do not commit, revert, or "finish" them unasked. They are
  another session's work-in-progress. Stage only your own paths.
- **When zones are ambiguous** (new file, unclear area): default to 🟡.
