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
import java.time.LocalTime;
import java.time.ZoneId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * DST boundaries and the date line are the interesting cases here, not the happy path.
 */
public class TimeConverterTest
{
	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");
	private static final ZoneId LONDON = ZoneId.of("Europe/London");
	private static final ZoneId UTC = ZoneId.of("UTC");

	/** Friday 28 August 2026, 20:30 in New York. */
	private static final Instant AUGUST_EVENING_IN_NEW_YORK = Instant.parse("2026-08-29T00:30:00Z");
	/** Thursday 15 January 2026, 20:30 in New York. */
	private static final Instant JANUARY_EVENING_IN_NEW_YORK = Instant.parse("2026-01-16T01:30:00Z");

	private static TimeConverter converter(Instant now, ZoneId localZone)
	{
		return new TimeConverter(Clock.fixed(now, localZone));
	}

	// -- The headline case ------------------------------------------------------------

	@Test
	public void newYorkToNewZealandCrossesTheDateLineForwards()
	{
		Conversion conversion = converter(AUGUST_EVENING_IN_NEW_YORK, NEW_YORK)
			.convert(NEW_YORK, AUCKLAND, LocalTime.of(20, 30));

		assertEquals("20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)", conversion.format());
		assertTrue(conversion.rollsOverToAnotherDay());
	}

	@Test
	public void newZealandToNewYorkCrossesTheDateLineBackwards()
	{
		Conversion conversion = converter(AUGUST_EVENING_IN_NEW_YORK, AUCKLAND)
			.convert(AUCKLAND, NEW_YORK, LocalTime.of(10, 0));

		assertEquals("10:00 NZST (Sat 29 Aug) = 18:00 EDT (Fri 28 Aug)", conversion.format());
		assertTrue(conversion.rollsOverToAnotherDay());
	}

	// -- DST boundaries ---------------------------------------------------------------

	@Test
	public void theSameCommandGivesEdtInAugustAndEstInJanuary()
	{
		Conversion august = converter(AUGUST_EVENING_IN_NEW_YORK, NEW_YORK)
			.convert(NEW_YORK, AUCKLAND, LocalTime.of(20, 30));
		Conversion january = converter(JANUARY_EVENING_IN_NEW_YORK, NEW_YORK)
			.convert(NEW_YORK, AUCKLAND, LocalTime.of(20, 30));

		assertEquals("20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)", august.format());
		assertEquals("20:30 EST (Thu 15 Jan) = 14:30 NZDT (Fri 16 Jan)", january.format());

		// Same wall-clock time, different offset gap: 16 hours in August, 18 in January.
		assertEquals(-4 * 60, august.getSource().getOffset().getTotalSeconds() / 60);
		assertEquals(-5 * 60, january.getSource().getOffset().getTotalSeconds() / 60);
	}

	@Test
	public void londonFlipsBetweenBstAndGmt()
	{
		assertEquals("12:00 BST (Sat 29 Aug) = 11:00 UTC (Sat 29 Aug)",
			converter(AUGUST_EVENING_IN_NEW_YORK, LONDON).convert(LONDON, UTC, LocalTime.of(12, 0)).format());

		assertEquals("12:00 GMT (Fri 16 Jan) = 12:00 UTC (Fri 16 Jan)",
			converter(JANUARY_EVENING_IN_NEW_YORK, LONDON).convert(LONDON, UTC, LocalTime.of(12, 0)).format());
	}

	/**
	 * OPEN-QUESTIONS O7 is unresolved, so this pins what java.time does by default rather
	 * than inventing a policy. See RISKS A2.
	 */
	@Test
	public void aTimeInsideTheSpringForwardGapIsPushedForwards()
	{
		// 02:30 on 8 March 2026 never happens in New York: the clocks jump 02:00 -> 03:00.
		Instant duringTheGapDay = Instant.parse("2026-03-08T12:00:00Z");
		Conversion conversion = converter(duringTheGapDay, NEW_YORK).convert(NEW_YORK, UTC, LocalTime.of(2, 30));

		assertEquals(LocalTime.of(3, 30), conversion.getSource().toLocalTime());
		assertEquals("03:30 EDT (Sun 8 Mar) = 07:30 UTC (Sun 8 Mar)", conversion.format());
	}

	/**
	 * The other half of O7: on a fall-back day the same wall-clock time happens twice and
	 * java.time silently takes the earlier one.
	 */
	@Test
	public void aTimeInsideTheFallBackOverlapTakesTheEarlierOffset()
	{
		// 01:30 on 1 November 2026 happens twice in New York, once at -04:00 and once at -05:00.
		Instant duringTheOverlapDay = Instant.parse("2026-11-01T12:00:00Z");
		Conversion conversion = converter(duringTheOverlapDay, NEW_YORK).convert(NEW_YORK, UTC, LocalTime.of(1, 30));

		assertEquals(-4 * 3600, conversion.getSource().getOffset().getTotalSeconds());
		assertEquals("01:30 EDT (Sun 1 Nov) = 05:30 UTC (Sun 1 Nov)", conversion.format());
	}

