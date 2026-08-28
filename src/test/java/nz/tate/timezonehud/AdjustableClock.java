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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock the test moves by hand. {@code Clock.fixed} cannot express "a minute later",
 * which is the only interesting case for a cache that rebuilds on the minute.
 *
 * <p>Views made by {@link #withZone} share the same instant, because the code under test
 * calls it for every zone it renders and must see the time the test has set.
 */
class AdjustableClock extends Clock
{
	private final Ticker ticker;
	private final ZoneId zone;

	AdjustableClock(Instant start, ZoneId zone)
	{
		this(new Ticker(start), zone);
	}

	private AdjustableClock(Ticker ticker, ZoneId zone)
	{
		this.ticker = ticker;
		this.zone = zone;
	}

	void advance(Duration amount)
	{
		ticker.now = ticker.now.plus(amount);
	}

	@Override
	public ZoneId getZone()
	{
		return zone;
	}

	@Override
	public Clock withZone(ZoneId other)
	{
		return new AdjustableClock(ticker, other);
	}

	@Override
	public Instant instant()
	{
		return ticker.now;
	}

	private static class Ticker
	{
		private Instant now;

		Ticker(Instant now)
		{
			this.now = now;
		}
	}
}
