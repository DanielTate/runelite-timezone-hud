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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * D8's output string is a user-visible contract, so these assert it exactly.
 */
public class ConversionFormatTest
{
	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");
	private static final ZoneId LONDON = ZoneId.of("Europe/London");
	private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
	private static final ZoneId UTC = ZoneId.of("UTC");

	private static ZonedDateTime at(String date, String time, ZoneId zone)
	{
		return ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone);
	}

	@Test
	public void producesTheExactStringFromD8()
	{
		ZonedDateTime source = at("2026-08-28", "20:30", NEW_YORK);
		ZonedDateTime target = source.withZoneSameInstant(AUCKLAND);

		assertEquals("20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)", ConversionFormat.format(source, target));
	}

	@Test
	public void bothDaysArePresentEvenWhenTheyMatch()
	{
		ZonedDateTime source = at("2026-08-28", "14:00", NEW_YORK);
		ZonedDateTime target = source.withZoneSameInstant(CHICAGO);

		assertEquals("14:00 EDT (Fri 28 Aug) = 13:00 CDT (Fri 28 Aug)", ConversionFormat.format(source, target));
	}

	@Test
	public void rollsForwardOverTheDateLine()
	{
		ZonedDateTime source = at("2026-08-28", "23:45", NEW_YORK);
		ZonedDateTime target = source.withZoneSameInstant(AUCKLAND);

		assertEquals("23:45 EDT (Fri 28 Aug) = 15:45 NZST (Sat 29 Aug)", ConversionFormat.format(source, target));
	}

	@Test
	public void rollsBackwardOverTheDateLine()
	{
		ZonedDateTime source = at("2026-08-29", "10:00", AUCKLAND);
		ZonedDateTime target = source.withZoneSameInstant(NEW_YORK);

		assertEquals("10:00 NZST (Sat 29 Aug) = 18:00 EDT (Fri 28 Aug)", ConversionFormat.format(source, target));
	}

	// -- The abbreviation is the one live at the converted instant --------------------

	@Test
	public void aJanuaryConversionPrintsEstNotEdt()
	{
		ZonedDateTime source = at("2026-01-15", "20:30", NEW_YORK);
		ZonedDateTime target = source.withZoneSameInstant(AUCKLAND);

		assertEquals("20:30 EST (Thu 15 Jan) = 14:30 NZDT (Fri 16 Jan)", ConversionFormat.format(source, target));
	}

	@Test
	public void londonPrintsBstInSummerAndGmtInWinter()
	{
		ZonedDateTime summer = at("2026-08-28", "12:00", UTC);
		assertEquals("12:00 UTC (Fri 28 Aug) = 13:00 BST (Fri 28 Aug)",
			ConversionFormat.format(summer, summer.withZoneSameInstant(LONDON)));

		ZonedDateTime winter = at("2026-01-15", "12:00", UTC);
		assertEquals("12:00 UTC (Thu 15 Jan) = 12:00 GMT (Thu 15 Jan)",
			ConversionFormat.format(winter, winter.withZoneSameInstant(LONDON)));
	}

	@Test
	public void theTwoSidesCanCarryDifferentSeasons()
	{
		// Converting a January instant while the northern zone is on standard time and the
		// southern one is on daylight time -- both abbreviations must reflect their own side.
		ZonedDateTime source = at("2026-01-15", "08:00", AUCKLAND);
		ZonedDateTime target = source.withZoneSameInstant(NEW_YORK);

		assertEquals("08:00 NZDT (Thu 15 Jan) = 14:00 EST (Wed 14 Jan)", ConversionFormat.format(source, target));
	}

	// -- Shape details ----------------------------------------------------------------

	@Test
	public void timesAreTwentyFourHourAndZeroPadded()
	{
		ZonedDateTime source = at("2026-08-28", "08:05", NEW_YORK);
		assertEquals("08:05 EDT (Fri 28 Aug)", ConversionFormat.side(source));
	}

	@Test
	public void daysOfTheMonthAreNotZeroPadded()
	{
		ZonedDateTime source = at("2026-08-01", "09:00", NEW_YORK);
		assertEquals("09:00 EDT (Sat 1 Aug)", ConversionFormat.side(source));
	}

	@Test
	public void theYearIsNeverPrinted()
	{
		ZonedDateTime source = at("2026-08-28", "20:30", NEW_YORK);
		ZonedDateTime target = source.withZoneSameInstant(AUCKLAND);

		assertEquals(-1, ConversionFormat.format(source, target).indexOf("2026"));
	}

	@Test
	public void sameZoneConversionIsAnIdentityLine()
	{
		ZonedDateTime source = at("2026-08-28", "20:30", NEW_YORK);
		assertEquals("20:30 EDT (Fri 28 Aug) = 20:30 EDT (Fri 28 Aug)", ConversionFormat.format(source, source));
	}
}
