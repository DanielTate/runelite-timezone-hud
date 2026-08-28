# Timezone Plugin — Design Decisions

Captured during scoping. One entry per resolved question. Nothing here is code yet.

---

## D1. Timezone abbreviation semantics

**Question:** When someone writes `EST` (in a sidebar checkbox or in `!TZ`), what does it mean?

**Decision: the US Eastern *zone*, DST-aware.**

`EST` resolves to the IANA zone `America/New_York`, not to a fixed offset. The plugin
looks up the correct offset for the date in question, so it is UTC-4 in summer and
UTC-5 in winter.

```
!TZ EST 20:30 > NZST
  -> interprets EST as America/New_York
  -> 28 Aug 2026 is EDT (UTC-4)
  -> 20:30 EDT = 12:30 NZST (next day)

Sidebar checkbox "EST" displays:
  EDT  18:57   (label flips to EST in winter)
```

**Consequence — the displayed label tracks reality, not the checkbox name.** A box
ticked as "EST" will render `EDT` for roughly eight months of the year. This is
correct and intended; see open question O1 about whether the *checkbox* label should
also be renamed to something zone-shaped ("US Eastern").

**Consequence — `ZoneId.SHORT_IDS` is unusable.** Verified on JDK 17:

```
ZoneId.of("EST")   -> ZoneRulesException: Unknown time-zone ID
ZoneId.of("NZST")  -> ZoneRulesException: Unknown time-zone ID
ZoneId.SHORT_IDS   -> EST=-05:00  (fixed offset, no DST -- wrong for this decision)
                      ART=Africa/Cairo  (legacy junk)
```

The plugin must therefore ship **its own abbreviation -> IANA zone table**, e.g.
`EST/EDT/ET -> America/New_York`, `NZST/NZDT/NZ -> Pacific/Auckland`,
`GMT/BST/UK -> Europe/London`, `UTC -> UTC`. Once mapped, `java.time` handles all DST
transitions automatically from the JDK's bundled tzdb.

Verified conversion behaviour:
```
20:30 America/New_York -> Pacific/Auckland  =  2026-08-28T12:30+12:00[Pacific/Auckland]
Format pattern "zzz" correctly renders EDT / NZST / BST per date.
```

---

## D2. Sidebar UI approach

**Question:** How should the timezone list in the sidebar work?

**Decision: a custom side panel with a searchable list.**

Own icon in the RuneLite toolbar, opening a `PluginPanel`. Search across all IANA
zones, tick any of them, reorder, rename.

```
[globe icon in toolbar]

  World Clocks        [+]
  +---------------------------+
  | search: auck_             |
  +---------------------------+
  | Pacific/Auckland      [+] |
  +---------------------------+

  ACTIVE
  [x] NZ      Auckland  10:57
  [x] UTC               22:57
  [x] Home    New York  18:57
```

**Rationale / consequences**

- Any of the ~603 zones available (`ZoneId.getAvailableZoneIds().size()` == 603 on
  JDK 17), not a pre-baked subset. Renameable entries ("Home", "NZ").
- Meaningfully more work than config checkboxes: this is real Swing, and it is the
  single largest chunk of build effort in this plugin.
- This is also the half that **overlaps the existing `time-zone-clocks` hub plugin**,
  which already does searchable zones, tabs, custom names and a fixed Local/Jagex tab.
  The novel value of our plugin therefore lives almost entirely in the **movable
  in-game overlay** and the **`!TZ` converter** -- neither of which that plugin has.

**Technical shape (established from prior art, not yet code)**

- `ClientToolbar` + `NavigationButton` + `PluginPanel`, wired up in `startUp()` and
  torn down in `shutDown()`.
- A `ScheduledExecutorService` ticking once per second to refresh displayed times.
  Per RuneLite's `AGENTS.md`: must `shutdownNow()` and explicitly cancel the
  `ScheduledFuture` on shutdown, and must never block in `startUp()`/`shutDown()`.
- Selected zones persisted through `ConfigManager` (the existing plugin uses a
  data-manager class serialising to a config value).
- Panel icon PNG loaded with `Class.getResourceAsStream()`, not `getResource()`,
  so it resolves inside the packaged jar.

