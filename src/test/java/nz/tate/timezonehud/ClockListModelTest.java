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

import com.google.gson.Gson;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import nz.tate.timezonehud.core.ClockRepository;
import nz.tate.timezonehud.core.WorldClock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import org.junit.Before;
import org.junit.Test;

public class ClockListModelTest
{
	private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-28T22:57:00Z"), ZoneId.of("Pacific/Auckland"));

	private MapConfigValues values;
	private ClockListModel model;

	@Before
	public void setUp()
	{
		values = new MapConfigValues();
		model = newModel();
	}

	private ClockListModel newModel()
	{
		ConfigClockStore store = new ConfigClockStore(values, new Gson());
		return new ClockListModel(new ClockRepository(store, FIXED));
	}

	@Test
	public void startsWithTheSeededClocks()
	{
		List<WorldClock> clocks = model.getClocks();

		assertEquals(2, clocks.size());
		assertEquals(ClockRepository.LOCAL_LABEL, clocks.get(0).getLabel());
		assertEquals(ClockRepository.JAGEX_LABEL, clocks.get(1).getLabel());
	}

	@Test
	public void repeatedReadsReturnTheSameListUntilSomethingChanges()
	{
		// The overlay leans on this: an unchanged list means nothing to re-render.
		assertSame(model.getClocks(), model.getClocks());
	}

	@Test
	public void anEditReplacesTheListRatherThanMutatingIt()
	{
		List<WorldClock> before = model.getClocks();

		model.addClock(new WorldClock(ZoneId.of("America/New_York"), "US Eastern"));

		assertNotSame(before, model.getClocks());
		assertEquals(2, before.size());
		assertEquals(3, model.getClocks().size());
	}

	@Test
	public void editsAreVisibleToTheNextSession()
	{
		model.addClock(new WorldClock(ZoneId.of("Asia/Tokyo"), "Japan"));
		model.renameClock(0, "home");
		model.removeClock(1);
		model.moveClock(1, 0);

		List<WorldClock> reloaded = newModel().getClocks();

		assertEquals(2, reloaded.size());
		assertEquals("Japan", reloaded.get(0).getLabel());
		assertEquals("home", reloaded.get(1).getLabel());
	}

	@Test
	public void listenersFireOnceForEachEdit()
	{
		AtomicInteger notifications = new AtomicInteger();
		Runnable listener = notifications::incrementAndGet;
		model.addListener(listener);

		model.addClock(new WorldClock(ZoneId.of("Europe/London"), "UK"));
		model.renameClock(0, "somewhere else");

		assertEquals(2, notifications.get());

		model.removeListener(listener);
		model.removeClock(0);

		assertEquals(2, notifications.get());
	}

	@Test
	public void clearingEveryClockSticks()
	{
		model.removeClock(1);
		model.removeClock(0);

		assertEquals(0, model.getClocks().size());
		assertEquals(0, newModel().getClocks().size());
	}
}
