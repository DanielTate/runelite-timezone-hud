# Timezone Plugin — Open Questions

Unresolved. Each needs an answer before or during build.

**Resolved and moved to `DECISIONS.md`:** O1 (row labels -> D7), O2 (abbreviation
collisions -> D6), O3 (HUD master toggle -> D11), O5 (output string -> D8), O6 (clock
format -> D9), O8 (Jagex clock -> D10).

---

**O4. Command grammar details left open by D5.**
- Can a date be supplied? `!TZ EST 20:30 sat > NZST`, or `28/8`? D8 omits the year from
  the output, which is only safe while dates stay near-term — settle both together.
- How are multi-word city aliases delimited, given the parser is whitespace-driven?
  ("new york" — quoted? underscore? longest-match against the alias table?) D6 makes
  this more pressing: the "did you mean" hint tells users to type a city name, so city
  input has to actually work.
- What does `!TZ` with no arguments do — usage hint, or dump all your clocks?
- Behaviour on an **unknown** zone token. D6 settles the *ambiguous* case and requires
  the two errors look visibly different, so the unknown-token message needs designing
  alongside it.

**O7. DST transition edge cases.**

`20:30` on a spring-forward day may not exist; on a fall-back day it happens twice.
`java.time` resolves both silently by default. Decide whether to surface a warning or
accept the default resolution.

Note this now has an output-format consequence: D8 prints the abbreviation live at the
converted instant, so a fall-back ambiguity is the one case where the abbreviation
genuinely disambiguates the answer (`EDT` vs `EST` at the same wall-clock time). That may
be enough on its own, or may argue for saying so explicitly.
