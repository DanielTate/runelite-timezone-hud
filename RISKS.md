# Risks, Issues and Concerns

Live record kept during the build. Newest first within each section.

## Open

**R9. The grammar accepts no date, so D8's omitted year stays safe -- for now.** O4 leaves
open whether `!TZ EST 20:30 sat > NZST` should work. Commit 6 does not implement it: a time
is always interpreted on today's date in the source zone. If dates are ever added, D8's
decision to omit the year has to be revisited in the same change.

**R8. `!tz` with no arguments returns NO_ARGUMENTS and no opinion.** O4 has not settled
whether that should print a usage hint or dump the user's clock list. The parser reports the
condition and leaves the choice to the chat handler in commit 13/14.

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

**R3. `GMT` resolves to `Europe/London`, which can contradict what the user typed.**
D1 states the mapping `GMT/BST/UK -> Europe/London`, so that is what commit 2 ships. The
consequence is that `!tz GMT 20:30 > NZST` in August is interpreted as 20:30 *London* time
(UTC+1) and D8 will print the abbreviation live at that instant, i.e. `20:30 BST`. The
output contradicts the input token. This is exactly the class of quiet wrongness D6 exists
to prevent, but D1 is explicit and D1 predates D6, so this has been implemented as written
rather than redesigned. Recommended fix if the user agrees: point `gmt` at the `UTC` zone
(display "GMT") and leave `uk`/`london`/`britain` on `Europe/London`. Not done unilaterally.

**R2. `Local` is seeded as a concrete zone id, so it does not follow a mid-session zone
change.** D10 says "Local is a real zone lookup (the system default) ... so it behaves
correctly if the user's machine changes zone mid-session", but it also says clocks are
ordinary rows with no special-casing and that the list persists. Those pull in opposite
directions: a persisted row has to store *something*, and storing a sentinel like `SYSTEM`
is the special-casing D10 forbids. Commit 8 resolves `ZoneId.systemDefault()` once, at
seeding time, and stores the resulting id. If the user's machine changes zone the row keeps
the old zone under the label "Local" until they edit it. Flagging rather than choosing.

**R1. Scaffold directories were created with literal backslashes in their names.** Commit 1
left `src/main/java/nz\/tate\/timezonehud` (a directory literally named `nz\`) instead of
`src/main/java/nz/tate/timezonehud`. They were empty, so git had not recorded them. Removed
and recreated correctly in commit 2. Worth a glance at any other path the scaffold wrote.

## Resolved

*(none yet)*

## Accepted

**A1. Several curated zones share one display name.** `Europe/Paris`, `Europe/Berlin`,
`Europe/Madrid` and friends are all labelled "Central Europe" per D7's "label by zone, not
by city" rule. Two clocks added from different cities in that band therefore render
identical rows. D2's rename feature is the escape hatch. Accepted: the alternative is
city-shaped labels, which D7 explicitly rejects.
