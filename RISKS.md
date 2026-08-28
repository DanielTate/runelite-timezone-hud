# Risks, Issues and Concerns

Live record kept during the build. Newest first within each section.

## Open

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
