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

import java.time.LocalTime;
import java.util.Objects;

/**
 * What the user asked for, as raw tokens. The parser deliberately does not resolve the zone
 * tokens -- that is {@link ZoneRegistry}'s job, and keeping them separate is what lets the
 * caller report "unknown zone: X" using the spelling the user actually typed.
 *
 * <p>A null source or target means "my local zone", and a null time means "right now", per
 * D5's independently optional fields.
 */
public final class ConversionRequest
{
	private final String sourceToken;
	private final String targetToken;
	private final LocalTime time;

	public ConversionRequest(String sourceToken, String targetToken, LocalTime time)
	{
		this.sourceToken = sourceToken;
		this.targetToken = targetToken;
		this.time = time;
	}

	/**
	 * @return the zone the time is in, or null for the user's local zone
	 */
	public String getSourceToken()
	{
		return sourceToken;
	}

	/**
	 * @return the zone to convert into, or null for the user's local zone
	 */
	public String getTargetToken()
	{
		return targetToken;
	}

	/**
	 * @return the wall-clock time to convert, or null to convert the current moment
	 */
	public LocalTime getTime()
	{
		return time;
	}

	public boolean hasSource()
	{
		return sourceToken != null;
	}

	public boolean hasTarget()
	{
		return targetToken != null;
	}

	public boolean isNow()
	{
		return time == null;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof ConversionRequest))
		{
			return false;
		}
		ConversionRequest that = (ConversionRequest) other;
		return Objects.equals(sourceToken, that.sourceToken)
			&& Objects.equals(targetToken, that.targetToken)
			&& Objects.equals(time, that.time);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(sourceToken, targetToken, time);
	}

	@Override
	public String toString()
	{
		return "ConversionRequest[" + sourceToken + " " + (time == null ? "now" : time) + " -> " + targetToken + "]";
	}
}
