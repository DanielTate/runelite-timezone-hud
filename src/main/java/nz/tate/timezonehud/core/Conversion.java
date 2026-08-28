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

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * One moment seen from two zones. Both sides are full {@link ZonedDateTime}s so that D8 can
 * print each side's own abbreviation and day.
 */
public final class Conversion
{
	private final ZonedDateTime source;
	private final ZonedDateTime target;

	public Conversion(ZonedDateTime source, ZonedDateTime target)
	{
		this.source = Objects.requireNonNull(source, "source");
		this.target = Objects.requireNonNull(target, "target");
	}

	public ZonedDateTime getSource()
	{
		return source;
	}

	public ZonedDateTime getTarget()
	{
		return target;
	}

	/**
	 * True when the two sides land on different calendar days -- the case the whole
	 * converter exists for.
	 */
	public boolean rollsOverToAnotherDay()
	{
		return !source.toLocalDate().equals(target.toLocalDate());
	}

	/**
	 * The D8 string. Both chat paths print exactly this.
	 */
	public String format()
	{
		return ConversionFormat.format(source, target);
	}

	@Override
	public String toString()
	{
		return format();
	}
}
