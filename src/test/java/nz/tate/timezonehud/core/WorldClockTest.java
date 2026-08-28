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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

public class WorldClockTest
{
	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	@Test
	public void holdsAZoneAndALabel()
	{
		WorldClock clock = new WorldClock(NEW_YORK, "Raid team");

		assertEquals(NEW_YORK, clock.getZone());
		assertEquals("America/New_York", clock.getZoneId());
		assertEquals("Raid team", clock.getLabel());
	}

	@Test
	public void theZoneIdConstructorMatchesTheZoneConstructor()
	{
		assertEquals(new WorldClock(NEW_YORK, "Work"), new WorldClock("America/New_York", "Work"));
	}

	@Test
	public void aNewClockIsLabelledWithTheZonesCuratedName()
	{
		// D7: label by zone, not by city and not by IANA id.
		assertEquals("US Eastern", WorldClock.forZone(NEW_YORK).getLabel());
		assertEquals("New Zealand", WorldClock.forZone(ZoneId.of("Pacific/Auckland")).getLabel());
		assertEquals("Ushuaia", WorldClock.forZone(ZoneId.of("America/Argentina/Ushuaia")).getLabel());
	}

	@Test
	public void aClockCanBeBuiltStraightFromARegistryEntry()
	{
		ZoneEntry entry = ZoneRegistry.lookup("tokyo").getZone();
		WorldClock clock = WorldClock.forZone(entry);

		assertEquals(ZoneId.of("Asia/Tokyo"), clock.getZone());
		assertEquals("Japan", clock.getLabel());
	}

	@Test
	public void renamingLeavesTheZoneAlone()
	{
		WorldClock renamed = WorldClock.forZone(NEW_YORK).withLabel("Mum");

		assertEquals("Mum", renamed.getLabel());
		assertEquals(NEW_YORK, renamed.getZone());
	}

	@Test
	public void clocksCompareByValue()
	{
		WorldClock clock = new WorldClock(NEW_YORK, "Work");

		assertEquals(clock, new WorldClock(NEW_YORK, "Work"));
		assertEquals(clock.hashCode(), new WorldClock(NEW_YORK, "Work").hashCode());
		assertNotEquals(clock, new WorldClock(NEW_YORK, "Home"));
		assertNotEquals(clock, new WorldClock(ZoneId.of("UTC"), "Work"));
		assertNotEquals(clock, "Work");
	}

	@Test
	public void toStringNamesBothHalves()
	{
		assertEquals("Work (America/New_York)", new WorldClock(NEW_YORK, "Work").toString());
	}

	@Test
	public void aSeededClockRendersLikeAnyOtherRow()
	{
		// D10 wants no special-casing anywhere, rendering included.
		ClockFormatter formatter = new ClockFormatter(
			Clock.fixed(Instant.parse("2026-08-28T12:30:00Z"), ZoneId.of("UTC")), true);

		WorldClock jagex = new WorldClock(ZoneId.of("UTC"), ClockRepository.JAGEX_LABEL);
		assertEquals("Jagex (UTC)   12:30 UTC", formatter.formatRow(jagex.getLabel(), jagex.getZone(), 14));
	}

	@Test(expected = NullPointerException.class)
	public void aNullLabelIsRejected()
	{
		new WorldClock(NEW_YORK, null);
	}
}
