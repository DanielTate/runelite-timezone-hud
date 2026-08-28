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
import java.util.Objects;

/**
 * The zone arithmetic behind {@code !TZ} and {@code ::tz}.
 *
 * <p>Per D1 there is no offset maths here at all. Both zones are real IANA zones and
 * {@code java.time} applies the right daylight-saving rules for the date in question from
 * the JDK's bundled tzdb, which is why a conversion into winter comes back as EST rather
 * than EDT without this class knowing anything about seasons.
 *
 * <p>The injected {@link java.time.Clock} supplies "now" and the user's own zone, so every
 * DST boundary is directly expressible in a test and nothing reads the real system time.
 */
public class TimeConverter
{
	private final java.time.Clock clock;

	public TimeConverter(java.time.Clock clock)
	{
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * The user's own zone, used wherever D5 leaves a source or target out.
	 */
	public ZoneId getLocalZone()
	{
		return clock.getZone();
	}

	/**
	 * Convert a wall-clock time from one zone to another.
	 *
	 * @param time the time in the source zone, or null to convert the current moment
	 */
	public Conversion convert(ZoneId source, ZoneId target, LocalTime time)
	{
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(target, "target");

		ZonedDateTime sourceTime;
		if (time == null)
		{
			sourceTime = ZonedDateTime.now(clock.withZone(source));
		}
		else
		{
			// The date is today *in the source zone*, which is not always today where the
			// user is sitting -- "20:30 EST" typed from New Zealand means yesterday's date.
			LocalDate sourceDate = LocalDate.now(clock.withZone(source));
			sourceTime = ZonedDateTime.of(sourceDate, time, source);
		}

		return new Conversion(sourceTime, sourceTime.withZoneSameInstant(target));
	}

	/**
	 * Convert a parsed request whose zone tokens the caller has already resolved. A null
	 * zone means the user's local one, per D5.
	 */
	public Conversion convert(ConversionRequest request, ZoneId source, ZoneId target)
	{
		ZoneId from = source != null ? source : getLocalZone();
		ZoneId to = target != null ? target : getLocalZone();
		return convert(from, to, request.getTime());
	}
}
