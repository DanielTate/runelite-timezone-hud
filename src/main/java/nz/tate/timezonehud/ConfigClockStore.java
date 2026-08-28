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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import nz.tate.timezonehud.core.ClockStore;
import nz.tate.timezonehud.core.WorldClock;

/**
 * Keeps the clock list in RuneLite's config, as a small JSON array:
 *
 * <pre>
 * [{"zoneId":"Pacific/Auckland","label":"Local"},{"zoneId":"UTC","label":"Jagex (UTC)"}]
 * </pre>
 *
 * <p>The seeded flag lives under its own key rather than being inferred from an empty list.
 * D10 requires seeding to happen exactly once, so a user who deliberately deletes every
 * clock must get an empty panel back on the next start, not Local and Jagex again.
 *
 * <p>Anything unreadable is dropped rather than thrown: a zone the JDK no longer recognises
 * after a tzdb update should cost the user that one row, not their whole list.
 */
@Slf4j
public class ConfigClockStore implements ClockStore
{
	static final String KEY_CLOCKS = "clocks";
	static final String KEY_SEEDED = "seeded";

	private static final Type STORED_CLOCK_LIST = new TypeToken<List<StoredClock>>()
	{
	}.getType();

	private final ConfigValues values;
	private final Gson gson;

	public ConfigClockStore(ConfigValues values, Gson gson)
	{
		this.values = Objects.requireNonNull(values, "values");
		this.gson = Objects.requireNonNull(gson, "gson");
	}

	@Override
	public List<WorldClock> loadClocks()
	{
		List<WorldClock> clocks = new ArrayList<>();

		String json = values.get(KEY_CLOCKS);
		if (json == null || json.trim().isEmpty())
		{
			return clocks;
		}

		List<StoredClock> stored;
		try
		{
			stored = gson.fromJson(json, STORED_CLOCK_LIST);
		}
		catch (JsonParseException e)
		{
			log.debug("saved clock list could not be read, starting empty", e);
			return clocks;
		}

		if (stored == null)
		{
			return clocks;
		}

		for (StoredClock entry : stored)
		{
			WorldClock clock = toWorldClock(entry);
			if (clock != null)
			{
				clocks.add(clock);
			}
		}

		return clocks;
	}

	@Override
	public void saveClocks(List<WorldClock> clocks)
	{
		Objects.requireNonNull(clocks, "clocks");

		List<StoredClock> stored = new ArrayList<>(clocks.size());
		for (WorldClock clock : clocks)
		{
			StoredClock entry = new StoredClock();
			entry.zoneId = clock.getZoneId();
			entry.label = clock.getLabel();
			stored.add(entry);
		}

		values.put(KEY_CLOCKS, gson.toJson(stored, STORED_CLOCK_LIST));
	}

	@Override
	public boolean isSeeded()
	{
		return Boolean.parseBoolean(values.get(KEY_SEEDED));
	}

	@Override
	public void markSeeded()
	{
		values.put(KEY_SEEDED, "true");
	}

	private static WorldClock toWorldClock(StoredClock entry)
	{
		if (entry == null || entry.zoneId == null || entry.label == null)
		{
			return null;
		}

		try
		{
			if (WorldClock.SYSTEM_ZONE_ID.equals(entry.zoneId))
			{
				// Not an IANA id, so ZoneId.of would reject it. This is the seeded Local
				// row, which resolves against the machine when it is displayed.
				return WorldClock.local(entry.label);
			}
			return new WorldClock(ZoneId.of(entry.zoneId), entry.label);
		}
		catch (DateTimeException e)
		{
			log.debug("dropping saved clock with unknown zone {}", entry.zoneId);
			return null;
		}
	}

	/**
	 * The on-disk shape. Deliberately two plain strings: a saved clock must survive any
	 * later change to {@link WorldClock}, and a zone id is text everywhere else too.
	 */
	private static class StoredClock
	{
		private String zoneId;
		private String label;
	}
}
