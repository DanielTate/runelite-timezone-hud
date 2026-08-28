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
 * One clock the user has added: a zone plus the label shown beside it.
 *
 * <p>Named WorldClock rather than Clock so it cannot be confused with
 * {@link java.time.Clock}, which this plugin injects everywhere for testability.
 *
 * <p>The {@link ZoneId} is resolved once here and held, because the HUD renders these
 * every time it rebuilds and {@code ZoneId.of} should not be on that path.
 *
 * <p>Per D10 every clock is the same kind of thing. The seeded Local and Jagex rows are
 * ordinary instances of this class, with no flag marking them special.
 */
public final class WorldClock
{
	private final ZoneId zone;
	private final String label;

	public WorldClock(ZoneId zone, String label)
	{
		this.zone = Objects.requireNonNull(zone, "zone");
		this.label = Objects.requireNonNull(label, "label");
	}

	public WorldClock(String zoneId, String label)
	{
		this(ZoneId.of(zoneId), label);
	}

	/**
	 * A clock labelled with the zone's curated name, per D7. This is the default a user
	 * gets when adding a zone from the panel, before any rename.
	 */
	public static WorldClock forZone(ZoneId zone)
	{
		return new WorldClock(zone, ZoneRegistry.entryFor(zone).getDisplayName());
	}

	public static WorldClock forZone(ZoneEntry entry)
	{
		return new WorldClock(entry.getZone(), entry.getDisplayName());
	}

	public ZoneId getZone()
	{
		return zone;
	}

	/**
	 * The IANA identifier. This is the half that gets persisted.
	 */
	public String getZoneId()
	{
		return zone.getId();
	}

	public String getLabel()
	{
		return label;
	}

	/**
	 * D2 lets any row be renamed, including the seeded ones.
	 */
	public WorldClock withLabel(String newLabel)
	{
		return new WorldClock(zone, newLabel);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof WorldClock))
		{
			return false;
		}
		WorldClock that = (WorldClock) other;
		return zone.equals(that.zone) && label.equals(that.label);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(zone, label);
	}

	@Override
	public String toString()
	{
		return label + " (" + zone.getId() + ")";
	}
}
