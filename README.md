# Timezone HUD

World clocks on a movable in-game overlay, a side panel to edit them, and a chat command
that converts times between zones.

Built for the case the abbreviation tables get wrong: `EST` means the US Eastern *zone*, so
it answers in EDT in August and EST in January.

## Install

From the RuneLite Plugin Hub, or build it yourself:

```
./gradlew build      # requires JDK 17; the plugin targets Java 11
```

## Clocks

Open the clock icon in the RuneLite sidebar. Search by city, abbreviation or IANA id
(`auckland`, `EST`, `Europe/Paris`), click a result to add it, then rename, reorder or
remove it. Every clock you add shows on the overlay, in panel order.

Drag the overlay with alt held; RuneLite remembers where you put it.

New installs start with **Local** and **Jagex (UTC)**. They are ordinary rows — delete them
if you want, and they stay deleted.

## Settings

| Setting | Default | What it does |
|---|---|---|
| Show clock overlay | on | Hides the in-game overlay. Your clocks and the panel are untouched. |
| 24-hour time | on | `13:30` rather than `1:30 PM`. Seconds are never shown. |

## Commands

`::tz` answers privately. `!TZ` sends the line to chat as you typed it, and anyone running
this plugin sees the answer in its place.

```
::tz EST 20:30 > NZST
20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)
```

Source, time and target are each optional — anything left out is your own zone.

```
::tz 20:30 > NZST      from your zone
::tz EST 20:30         into your zone
::tz EST > NZST        converts right now
::tz EST 8:30pm > NZST 12-hour input
```

Separators are `>`, `->` or `to`. Ambiguous abbreviations are refused rather than guessed:

```
::tz IST 20:30 > NZST
"IST" is ambiguous: India (kolkata), Ireland (dublin) or Israel (jerusalem). Use one of those instead.
```

`!TZ` never touches the message on its way out — only the copy your client received is
redrawn, so what other players see is exactly what you typed.

## Verifying

The tests cover everything that does not need a running client. Behaviour in-game — the
overlay, the panel and both commands — has to be confirmed by a human with RuneLite open.

## Licence

BSD-2-Clause. See `LICENSE`.
