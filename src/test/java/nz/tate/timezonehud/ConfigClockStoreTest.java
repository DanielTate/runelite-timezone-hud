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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nz.tate.timezonehud.core.ClockRepository;
import nz.tate.timezonehud.core.WorldClock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ConfigClockStoreTest
{
	private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-28T22:57:00Z"), ZoneId.of("Pacific/Auckland"));

	private final MapConfigValues values = new MapConfigValues();
	private final Gson gson = new Gson();

	private ConfigClockStore newStore()
	{
		return new ConfigClockStore(values, gson);
	}

	@Test
	public void emptyConfigHasNoClocksAndIsNotSeeded()
	{
		ConfigClockStore store = newStore();

		assertEquals(Collections.emptyList(), store.loadClocks());
		assertFalse(store.isSeeded());
	}

	@Test
	public void clocksRoundTripThroughConfig()
	{
		List<WorldClock> saved = Arrays.asList(
			new WorldClock(ZoneId.of("Pacific/Auckland"), "Local"),
			new WorldClock(ZoneId.of("UTC"), "Jagex (UTC)"),
			new WorldClock(ZoneId.of("America/New_York"), "raid team"));

		newStore().saveClocks(saved);

		// A fresh store over the same config, so nothing is being read out of memory.
		assertEquals(saved, newStore().loadClocks());
	}

	@Test
	public void labelsWithAwkwardCharactersSurvive()
	{
		List<WorldClock> saved = Collections.singletonList(
			new WorldClock(ZoneId.of("UTC"), "mum's \"place\", | \\ and, commas"));

		newStore().saveClocks(saved);

		assertEquals(saved, newStore().loadClocks());
	}

	@Test
	public void orderIsPreserved()
	{
		List<WorldClock> saved = Arrays.asList(
			new WorldClock(ZoneId.of("UTC"), "one"),
			new WorldClock(ZoneId.of("Europe/London"), "two"),
			new WorldClock(ZoneId.of("Asia/Tokyo"), "three"));

		newStore().saveClocks(saved);

		List<WorldClock> loaded = newStore().loadClocks();
		assertEquals("one", loaded.get(0).getLabel());
		assertEquals("two", loaded.get(1).getLabel());
		assertEquals("three", loaded.get(2).getLabel());
	}

	@Test
	public void seededFlagIsStoredApartFromTheList()
	{
		ConfigClockStore store = newStore();
		store.saveClocks(Collections.<WorldClock>emptyList());

		assertFalse("saving clocks must not imply seeding", store.isSeeded());

		store.markSeeded();
		assertTrue(newStore().isSeeded());
		assertEquals(Collections.emptyList(), newStore().loadClocks());
	}

	@Test
	public void clocksTheUserDeletedStayDeleted()
	{
		// R11 / D10: seeding is guarded by the flag, never by "is the list empty".
		ClockRepository first = new ClockRepository(newStore(), FIXED);
		assertEquals(2, first.getClocks().size());

		first.setClocks(Collections.<WorldClock>emptyList());

		ClockRepository nextSession = new ClockRepository(newStore(), FIXED);
		assertEquals(Collections.emptyList(), nextSession.getClocks());
	}

	@Test
	public void unreadableConfigIsIgnoredRatherThanThrown()
	{
		values.put(ConfigClockStore.KEY_CLOCKS, "this is not json");

		assertEquals(Collections.emptyList(), newStore().loadClocks());
	}

	@Test
	public void aClockWhoseZoneNoLongerExistsIsDroppedNotTheWholeList()
	{
		values.put(ConfigClockStore.KEY_CLOCKS,
			"[{\"zoneId\":\"Mars/Olympus_Mons\",\"label\":\"gone\"},{\"zoneId\":\"UTC\",\"label\":\"kept\"}]");

		List<WorldClock> loaded = newStore().loadClocks();

		assertEquals(1, loaded.size());
		assertEquals("kept", loaded.get(0).getLabel());
	}

	@Test
	public void savingTheSeededListMakesItReadableAgain()
	{
		ConfigClockStore store = newStore();
		List<WorldClock> seeded = new ArrayList<>(ClockRepository.seedClocks(ZoneId.of("Pacific/Auckland")));

		store.saveClocks(seeded);

		assertEquals(seeded, newStore().loadClocks());
	}
}
