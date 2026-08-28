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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nz.tate.timezonehud.core.WorldClock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class ClockLineCacheTest
{
	/** 10:57 on Saturday in Auckland, which is still Friday evening in New York. */
	private static final Instant NOW = Instant.parse("2026-08-28T22:57:00Z");

	private static final List<WorldClock> CLOCKS = Arrays.asList(
		new WorldClock(ZoneId.of("Pacific/Auckland"), "Local"),
		new WorldClock(ZoneId.of("UTC"), "Jagex (UTC)"),
		new WorldClock(ZoneId.of("America/New_York"), "US Eastern"));

	private AdjustableClock clock;
	private ClockLineCache cache;

	@Before
	public void setUp()
	{
		clock = new AdjustableClock(NOW, ZoneId.of("Pacific/Auckland"));
		cache = new ClockLineCache(clock);
	}

	@Test
	public void formatsOneRowPerClock()
	{
		List<ClockLine> lines = cache.getLines(CLOCKS, true);

		assertEquals(3, lines.size());
		assertEquals("Local", lines.get(0).getLabel());
		assertEquals("10:57 NZST", lines.get(0).getTime());
		assertEquals("22:57 UTC  Fri 28", lines.get(1).getTime());
		assertEquals("18:57 EDT  Fri 28", lines.get(2).getTime());
	}

	@Test
	public void repeatedFramesWithinTheSameMinuteDoNoWork()
	{
		List<ClockLine> first = cache.getLines(CLOCKS, true);

		for (int frame = 0; frame < 200; frame++)
		{
			assertSame(first, cache.getLines(CLOCKS, true));
		}

		assertEquals(1, cache.getRebuildCount());
	}

	@Test
	public void secondsTickingDoesNotRebuild()
	{
		cache.getLines(CLOCKS, true);

		clock.advance(Duration.ofSeconds(59));
		cache.getLines(CLOCKS, true);

		assertEquals(1, cache.getRebuildCount());
	}

	@Test
	public void theMinuteTickingOverRebuilds()
	{
		cache.getLines(CLOCKS, true);

		clock.advance(Duration.ofMinutes(1));
		List<ClockLine> later = cache.getLines(CLOCKS, true);

		assertEquals(2, cache.getRebuildCount());
		assertEquals("10:58 NZST", later.get(0).getTime());
	}

	@Test
	public void editingTheListRebuilds()
	{
		cache.getLines(CLOCKS, true);

		List<WorldClock> edited = new ArrayList<>(CLOCKS);
		edited.add(new WorldClock(ZoneId.of("Asia/Tokyo"), "Japan"));
		List<ClockLine> lines = cache.getLines(edited, true);

		assertEquals(2, cache.getRebuildCount());
		assertEquals(4, lines.size());
		assertEquals("Japan", lines.get(3).getLabel());
	}

	@Test
	public void renamingAClockRebuilds()
	{
		cache.getLines(CLOCKS, true);

		List<WorldClock> renamed = new ArrayList<>(CLOCKS);
		renamed.set(0, renamed.get(0).withLabel("home"));

		assertEquals("home", cache.getLines(renamed, true).get(0).getLabel());
		assertEquals(2, cache.getRebuildCount());
	}

	@Test
	public void switchingToTwelveHourRebuilds()
	{
		cache.getLines(CLOCKS, true);

		List<ClockLine> lines = cache.getLines(CLOCKS, false);

		assertEquals(2, cache.getRebuildCount());
		assertEquals("10:57 AM NZST", lines.get(0).getTime());
	}

	@Test
	public void anEmptyListGivesNoRows()
	{
		assertTrue(cache.getLines(Collections.<WorldClock>emptyList(), true).isEmpty());
	}

	@Test
	public void callersCannotEditTheReturnedRows()
	{
		List<ClockLine> lines = cache.getLines(CLOCKS, true);

		try
		{
			lines.clear();
			throw new AssertionError("the cached rows must not be modifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// The overlay hands this list straight on; a caller must not be able to
			// corrupt what the next frame will compare against.
		}
	}
}
