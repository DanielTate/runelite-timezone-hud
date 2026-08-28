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
package nz.tate.timezonehud.core;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * D10: Local and Jagex (UTC) are seeded once, as ordinary rows, guarded by a first-run flag
 * rather than by "is the list empty".
 */
public class ClockRepositoryTest
{
	private static final Instant NOW = Instant.parse("2026-08-28T12:30:00Z");
	private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");
	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	private InMemoryClockStore store;
	private ClockRepository repository;

	@Before
	public void setUp()
	{
		store = new InMemoryClockStore();
		repository = new ClockRepository(store, Clock.fixed(NOW, AUCKLAND));
	}

	private static List<String> labelsOf(List<WorldClock> clocks)
	{
		List<String> labels = new ArrayList<>();
		for (WorldClock clock : clocks)
		{
			labels.add(clock.getLabel());
		}
		return labels;
	}

	// -- First-run seeding -------------------------------------------------------------

	@Test
	public void firstRunSeedsLocalAndJagex()
	{
		List<WorldClock> clocks = repository.getClocks();

		assertEquals(Arrays.asList("Local", "Jagex (UTC)"), labelsOf(clocks));
		assertEquals(AUCKLAND, clocks.get(0).getZone());
		assertEquals(ZoneId.of("UTC"), clocks.get(1).getZone());
	}

	@Test
	public void localFollowsTheMachineRatherThanFreezingAtSeedingTime()
	{
		ClockRepository american = new ClockRepository(new InMemoryClockStore(), Clock.fixed(NOW, NEW_YORK));
		WorldClock local = american.getClocks().get(0);

		// The row stores a sentinel, not a resolved zone, so it cannot go stale when the
		// user moves country. It resolves against the machine at display time.
		assertTrue(local.followsSystemZone());
		assertEquals(WorldClock.SYSTEM_ZONE_ID, local.getZoneId());
		assertEquals(ZoneId.systemDefault(), local.getZone());
	}

	@Test
	public void localIsStillAnOrdinaryRow()
	{
		// D10 wants no special-casing: the seeded rows must rename, persist and delete
		// exactly like any other. Only zone resolution differs.
		WorldClock local = repository.getClocks().get(0);

		assertEquals("Somewhere else", local.withLabel("Somewhere else").getLabel());
		assertTrue(local.withLabel("Somewhere else").followsSystemZone());
	}

	@Test
	public void seedingMarksTheStoreAndDoesNotRepeat()
	{
		assertFalse(store.isSeeded());

		repository.getClocks();
		assertTrue(store.isSeeded());

		int savesAfterSeeding = store.getSaveCount();
		repository.getClocks();
		repository.getClocks();

		assertEquals(savesAfterSeeding, store.getSaveCount());
	}

	@Test
	public void aDeliberatelyClearedListStaysCleared()
	{
		repository.getClocks();
		repository.setClocks(Collections.<WorldClock>emptyList());

		assertTrue(repository.getClocks().isEmpty());
		assertTrue(repository.getClocks().isEmpty());
	}

	@Test
	public void removingBothSeededClocksIsALegitimateRestingState()
	{
		repository.getClocks();
		repository.removeClock(1);
		repository.removeClock(0);

		assertTrue(repository.getClocks().isEmpty());
	}

	@Test
	public void anAlreadySeededStoreIsLeftAlone()
	{
		store.saveClocks(Collections.singletonList(new WorldClock(NEW_YORK, "Work")));
		store.markSeeded();

		assertEquals(Collections.singletonList("Work"), labelsOf(repository.getClocks()));
	}

	@Test
	public void seedClocksIsPureAndDoesNotTouchTheStore()
	{
		List<WorldClock> seeded = ClockRepository.seedClocks(NEW_YORK);

		assertEquals(Arrays.asList("Local", "Jagex (UTC)"), labelsOf(seeded));
		assertFalse(store.isSeeded());
	}

	// -- The seeded rows are ordinary ---------------------------------------------------

	@Test
	public void theSeededRowsCanBeRenamedLikeAnyOther()
	{
		repository.getClocks();
		repository.renameClock(0, "Home");
		repository.renameClock(1, "Game");

		assertEquals(Arrays.asList("Home", "Game"), labelsOf(repository.getClocks()));
		assertEquals(AUCKLAND, repository.getClocks().get(0).getZone());
	}

	@Test
	public void theSeededRowsCanBeReorderedLikeAnyOther()
	{
		repository.getClocks();
		repository.moveClock(0, 1);

		assertEquals(Arrays.asList("Jagex (UTC)", "Local"), labelsOf(repository.getClocks()));
	}

	// -- List editing --------------------------------------------------------------------

	@Test
	public void clocksAreAddedToTheEndOfTheList()
	{
		repository.getClocks();
		repository.addClock(WorldClock.forZone(NEW_YORK));

		assertEquals(Arrays.asList("Local", "Jagex (UTC)", "US Eastern"), labelsOf(repository.getClocks()));
	}

	@Test
	public void removingTakesTheClockOutAndKeepsTheOrder()
	{
		repository.setClocks(Arrays.asList(
			new WorldClock(AUCKLAND, "One"),
			new WorldClock(NEW_YORK, "Two"),
			new WorldClock(ZoneId.of("UTC"), "Three")));

		repository.removeClock(1);

		assertEquals(Arrays.asList("One", "Three"), labelsOf(repository.getClocks()));
	}

	@Test
	public void movingShiftsOneClockAndSlidesTheRest()
	{
		repository.setClocks(Arrays.asList(
			new WorldClock(AUCKLAND, "One"),
			new WorldClock(NEW_YORK, "Two"),
			new WorldClock(ZoneId.of("UTC"), "Three")));

		repository.moveClock(2, 0);

		assertEquals(Arrays.asList("Three", "One", "Two"), labelsOf(repository.getClocks()));
	}

	@Test
	public void setClocksReplacesTheWholeList()
	{
		repository.getClocks();
		repository.setClocks(Collections.singletonList(new WorldClock(NEW_YORK, "Only")));

		assertEquals(Collections.singletonList("Only"), labelsOf(repository.getClocks()));
	}

	@Test
	public void everyChangeRoundTripsThroughTheStore()
	{
		repository.getClocks();
		repository.addClock(WorldClock.forZone(NEW_YORK));
		repository.renameClock(2, "Raid team");

		// A second repository sharing the store sees exactly the same list, as it would
		// after a client restart.
		ClockRepository reopened = new ClockRepository(store, Clock.fixed(NOW, AUCKLAND));

		assertEquals(labelsOf(repository.getClocks()), labelsOf(reopened.getClocks()));
		assertEquals("Raid team", reopened.getClocks().get(2).getLabel());
	}

	// -- Guards ----------------------------------------------------------------------------

	@Test(expected = UnsupportedOperationException.class)
	public void theReturnedListIsASnapshotNotALiveHandle()
	{
		repository.getClocks().add(WorldClock.forZone(NEW_YORK));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void removingAClockThatIsNotThereFails()
	{
		repository.getClocks();
		repository.removeClock(9);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void movingToAPositionThatIsNotThereFails()
	{
		repository.getClocks();
		repository.moveClock(0, 9);
	}

	@Test(expected = NullPointerException.class)
	public void aNullStoreIsRejected()
	{
		new ClockRepository(null, Clock.fixed(NOW, AUCKLAND));
	}

	@Test(expected = NullPointerException.class)
	public void aNullClockIsRejected()
	{
		new ClockRepository(store, null);
	}
}