**Still open:** whether the *ticked* set in this panel is the same set the in-game
overlay renders, or whether the overlay has its own independent selection. See O2.

---

## D3. What controls which clocks appear on the in-game overlay

**Question:** Does the HUD mirror a per-clock visibility toggle, or show everything?

**Decision: the overlay shows all clocks; the panel is purely the editor.**

Every clock you have added renders on the HUD. The side panel exists to add, remove,
rename and order them -- there is no separate visibility concept.

```
PANEL (editor)           IN-GAME HUD
  NZ     [edit] [x]  -->  NZ    10:57
  UTC    [edit] [x]  -->  UTC   22:57
  Home   [edit] [x]  -->  Home  18:57

Add it = you see it.
Don't want it? Remove it.
```

**Consequences**

- This **supersedes the checkbox framing from the original idea.** There are no
  per-clock checkboxes acting as visibility switches; adding a zone *is* the act of
  showing it. D2's panel sketch showed `[x]` boxes -- read those as remove buttons /
  list rows, not visibility toggles.
- One list, one order. Panel order == HUD order, so reordering in the panel is
  directly meaningful and worth supporting.
- Fewest concepts, least control. The accepted trade-off: to temporarily hide a zone
  from the HUD you must delete it and re-add it later.
- A single master on/off for the whole overlay is still expected (see O3) -- the
  decision above is only about *per-clock* visibility.

**Technical shape**

- One `OverlayPanel` rendering N `LineComponent` rows, one per clock, in list order.
- `setPosition(OverlayPosition.DETACHED)` makes it user-movable (verified in
  `Overlay.java`: DETACHED sets `movable = true`, `snappable = false`). `OverlayManager`
  persists `preferredLocation` across sessions; the user drags it with alt held.
- Per RuneLite's `AGENTS.md`, overlay `render()` runs every frame and must stay cheap:
  format the time strings on the 1s scheduler tick and have `render()` only draw the
  already-computed strings.

---

## D4. Chat command trigger

**Question:** How is the time-conversion command triggered?

**Decision: support both `!TZ` (public) and `::tz` (local).**

```
!TZ EST 20:30 > NZST
  sent to chat verbatim.
  You + others with the plugin see:
    Dan: 20:30 EDT = 12:30 NZST (Sat 29th)
  Others see the raw text.

::tz EST 20:30 > NZST
  never leaves your client.
  Only you see:
    [World Clocks] 20:30 EDT
                   = 12:30 NZST (Sat)
```

### `!TZ` -- the public path

Registered through `ChatCommandManager.registerCommand("!tz", ...)`. The plugin
supplies **only the `execute` half**, which rewrites the *received* `MessageNode`
for local display and calls `client.refreshChat()`. This is the same pattern core
RuneLite uses for `!kc` / `!pb`.

**Rule compliance -- this is the important bit.** RuneLite's `AGENTS.md` forbids:

> - No **autotyping** -- plugins must not programmatically insert text into the chatbox input
> - **No modifying outgoing chat messages after the user sends them**

The chosen design does neither. The message the user typed goes to Jagex's servers
**byte-for-byte as typed**; only the locally *rendered* text is substituted, and only
in the clients of people who have the plugin. Do **not** implement this by supplying
the `BiPredicate<ChatInput, String>` input half and calling `chatboxInput.consume()`
to swallow or alter the send -- that is the grey-area variant and is explicitly
rejected here.

Mechanics confirmed by reading `ChatCommandManager` and `ChatInputManager`:

- Fires on `PUBLICCHAT`, `MODCHAT`, `FRIENDSCHAT`, `PRIVATECHAT`, `MODPRIVATECHAT`,
  `PRIVATECHATOUT`, `CLAN_CHAT`, `CLAN_GUEST_CHAT`, `CLAN_GIM_CHAT`.
- Commands are matched case-insensitively (stored via `toLowerCase()`), so `!TZ`,
  `!tz` and `!Tz` all work.
- `extractCommand()` takes the substring **before the first space**, so the trigger
  token itself must contain no spaces. `!tz` is fine.
- The `>` separator is just an argument character and survives parsing. Note the raw
  text still passes through Jagex's chat filter on its way out.
- Use `registerCommandAsync` only if work is slow; timezone math is trivial and
  synchronous, so plain `registerCommand` is appropriate.