	@Test
	public void theNewZealandSpringForwardGapBehavesTheSameWay()
	{
		// New Zealand jumps 02:00 -> 03:00 on 27 September 2026.
		Instant duringTheGapDay = Instant.parse("2026-09-26T14:00:00Z");
		Conversion conversion = converter(duringTheGapDay, AUCKLAND).convert(AUCKLAND, UTC, LocalTime.of(2, 30));

		assertEquals(LocalTime.of(3, 30), conversion.getSource().toLocalTime());
		assertEquals("03:30 NZDT (Sun 27 Sep) = 14:30 UTC (Sat 26 Sep)", conversion.format());
	}

	// -- Dates are taken in the source zone, not the user's ---------------------------

	@Test
	public void theDateIsTodayInTheSourceZoneNotWhereTheUserIs()
	{
		// It is Saturday lunchtime in New Zealand, but still Friday evening in New York.
		TimeConverter converter = converter(AUGUST_EVENING_IN_NEW_YORK, AUCKLAND);

		assertEquals("2026-08-28", converter.convert(NEW_YORK, UTC, LocalTime.of(20, 30))
			.getSource().toLocalDate().toString());
		assertEquals("2026-08-29", converter.convert(AUCKLAND, UTC, LocalTime.of(12, 30))
			.getSource().toLocalDate().toString());
	}

	// -- "now" and identity -----------------------------------------------------------

	@Test
	public void aNullTimeConvertsTheCurrentMoment()
	{
		Conversion conversion = converter(AUGUST_EVENING_IN_NEW_YORK, NEW_YORK).convert(NEW_YORK, AUCKLAND, null);

		assertEquals(AUGUST_EVENING_IN_NEW_YORK, conversion.getSource().toInstant());
		assertEquals("20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)", conversion.format());
	}

	@Test
	public void convertingAZoneToItselfChangesNothing()
	{
		Conversion conversion = converter(AUGUST_EVENING_IN_NEW_YORK, NEW_YORK)
			.convert(NEW_YORK, NEW_YORK, LocalTime.of(20, 30));

		assertEquals(conversion.getSource(), conversion.getTarget());
		assertFalse(conversion.rollsOverToAnotherDay());
		assertEquals("20:30 EDT (Fri 28 Aug) = 20:30 EDT (Fri 28 Aug)", conversion.format());
	}

	@Test
	public void bothSidesAlwaysDescribeTheSameInstant()
	{
		Conversion conversion = converter(AUGUST_EVENING_IN_NEW_YORK, NEW_YORK)
			.convert(NEW_YORK, AUCKLAND, LocalTime.of(20, 30));

		assertEquals(conversion.getSource().toInstant(), conversion.getTarget().toInstant());
	}

	// -- Local zone defaults, per D5 ---------------------------------------------------

	@Test
	public void theClockSuppliesTheUsersOwnZone()
	{
		assertEquals(AUCKLAND, converter(AUGUST_EVENING_IN_NEW_YORK, AUCKLAND).getLocalZone());
	}

	@Test
	public void aNullSourceOrTargetFallsBackToLocal()
	{
		TimeConverter converter = converter(AUGUST_EVENING_IN_NEW_YORK, AUCKLAND);

		Conversion fromLocal = converter.convert(new ConversionRequest(null, "EST", LocalTime.of(12, 30)), null, NEW_YORK);
		assertEquals("12:30 NZST (Sat 29 Aug) = 20:30 EDT (Fri 28 Aug)", fromLocal.format());

		Conversion toLocal = converter.convert(new ConversionRequest("EST", null, LocalTime.of(20, 30)), NEW_YORK, null);
		assertEquals("20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)", toLocal.format());

		Conversion bothLocal = converter.convert(new ConversionRequest(null, null, null), null, null);
		assertEquals(AUCKLAND, bothLocal.getSource().getZone());
		assertEquals(AUCKLAND, bothLocal.getTarget().getZone());
	}

	@Test
	public void aRequestWithNoTimeConvertsNow()
	{
		Conversion conversion = converter(AUGUST_EVENING_IN_NEW_YORK, NEW_YORK)
			.convert(new ConversionRequest("EST", "NZST", null), NEW_YORK, AUCKLAND);

		assertEquals(AUGUST_EVENING_IN_NEW_YORK, conversion.getSource().toInstant());
	}

	// -- Guards -------------------------------------------------------------------------

	@Test(expected = NullPointerException.class)
	public void aNullClockIsRejected()
	{
		new TimeConverter(null);
	}

	@Test(expected = NullPointerException.class)
	public void aNullSourceZoneIsRejected()
	{
		converter(AUGUST_EVENING_IN_NEW_YORK, UTC).convert(null, UTC, LocalTime.NOON);
	}
}
