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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Renders one clock row for the HUD and the side panel, per D9.
 *
 * <pre>
 * US Eastern    08:30 EDT
 * UK            13:30 BST
 * New Zealand   00:30 NZST  Sat 30
 * </pre>
 *
 * <p>24-hour by default with a 12-hour toggle, never seconds, and the date appended only
 * when that zone is on a different calendar day from you. Excluding seconds is what lets
 * the overlay rebuild its strings once a minute instead of once a frame.
 *
 * <p>The injected {@link java.time.Clock} supplies both "now" and the user's own zone, so
 * tests can pin the date-differs case without reading the real system time.
 */
public class ClockFormatter
{
	private static final DateTimeFormatter TWENTY_FOUR_HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter TWELVE_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
	private static final DateTimeFormatter ABBREVIATION = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH);
	private static final DateTimeFormatter DATE_SUFFIX = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH);

	/** Two spaces, so a row with a date marker still lines up with one without. */
	private static final String DATE_GAP = "  ";

	private final java.time.Clock clock;
	private final boolean twentyFourHour;

	public ClockFormatter(java.time.Clock clock, boolean twentyFourHour)
	{
		this.clock = Objects.requireNonNull(clock, "clock");
		this.twentyFourHour = twentyFourHour;
	}

	/**
	 * The right-hand half of a row: the time, the abbreviation live in that zone right now,
	 * and a short date if the zone is on a different day from the user.
	 */
	public String formatTime(ZoneId zone)
	{
		ZonedDateTime there = ZonedDateTime.now(clock.withZone(zone));
		LocalDate here = LocalDate.now(clock);

		StringBuilder row = new StringBuilder(24);
		row.append(timeFormatter().format(there));
		row.append(' ');
		row.append(ABBREVIATION.format(there));

		if (!there.toLocalDate().equals(here))
		{
			row.append(DATE_GAP);
			row.append(DATE_SUFFIX.format(there));
		}

		return row.toString();
	}

	/**
	 * A whole row with the label padded out to a fixed column, for the places that render
	 * into a monospaced block rather than into two separate components.
	 *
	 * @param labelWidth the column width the caller has chosen for the whole list
	 */
	public String formatRow(String label, ZoneId zone, int labelWidth)
	{
		StringBuilder row = new StringBuilder(labelWidth + 24);
		row.append(label);
		while (row.length() < labelWidth)
		{
			row.append(' ');
		}
		row.append(formatTime(zone));
		return row.toString();
	}

	public boolean isTwentyFourHour()
	{
		return twentyFourHour;
	}

	private DateTimeFormatter timeFormatter()
	{
		return twentyFourHour ? TWENTY_FOUR_HOUR : TWELVE_HOUR;
	}
}