### `::tz` -- the local path

Handled by subscribing to the `CommandExecuted` event. RuneLite strips the `::` and
delivers `command` plus a `String[] arguments` split on spaces. Nothing is ever sent
to the server -- this is client-only by design, so it carries no chat-rule exposure
at all. Output printed with `client.addChatMessage(ChatMessageType.GAMEMESSAGE, ...)`.

**Note the arg-splitting difference:** `CommandExecuted` hands you pre-split
arguments, whereas `ChatCommandManager` hands you the whole raw message string. Both
paths should funnel into one shared parser so `!TZ` and `::tz` cannot drift apart.

**Still open:** the exact grammar the parser accepts -- see O4.

---

## D5. Command grammar

**Question:** How flexible should the `!TZ` / `::tz` grammar be?

**Decision: flexible -- optional source, optional target, bare times, aliases.**

```
!TZ EST 20:30 > NZST   full form
!TZ 20:30 > NZST       source omitted -> your local zone
!TZ 8:30pm > NZST      12h accepted
!TZ EST 20:30          target omitted -> your local zone
!TZ EST > NZST         no time -> converts *now*
!TZ now > NZST         same thing

Separator: > or -> or "to"
Aliases:   EST / EDT / ET / "new york"
```

**Consequences**

- Both source and target are independently optional, each defaulting to the user's
  local zone. `!TZ 20:30` (neither given) is degenerate and should print the usage hint
  rather than echo the input back.
- More parser surface than the strict alternative, so the grammar needs its own unit
  tests. This is the one part of the plugin that is pure logic with no client
  dependency, so it is cheap to test properly.
- Ambiguity is accepted as a cost. `!TZ 8:30 > NZST` is 08:30, not 20:30 -- bare
  times without am/pm are 24h.
- Aliases mean the abbreviation table from D1 needs a **many-to-one** shape: several
  input spellings, plus city names, mapping onto one IANA zone.

**Grammar sketch (to be firmed up during build)**

```
command   := trigger [source] [time] [sep target]
trigger   := "!tz" | "::tz"          (case-insensitive)
sep       := ">" | "->" | "to"
time      := HH:MM | H:MM | HHMM | H[:MM]("am"|"pm") | "now"
source    := zone-token               (default: local)
target    := zone-token               (default: local)
zone-token:= abbrev | alias | city | IANA id
```

Unresolved sub-questions folded into O4: whether a **date** can be supplied
(`!TZ EST 20:30 sat > NZST`), and how multi-word city aliases ("new york") are
delimited given the parser is whitespace-driven.

**Output format** must make the day-rollover unmissable, since that is the whole
point for NZ<->US conversions:

```
20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)
```

See O5 -- exact output string is still open.


### Amendment — `GMT` resolves to UTC, not to `Europe/London`

Found during the build. D1's rule is right for colloquial *place* abbreviations: `EST`
means "US Eastern, whatever it is doing today". But `GMT` is not one of those — it is a
time scale that literally means UTC+0.

Mapping it onto `Europe/London` made the converter contradict its own input:

```
!TZ GMT 20:30 > NZST      in August

  read as 20:30 London time (UTC+1)
  D8 prints the abbreviation live at that instant
  -> "20:30 BST (...)"    the user typed GMT
```

That is precisely the quiet wrongness D6 exists to prevent, arrived at from the other
direction. `gmt` now points at the `UTC` zone; `london`, `uk`, `britain` and the city
names keep `Europe/London` and still flip to `BST` in summer.

`GMT` therefore renders as `UTC` rather than `GMT` — a different word from the one typed,
but the same instant. That is a much smaller surprise than a different time.

---

## D6. Ambiguous abbreviations are rejected, not guessed

**Decision: a token with more than one plausible meaning is refused, with a "did you mean"
naming the candidates and showing an unambiguous form.** This resolves O2.

```
!TZ IST 20:30 > NZST

  "IST is ambiguous - did you mean
   India (IST), Ireland (IST/GMT), or Israel (IDT)?
   Try: !TZ Kolkata 20:30 > NZST"
```

