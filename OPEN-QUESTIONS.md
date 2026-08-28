# Timezone Plugin — Open Questions

**All resolved.** Kept as a record of where each answer lives.

| | |
|---|---|
| O1 row labels | D7 — labelled by zone name, abbreviation beside the time |
| O2 abbreviation collisions | D6 — ambiguous tokens rejected with a "did you mean" |
| O3 HUD master toggle | D11 |
| O4 command grammar | D14 — no dates in v1, longest-match cities, usage hint on bare `!TZ` |
| O5 output string | D8 — both days always shown |
| O6 clock format | D9 — 24h default, no seconds, date only when it differs |
| O7 DST gaps and overlaps | D13 — `java.time` defaults, pinned by tests |
| O8 Jagex clock | D10 — Local and Jagex seeded as ordinary removable rows |

Two things remain, and neither is a design question:

- **Verify in-game.** Nothing in the RuneLite-facing layer has run in a client, and per
  RuneLite's guidelines an agent must not drive the game. See the README.
- **Check the plugin name against the hub before submitting.** "Timezone HUD" is the
  working name; `time-zone-clocks` already exists and is a different plugin. This needs a
  GitHub account and a plugin-hub PR either way.
