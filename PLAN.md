# Timezone Plugin — Build Plan

Working name **"Timezone HUD"**. Verify hub name collisions before submitting (see
`OPEN-QUESTIONS.md`). Decisions referenced as D1-D11 from `DECISIONS.md`.

| | |
|---|---|
| Package | `nz.tate.timezonehud` |
| Config group | `timezone-hud` |
| Author | `dan` |
| Toolchain | JDK 17 compiling to Java 11 (`options.release.set(11)`), Gradle 8.10 wrapper, JUnit 4.12 |

---

## Architecture — pure core, thin RuneLite shell

The rule that shapes everything: **every piece of logic that can be tested without a
running game client lives in a class that imports nothing from `net.runelite`.** The
plugin, overlay, panel and chat handlers are thin adapters over that core.

This is what makes "we want tests" achievable at all — per `AGENTS.md` and Jagex's
guidelines, no agent may drive the game, so anything requiring the client is manual.
Pushing logic into pure classes shrinks that manual surface to rendering and wiring.

```
core/   (no RuneLite imports — 100% unit tested)
  ZoneRegistry      abbreviation -> IANA zone, contested set, display names   D1 D6 D7
  ZoneLookup        result type: Resolved | Ambiguous(candidates) | Unknown   D6
  ClockFormatter    row rendering: 24h/12h, abbreviation, date-when-differs   D9
  ConversionFormat  the converter output string                              D8
  CommandParser     !TZ / ::tz grammar -> ConversionRequest                  D5
  TimeConverter     the actual zone arithmetic                               D1 D8
  Clock             one clock: zone id + display label                       D7 D10

plugin/ (RuneLite-facing — manual verification)
  TimezoneHudPlugin      lifecycle, event wiring
  TimezoneHudConfig      config interface                                    D9 D11
  ClockRepository        persistence + first-run seeding                     D10
  TimezoneHudOverlay     DETACHED movable HUD                                D3 D11
  TimezoneHudPanel       side panel editor                                   D2
  ChatCommandHandler     ::tz and !TZ                                        D4
```

## Rules this plan is built around

| Rule | Where it lands |
|---|---|
| No modifying outgoing chat | `!TZ` rewrites the received `MessageNode` for local display only. The outgoing message is never touched. **This is the single highest-risk line in the plugin** — it gets its own commit and its own comment. |
| No autotyping | Nothing is ever inserted into the chatbox input. Output goes to `addChatMessage` or a rewritten node. |
| `ZoneId.SHORT_IDS` is unusable | Legacy fixed offsets and wrong mappings (`BST`->Asia/Dhaka). We ship our own table. Enforced by a test asserting we never call it. |
| Java 11 | No `var` in fields, no records, no switch expressions, no text blocks. |
| Overlay runs every frame | The HUD formats strings on a minute boundary and caches; `render()` only draws. D9 excludes seconds precisely so this holds. |
| Config keys are permanent | Group `timezone-hud` and every key name is fixed at first commit. Renaming silently resets users' settings. |
| No `log.info` per event | `log.debug` for diagnostics; `log.info` only for startup/shutdown. |

## Commit sequence

Each is one self-contained, reviewable change. Tests land in the same commit as the code
they cover — a commit is not "done" without them.

| # | Commit | Tests |
|---|---|---|
| 1 | `chore: scaffold plugin from example-plugin template` — rename package/classes/group, BSD-2 licence, properties, `.gitignore` | build compiles |
| 2 | `feat: zone registry with DST-aware abbreviation table` (D1, D7) | `EST`->`America/New_York`; renders `EDT` in Aug, `EST` in Jan; display names present for every entry |
| 3 | `feat: reject ambiguous abbreviations with candidate list` (D6) | `IST`/`CST`/`BST` return `Ambiguous` with all candidates; `EST`/`PST`/`NZST` still resolve; unknown differs from ambiguous |
| 4 | `feat: clock row formatting` (D9) | 24h default, 12h toggle, no seconds, date shown only when it differs from local |
| 5 | `feat: conversion output formatting` (D8) | both days always present; rollover and same-day cases; abbreviation is the one live at the *converted* instant |
| 6 | `feat: command grammar parser` (D5) | optional source/target, bare times, `now`, `>`/`->`/`to`, case-insensitivity, malformed input |
| 7 | `feat: time conversion engine` (D1, D8) | NZ<->US across DST boundaries; UTC; same-zone identity |
| 8 | `feat: clock model and repository with first-run seeding` (D10) | Local + Jagex seeded once; a cleared list stays cleared; round-trips through config |
| 9 | `feat: config interface` (D9, D11) | — |
| 10 | `feat: plugin lifecycle and overlay registration` | — |
| 11 | `feat: movable HUD clock overlay` (D3, D11) | formatter cache invalidates on the minute |
| 12 | `feat: side panel clock editor` (D2) | — |
| 13 | `feat: ::tz local chat command` (D4) | — |
| 14 | `feat: !TZ public command with local-only rewrite` (D4) | asserts the outgoing message object is never mutated |
| 15 | `docs: README` | — |

Commits 1-8 are pure logic and fully covered. Commits 9-14 are the manual-verification
surface — deliberately last, deliberately thin.

## Test strategy

- **JUnit 4.12** (what the template ships — not JUnit 5).
- **Fixed clocks everywhere.** `TimeConverter` and the formatters take a
  `java.time.Clock`, injected as `Clock.fixed(...)` in tests. No test ever reads the
  real time, so nothing is flaky and DST-boundary cases are directly expressible.
- **DST boundaries are the interesting cases**, not the happy path: convert into January
  from August and assert `EST` not `EDT`; convert across the NZ/US date line both ways.
- Formatter tests assert exact strings. D8's format is a user-visible contract.
- No test touches `Client`, the EDT, or the filesystem.

## Performance notes

- The HUD caches its rendered lines and rebuilds only when the minute changes or the
  clock list edits. `render()` draws pre-built strings.
- `ZoneId.of()` results are cached per clock, not looked up per frame.
- The alias table is a `HashMap` built once, statically.
- No timers, no executors, no scheduled tasks — there is nothing to leak and nothing to
  cancel in `shutDown()`. The overlay reads the current time when it draws.

## Known risks

Tracked live in `RISKS.md`.