**Rationale.** D1 committed the plugin to resolving abbreviations into real DST-aware
zones, which means a wrong guess is not a cosmetic issue — it silently produces a time in
the wrong country, and the output looks entirely plausible. `IST` is India, Ireland and
Israel; `CST` is US Central, China and Cuba; `BST` is British Summer Time and Bangladesh.
There is no defensible winner for any of them, and the whole point of the converter is that
you can trust the number without checking it.

**Consequences**

- **The alias table needs a "contested" set** alongside its normal entries — tokens that
  resolve to a list rather than a zone. It is small and hand-curated: `IST`, `CST`, `BST`,
  `AST`, `ACST`, `AMT`, and any others found during build.
- **Unambiguous abbreviations still resolve freely.** `EST`, `PST`, `NZST`, `JST`, `GMT`,
  `UTC` and the rest never prompt. Rejection applies only to the contested set, so the
  common path is unaffected.
- **The error must name a working alternative**, not just report failure. "IST is
  ambiguous" alone leaves the user stuck; naming the cities tells them what to type next.
  This makes city aliases (D5) load-bearing rather than a convenience.
- **Refusal must not look like a parse failure.** An ambiguous zone and an unrecognised
  zone need visibly different messages, or "IST" reads as unsupported.
- The same rule governs the side panel's search (D2): searching `IST` shows the candidate
  zones as separate results to pick from, which is the natural UI equivalent of the chat
  prompt and needs no special handling.
- This interacts with O4's open question about unknown tokens — the two error paths should
  be designed together.

---

## D7. Rows are labelled by zone, not by abbreviation

**Decision: each clock is labelled with its zone name. The live abbreviation appears
alongside the time, where it can change without the label appearing to.** This resolves O1.

```
US Eastern    08:30 EDT
UK            13:30 BST
New Zealand   00:30 NZST
```

**Rationale.** D1 makes a clock added as "EST" display `EDT` for most of the year. If the
row were labelled `EST`, the panel would show `EST ... EDT` — the first thing that reads as
a bug, and a recurring support question twice a year. Labelling by zone matches what D1
actually stores: the user picked a *place with rules*, not a fixed offset. The abbreviation
then reads as what it is — a live readout of the current state of that zone, next to the
time it applies to.

**Consequences**

- **The alias table needs a display name per zone**, not just an IANA id. `America/New_York`
  becomes "US Eastern", not "New York" and not "America/New_York". This is a third column
  to maintain by hand.
- **Zone names are wordier than cities** — "US Eastern" against "New York" — so the panel
  and HUD need enough width for the longest label in a typical set. Worth checking against
  the narrowest sensible side panel during build.
- **The label is stable year-round while the abbreviation beside it changes.** That is the
  entire point: `US Eastern 08:30 EDT` in August and `US Eastern 08:30 EST` in January both
  read correctly, with nothing appearing to have renamed itself.
- **D2's rename feature still applies on top.** A user who prefers "raid team" or "mum" can
  override any label; the zone name is only the default.
- Naming is a judgement call per zone and should be settled once, in the table, rather than
  derived — `java.time`'s own display names are inconsistent and sometimes surprising.

---

## D8. Converter output format

**Decision: both sides always carry their day, whether or not it rolls over. `!TZ` and
`::tz` share the format exactly.** This resolves O5.

```
20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)
```

**Rationale.** D5 requires the day rollover to be unmissable, and NZ<->US conversions cross
the date line most of the time. Printing the day only when it differs would make its
*absence* carry meaning, which is exactly the kind of signal a reader skims past. Showing
both unconditionally means the reader never has to notice anything — the two dates are
simply there to compare, and a rollover is self-evident.

**Consequences**

- **One format, two paths.** `!TZ` and `::tz` produce byte-identical strings, so there is a
  single formatter and no chance of the public and local forms disagreeing. This matters
  more than usual given D4: the `!TZ` path renders the *locally displayed* message only,
  so any divergence would be invisible to the sender.
- **Output is longer than the minimum**, which is a real cost in a chat line. Accepted —
  correctness of the day is the feature.
- **The abbreviation shown is the one live at the converted instant**, not at the moment of
  typing. A conversion into next January from August must print `EST`, not `EDT`, or the
  string contradicts itself.
- **Day names are English and abbreviated** (`Fri 28 Aug`), matching RuneScape's own chat
  conventions and keeping the line short. No locale handling in v1.
