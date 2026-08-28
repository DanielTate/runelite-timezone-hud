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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * D9: 24-hour by default, 12-hour on a toggle, never seconds, date only when it differs
 * from the user's own.
 */
public class ClockFormatterTest
{
	/** Friday 28 August 2026, 12:30 UTC. */
	private static final Instant AUGUST_NOON = Instant.parse("2026-08-28T12:30:00Z");
	/** Thursday 15 January 2026, 12:30 UTC. */
	private static final Instant JANUARY_NOON = Instant.parse("2026-01-15T12:30:00Z");

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	private static final ZoneId LONDON = ZoneId.of("Europe/London");
	private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");
	private static final ZoneId UTC = ZoneId.of("UTC");

	private static ClockFormatter formatter(Instant now, ZoneId localZone, boolean twentyFourHour)
	{
		return new ClockFormatter(Clock.fixed(now, localZone), twentyFourHour);
	}

	// -- 24-hour, the default --------------------------------------------------------

	@Test
	public void rendersTimeAndTheLiveAbbreviation()
	{
		ClockFormatter formatter = formatter(AUGUST_NOON, UTC, true);

		assertEquals("08:30 EDT", formatter.formatTime(NEW_YORK));
		assertEquals("13:30 BST", formatter.formatTime(LONDON));
		assertEquals("12:30 UTC", formatter.formatTime(UTC));
	}

	@Test
	public void theAbbreviationFollowsTheSeason()
	{
		assertEquals("07:30 EST", formatter(JANUARY_NOON, UTC, true).formatTime(NEW_YORK));
		assertEquals("12:30 GMT", formatter(JANUARY_NOON, UTC, true).formatTime(LONDON));
		assertEquals("01:30 NZDT  Fri 16", formatter(JANUARY_NOON, UTC, true).formatTime(AUCKLAND));
	}

	@Test
	public void secondsAreNeverShown()
	{
		Instant withSeconds = Instant.parse("2026-08-28T12:30:47Z");
		assertEquals("08:30 EDT", formatter(withSeconds, UTC, true).formatTime(NEW_YORK));
	}

	@Test
	public void midnightIsRenderedAsZeroZeroInTwentyFourHour()
	{
		assertEquals("00:30 NZST  Sat 29", formatter(AUGUST_NOON, UTC, true).formatTime(AUCKLAND));
	}

	// -- 12-hour toggle ---------------------------------------------------------------

	@Test
	public void twelveHourModeUsesAmAndPm()
	{
		ClockFormatter formatter = formatter(AUGUST_NOON, UTC, false);

		assertEquals("8:30 AM EDT", formatter.formatTime(NEW_YORK));
		assertEquals("1:30 PM BST", formatter.formatTime(LONDON));
		assertEquals("12:30 PM UTC", formatter.formatTime(UTC));
	}

	@Test
	public void twelveHourModeRendersMidnightAsTwelveAm()
	{
		assertEquals("12:30 AM NZST  Sat 29", formatter(AUGUST_NOON, UTC, false).formatTime(AUCKLAND));
	}

	@Test
	public void theToggleIsReadableBack()
	{
		assertTrue(formatter(AUGUST_NOON, UTC, true).isTwentyFourHour());
		assertFalse(formatter(AUGUST_NOON, UTC, false).isTwentyFourHour());
	}

	// -- The date marker appears only when the day differs from yours -----------------

	@Test
	public void noDateWhenTheZoneIsOnTheSameDayAsYou()
	{
		ClockFormatter formatter = formatter(AUGUST_NOON, UTC, true);

		// New York, London and UTC are all still Friday 28 August at this instant.
		assertFalse(formatter.formatTime(NEW_YORK).contains("Fri"));
		assertFalse(formatter.formatTime(LONDON).contains("Fri"));
		assertFalse(formatter.formatTime(UTC).contains("Fri"));
	}

	@Test
	public void dateAppearsWhenTheZoneIsAheadOfYou()
	{
		assertEquals("00:30 NZST  Sat 29", formatter(AUGUST_NOON, UTC, true).formatTime(AUCKLAND));
	}

	@Test
	public void dateAppearsWhenTheZoneIsBehindYou()
	{
		// Same instant, but now the user is in New Zealand, so it is already Saturday here.
		ClockFormatter formatter = formatter(AUGUST_NOON, AUCKLAND, true);

		assertEquals("08:30 EDT  Fri 28", formatter.formatTime(NEW_YORK));
		assertEquals("00:30 NZST", formatter.formatTime(AUCKLAND));
	}

	@Test
	public void differsIsMeasuredAgainstYourLocalDateNotUtc()
	{
		// 23:30 in New York on Friday is already 03:30 Saturday in UTC.
		Instant lateFridayInNewYork = Instant.parse("2026-08-29T03:30:00Z");
		ClockFormatter formatter = formatter(lateFridayInNewYork, NEW_YORK, true);

		assertEquals("23:30 EDT", formatter.formatTime(NEW_YORK));
		assertEquals("03:30 UTC  Sat 29", formatter.formatTime(UTC));
	}

	// -- Row assembly -----------------------------------------------------------------

	@Test
	public void rowsPadTheLabelOutToTheGivenColumn()
	{
		ClockFormatter formatter = formatter(AUGUST_NOON, UTC, true);
		int labelWidth = 14;

		assertEquals("US Eastern    08:30 EDT", formatter.formatRow("US Eastern", NEW_YORK, labelWidth));
		assertEquals("UK            13:30 BST", formatter.formatRow("UK", LONDON, labelWidth));
		assertEquals("New Zealand   00:30 NZST  Sat 29", formatter.formatRow("New Zealand", AUCKLAND, labelWidth));
	}

	@Test
	public void aLabelLongerThanTheColumnIsNotTruncated()
	{
		ClockFormatter formatter = formatter(AUGUST_NOON, UTC, true);
		assertEquals("Australia Eastern (QLD)22:30 AEST",
			formatter.formatRow("Australia Eastern (QLD)", ZoneId.of("Australia/Brisbane"), 4));
	}

	@Test(expected = NullPointerException.class)
	public void aNullClockIsRejected()
	{
		new ClockFormatter(null, true);
	}
}
