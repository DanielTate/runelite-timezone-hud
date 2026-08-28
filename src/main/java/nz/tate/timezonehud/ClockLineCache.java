/*
 * Copyright (c) 2026, dan <dan@tate.nz>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package nz.tate.timezonehud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import nz.tate.timezonehud.core.ClockFormatter;
import nz.tate.timezonehud.core.WorldClock;

/**
 * Formats the clock rows, and does it as rarely as it can get away with.
 *
 * <p>The HUD's {@code render()} runs every frame, and formatting a handful of dates sixty
 * times a second for a display that only changes once a minute would be pure waste. D9
 * excludes seconds precisely so that this holds: nothing on a row can change until the
 * minute does, or until the user edits the list or flips the 12/24-hour setting.
 *
 * <p>When nothing has changed the caller gets back the very same list object, so a caller
 * can decide whether to redraw with a reference comparison and no allocation at all.
 */
public class ClockLineCache
{
	private static final long MILLIS_PER_MINUTE = 60_000L;

	private final java.time.Clock clock;

	private List<ClockLine> lines = Collections.emptyList();
	private long builtAtMinute = Long.MIN_VALUE;
	private List<WorldClock> builtFrom = null;
	private boolean builtTwentyFourHour;
	private int rebuildCount;

	public ClockLineCache(java.time.Clock clock)
	{
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * @return the rows for these clocks, unmodifiable, and identical to the previous result
	 * whenever nothing that affects them has changed
	 */
	public List<ClockLine> getLines(List<WorldClock> clocks, boolean twentyFourHour)
	{
		Objects.requireNonNull(clocks, "clocks");

		// Every modern zone is a whole number of minutes from UTC, so one epoch minute is
		// one displayed minute everywhere, midnight rollovers included.
		long minute = clock.millis() / MILLIS_PER_MINUTE;

		if (minute == builtAtMinute
			&& twentyFourHour == builtTwentyFourHour
			&& clocks.equals(builtFrom))
		{
			return lines;
		}

		ClockFormatter formatter = new ClockFormatter(clock, twentyFourHour);
		List<ClockLine> rebuilt = new ArrayList<>(clocks.size());
		for (WorldClock worldClock : clocks)
		{
			rebuilt.add(new ClockLine(worldClock.getLabel(), formatter.formatTime(worldClock.getZone())));
		}

		lines = Collections.unmodifiableList(rebuilt);
		builtAtMinute = minute;
		builtFrom = new ArrayList<>(clocks);
		builtTwentyFourHour = twentyFourHour;
		rebuildCount++;

		return lines;
	}

	/**
	 * How many times the rows have actually been formatted. Only the tests care -- it is
	 * how "does not rebuild every frame" is asserted rather than assumed.
	 */
	public int getRebuildCount()
	{
		return rebuildCount;
	}
}