- Year is omitted. It reappears only if O4 ever admits explicit dates far enough out to
  need it — worth remembering when that question is settled.

---

## D9. Clock display format — 24-hour, no seconds

**Decision: 24-hour by default with a config toggle for 12-hour; no seconds; the date shown
on a row only when it differs from your own.** This resolves O6.

```
US Eastern    08:30 EDT
UK            13:30 BST
New Zealand   00:30 NZST  Sat 30

config: [x] 24-hour time
```

**Rationale.** One toggle covers the only preference that genuinely divides people. Seconds
are excluded deliberately: they would force the HUD overlay to repaint every second for no
information anyone needs from a world clock, and the existing `time-zone-clocks` plugin's
fuller config surface is exactly the kind of thing this plugin should be simpler than.
Showing the date only when it differs keeps rows short while flagging the case that matters
— the same reasoning as D8, inverted, because here the row is glanceable state rather than
a one-shot message that must survive being skimmed.

**Consequences**

- **The overlay repaints on the minute, not the second.** Cheap, and it keeps the HUD off
  the per-frame path entirely.
- **"Differs from your own" is computed against your local date**, not UTC, so the marker
  appears exactly when the row is on a different calendar day from you.
- **The date marker is short** (`Sat 30`) and sits after the abbreviation, so rows stay
  aligned whether or not it is present.
- **One config key, shared by panel and HUD.** They never disagree on format.
- Contrast with D8: the converter always prints both dates because a chat line is read once
  and cannot be re-examined; a clock row is ambient and re-read constantly, so the marker
  can afford to be exceptional.

**Declined:** a seconds toggle, an always-show-date toggle, and a free-form format string.

---

## D10. Local and Jagex clocks are seeded, not fixed

**Decision: the list is pre-populated on first run with Local and Jagex (UTC) as ordinary
rows, deletable and renameable like any other.** This resolves O8.

```
Local         20:30      [x]
Jagex (UTC)   08:30      [x]
US Eastern    08:30 EDT  [x]
```

**Rationale.** Game time is the single most useful clock for coordinating anything in
RuneScape, and an empty panel on first run teaches the user nothing about what the plugin
does. Seeding solves both. Making them ordinary rows rather than pinned specials keeps the
data model uniform — every row is the same kind of thing, with no branch in rendering,
reordering, deletion or persistence.

**Consequences**

- **No special-casing anywhere.** The overlay (D3 shows every clock), the panel, the
  storage format and D7's labelling all treat these two identically to a user-added zone.
- **Seeding happens once**, guarded by a first-run flag — not "if the list is empty", which
  would resurrect them every time someone deliberately clears the list.
- **Local is a real zone lookup** (the system default), not a formatting shortcut, so it
  behaves correctly if the user's machine changes zone mid-session.
- **`Jagex (UTC)` is the label**, per D7 — the zone is `UTC`, and naming it after the game
  is what makes it useful to a player. It is the one entry where the default label is not
  a geographic zone name, and that is deliberate.
- A user who deletes both gets an empty panel, and that is a legitimate state.

---

## D11. A master toggle hides the HUD overlay

**Decision: one config toggle hides the entire in-game clock overlay, leaving the side
panel and every saved clock untouched.** This resolves O3.

```
config:
  [x] Show clock overlay

(panel and clocks unaffected)
```

**Rationale.** D3 settled that the overlay shows every clock with no per-clock visibility,
which means without a master toggle there is no way to get clocks off your screen short of
deleting them. Since the overlay and the panel serve different moments — ambient glance
versus deliberate lookup — wanting one without the other is an ordinary preference, not an
edge case.

**Consequences**

- **Toggling is non-destructive and instant.** Clocks persist, order persists, the panel is
  unaffected; only the overlay stops rendering.
- **Off is a valid resting state.** With the overlay hidden the plugin is still fully
  useful — the panel works and both chat commands (D4) work, since neither depends on the
  overlay existing.
- **This is the only overlay visibility control**, consistent with D3. There is no
  per-clock hiding, and the panel remains the editor for what exists at all.
- Interacts with D3's design intent: the panel curates *which clocks exist*, this toggle
  controls *whether the HUD is drawn*. Two clean, non-overlapping jobs.
