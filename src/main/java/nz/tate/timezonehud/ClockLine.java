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

import java.util.Objects;

/**
 * One rendered clock row: the label on the left, the time on the right.
 *
 * <p>Both halves are already formatted. Whoever draws it -- the HUD or the side panel --
 * only has to place two strings.
 */
public final class ClockLine
{
	private final String label;
	private final String time;

	public ClockLine(String label, String time)
	{
		this.label = Objects.requireNonNull(label, "label");
		this.time = Objects.requireNonNull(time, "time");
	}

	public String getLabel()
	{
		return label;
	}

	/**
	 * @return e.g. {@code 08:30 EDT} or {@code 00:30 NZST  Sat 30}
	 */
	public String getTime()
	{
		return time;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof ClockLine))
		{
			return false;
		}

		ClockLine that = (ClockLine) other;
		return label.equals(that.label) && time.equals(that.time);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(label, time);
	}

	@Override
	public String toString()
	{
		return label + " " + time;
	}
}
