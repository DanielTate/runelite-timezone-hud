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

import java.time.ZoneId;
import java.util.Objects;

/**
 * One timezone the plugin knows about: a real IANA zone plus the label we show for it.
 *
 * <p>The display name is a zone name rather than a city or an abbreviation ("US Eastern",
 * not "New York" and not "America/New_York"). Per D7 the label has to stay stable all year
 * while the abbreviation printed beside it flips between, say, EST and EDT.
 */
public final class ZoneEntry
{
	private final ZoneId zone;
	private final String displayName;

	public ZoneEntry(ZoneId zone, String displayName)
	{
		this.zone = Objects.requireNonNull(zone, "zone");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
	}

	public ZoneEntry(String zoneId, String displayName)
	{
		this(ZoneId.of(zoneId), displayName);
	}

	public ZoneId getZone()
	{
		return zone;
	}

	/**
	 * The IANA identifier, e.g. {@code America/New_York}. This is what gets persisted.
	 */
	public String getZoneId()
	{
		return zone.getId();
	}

	public String getDisplayName()
	{
		return displayName;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof ZoneEntry))
		{
			return false;
		}
		ZoneEntry that = (ZoneEntry) other;
		return zone.equals(that.zone) && displayName.equals(that.displayName);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(zone, displayName);
	}

	@Override
	public String toString()
	{
		return displayName + " (" + zone.getId() + ")";
	}
}
