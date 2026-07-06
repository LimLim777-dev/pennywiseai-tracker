# PennyWise Development Harness

**Start here.** This directory turns the fork from prompt-driven ("explain the
project to the AI every session") into system-driven ("the AI reads the rules
and executes"). It is written so that smaller models can do consistent work
with near-zero ad-hoc prompting.

## The system in one paragraph

The user states a goal in plain language (often just a bank message screenshot
and "this wasn't recorded"). The AI session classifies the request with the
routing table below, reads **only** the referenced workflow/pattern (not the
whole repo), executes it to its acceptance criteria, records the outcome in
`STATE.md`, and — if anything went wrong — logs it in `LESSONS.md` so the
rules improve. Human input is reserved for the four things only the user can
provide: real message samples, on-device verification, product decisions, and
approvals in risky/critical safety zones.

## Session protocol (any AI model, every session)

1. Read this README, then [`STATE.md`](STATE.md) (live truth; overrides AI
   memory and old handover docs).
2. Route the request via the table below; read that workflow + task pattern.
3. Execute. Respect [`SAFETY_RULES.md`](SAFETY_RULES.md) zones at every file
   you touch; apply [`FAILURE_PLAYBOOK.md`](FAILURE_PLAYBOOK.md) limits when stuck.
4. Before ending: update `STATE.md`; append to `LESSONS.md` if something
   failed; encode any user correction per
   [`EVOLUTION_PROTOCOL.md`](EVOLUTION_PROTOCOL.md).

## Routing table

| Request looks like… | Pattern | Workflow |
|---|---|---|
| "Support bank/wallet X" / "this message wasn't captured" (new source) | TP-1 | W1 |
| "This transaction is wrong (amount/merchant/type/duplicate)" | TP-2 | W2/W3 |
| "Screen X looks/behaves wrong" | TP-3 | W3 |
| "Track a new kind of data" (schema) | TP-4 | W6 |
| "Clean up / restructure code" | TP-5 | W4 (gated) |
| "Something's broken, unclear why" | TP-6 | W3 + playbook |
| "Build me the APK / put it on my phone" | TP-7 | W8 |
| "Pull in upstream updates" | TP-8 | — (human-gated) |
| "FYI: new sender / salary format / life change" | TP-9 | — |
| "Always categorize/rename X as Y" | Rule/MerchantMapping first (DOMAIN_MODEL §6), code last | — |
| "Build new feature X" (calendar, PDF import, …) | **Requires human judgment**: agree scope with user first, then decompose into TP-1…TP-4 sized tasks | W5 per task |

Patterns: [`AI_TASK_LIBRARY.md`](AI_TASK_LIBRARY.md) ·
Workflows: [`OPERATING_SYSTEM.md`](OPERATING_SYSTEM.md)

## Document map

| File | Role |
|---|---|
| [`PROBLEM_DIAGNOSIS.md`](PROBLEM_DIAGNOSIS.md) | Why this harness exists; constraints it accepts |
| [`DOMAIN_MODEL.md`](DOMAIN_MODEL.md) | What the data means; invariants that must never break |
| [`OPERATING_SYSTEM.md`](OPERATING_SYSTEM.md) | W1–W8 deterministic workflows + session protocol |
| [`AI_TASK_LIBRARY.md`](AI_TASK_LIBRARY.md) | TP-1…TP-9 task contracts (inputs, outputs, verification) |
| [`SAFETY_RULES.md`](SAFETY_RULES.md) | 🟢/🟡/🔴 zones — what may change autonomously |
| [`FAILURE_PLAYBOOK.md`](FAILURE_PLAYBOOK.md) | Stuck detection, retry limits, escalation |
| [`EVOLUTION_PROTOCOL.md`](EVOLUTION_PROTOCOL.md) | How rules themselves get updated/pruned |
| [`STATE.md`](STATE.md) | **Live** open/blocked items — read every session |
| [`LESSONS.md`](LESSONS.md) | Append-only incident log feeding rule updates |

## How to avoid prompting (for the user)

You never need to explain the project again. Useful message shapes:

- *New bank*: name + verbatim in/out messages (mask account digits with X, keep wording).
- *Wrong record*: screenshot of the transaction + what it should say.
- *Broken screen*: which screen, what you tapped, what you expected.
- *News*: "salary message looks like this" / "new UOB short code 12345".

The AI will ask for exactly one missing thing at a time, in phone-doable terms.

## Fixed facts (so no session rediscovers them)

- Fork: `github.com/LimLim777-dev/pennywiseai-tracker`, working branch `main`,
  upstream `sarim2000/pennywiseai-tracker` (never auto-merged).
- Local clone: `D:\Claude Code - Personal\PennyWise\pennywiseai-tracker` (Windows/PowerShell).
- Build `.\gradlew :app:assembleStandardDebug` · parser tests
  `.\gradlew :parser-core:jvmTest` · phone APK = **arm64-v8a** from
  `build-my-apk.yml` Actions artifacts.
- Device: HONOR LGN-NX1; the notification listener dies if not whitelisted in
  battery settings (permanent OS limitation).
- Currency is always explicit `MYR` on fork code paths; entity defaults are INR
  (upstream) — see DOMAIN_MODEL §1.
- The Chinese handover doc (`../项目交接文档.md`) is **historical**; where it
  conflicts with `STATE.md` or code, it is out of date.
