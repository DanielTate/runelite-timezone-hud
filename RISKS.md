# Risks, Issues and Concerns

Live record kept during the build. Newest first within each section.

## Open

**R13. D4's `::tz` sketch shows a `[World Clocks]` prefix and two lines; D8 says the two
commands are byte-identical.** D8 is the later decision and resolves O5 explicitly ("one
format, two paths ... no chance of the public and local forms disagreeing"), so the
implementation follows D8: `::tz` prints exactly the same single line as `!TZ`, with no
prefix. The GAMEMESSAGE colour already marks it as coming from a plugin. D4's sketch was
drawn before the format was settled and was not amended. Flagging rather than choosing
differently.

**R12. `OverlayPosition.DETACHED` is deprecated in RuneLite client 1.12.37.** The build
prints a deprecation note for it. It is kept anyway: decompiling `Overlay.setPosition`
confirms DETACHED is still the only value that sets `movable = true` with
`snappable = false`, which is exactly what D3 wants -- a HUD the user drags anywhere rather
than one that snaps to a screen corner. `DYNAMIC` is not movable at all and every anchored
position snaps. The warning is deliberately left unsuppressed so that an actual removal is
noticed at the next client bump; if it goes, the overlay needs a new home and D3's "movable"
promise has to be re-checked.

**R11. `ClockStore` defines the persistence seam but nothing implements it yet.** Commit 8
deliberately stops at the interface: the ConfigManager-backed implementation, the config
group and the serialisation format all belong to commit 9. Until that lands, the clock list
exists only in tests. Note also that `setClocks` marks the store seeded, so the very first
thing a commit 9 implementation must get right is persisting the seeded flag separately from
the list -- if the flag is derived from the list, D10's "a cleared list stays cleared" breaks.

**R10. The core clock class is named `WorldClock`, not `Clock` as PLAN.md's diagram has it.**
Every class in the core takes an injected `java.time.Clock`, so a type named `Clock` in the
same package would have to be disambiguated at every use site. Renamed rather than fought
with. PLAN.md was not edited.

**R9. The grammar accepts no date, so D8's omitted year stays safe -- for now.** O4 leaves
open whether `!TZ EST 20:30 sat > NZST` should work. Commit 6 does not implement it: a time
is always interpreted on today's date in the source zone. If dates are ever added, D8's
decision to omit the year has to be revisited in the same change.

**R8. `!tz` with no arguments returns NO_ARGUMENTS and no opinion.** O4 has not settled
whether that should print a usage hint or dump the user's clock list. The parser reports the
condition and leaves the choice to the chat handler in commit 13/14. Commit 13 chose the
usage hint, for `::tz` only: it is the cheaper mistake to recover from, and dumping the
clock list would duplicate the panel and the HUD. On the `!TZ` path nothing is printed at
all, because rewriting someone's public message into an error message locally is worse than
leaving the line they typed alone. O4 can still overrule this in one place.

**R7. Bare one and two digit numbers are not accepted as times.** `!tz 8 > nzst` is read as
the zone token "8", not as 08:00. D5's grammar lists `HHMM` and `H[:MM](am|pm)`, so a naked
`8` is not strictly a time, and treating it as one would let a digit swallow a zone token.
Three and four digit forms (`830`, `2030`) do work. Users who type `!tz 8 > nzst` will get an
"unknown zone: 8" message, which is at least honest but may read oddly.

**R6. Multi-word city names use longest match, with two rough edges (O4).** The parser tries
the longest leading phrase that the alias table knows, so "new york 8pm" splits correctly.
Two consequences worth knowing: (a) `!tz new york city > tokyo` matches "new york" and then
reports "city" as a bad time rather than as an unknown zone -- the message will be confusing;
(b) because `to` is a separator, no multi-word alias may contain the standalone word "to".
None currently does, and a test would not catch a future one being added. O4 is still open;
this is the implementation, not the decision.

**R5. D10's sketch shows `Local` and `Jagex (UTC)` rows without an abbreviation, D9 shows
every row with one.** The formatter always appends the abbreviation, because suppressing it
for two particular rows is exactly the special-casing D10 forbids and D7 wants the live
abbreviation visible next to the time. The visible consequence is `Jagex (UTC)  08:30 UTC`,
which reads slightly redundant. Cosmetic; flagging rather than choosing.

**R4. D9 does not pin the shape of 12-hour output.** It only says there is a toggle.
Commit 4 renders `8:30 AM EDT` (`h:mm a`, English, no leading zero, uppercase meridiem).
Unlike D8's converter string this is not a stated contract, so it is safe to change, but the
tests assert it exactly and would need updating.


## Resolved

**R2. The `Local` clock froze its zone at seeding time.** Resolved by D12: the row now
stores a `SYSTEM` sentinel and resolves against the machine when displayed, so it stays
right if the user moves country. Implementing it surfaced a real bug -- the config store
dropped the row because `SYSTEM` is not a valid IANA id -- which an existing test caught.


**R3. `GMT` resolved to `Europe/London`, contradicting what the user typed.** Fixed: `gmt`
now points at the `UTC` zone, while `london`/`uk`/`britain` keep `Europe/London`. D1
amended in place with the reasoning. `!TZ GMT 20:30` no longer answers in BST.


**R1. Scaffold directories were created with literal backslashes in their names.** Commit 1
left `src/main/java/nz\/tate\/timezonehud` (a directory literally named `nz\`) instead of
`src/main/java/nz/tate/timezonehud`. They were empty, so git had not recorded them. Removed
and recreated correctly in commit 2. Worth a glance at any other path the scaffold wrote.

## Accepted

**A2. DST gap and overlap times take java.time's default resolution, with no warning.**
OPEN-QUESTIONS O7 is unresolved, so commit 7 pins the JDK's behaviour rather than inventing
a policy:

- **Spring-forward gap.** A wall-clock time that never happens is pushed forward by the
  length of the gap. `!tz EST 02:30 > UTC` on 8 March 2026 answers as though 03:30 EDT had
  been typed, silently.
- **Fall-back overlap.** A wall-clock time that happens twice resolves to the *earlier*
  offset. `!tz EST 01:30 > UTC` on 1 November 2026 answers 05:30 UTC (the -04:00 pass), not
  06:30 UTC.

Both are tested so a JDK change would be caught. The overlap case is partly self-describing
because D8 prints the live abbreviation, so the answer says `EDT` and a reader who knows the
date can tell which pass it took; the gap case has no such tell. Accepted as-is until O7 is
settled -- adding a warning is a small, isolated change to TimeConverter if wanted.

**A1. Several curated zones share one display name.** `Europe/Paris`, `Europe/Berlin`,
`Europe/Madrid` and friends are all labelled "Central Europe" per D7's "label by zone, not
by city" rule. Two clocks added from different cities in that band therefore render
identical rows. D2's rename feature is the escape hatch. Accepted: the alternative is
city-shaped labels, which D7 explicitly rejects.
