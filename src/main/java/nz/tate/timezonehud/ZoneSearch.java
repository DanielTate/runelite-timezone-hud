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

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import nz.tate.timezonehud.core.ZoneEntry;
import nz.tate.timezonehud.core.ZoneLookup;
import nz.tate.timezonehud.core.ZoneRegistry;

/**
 * What the side panel's search box offers, per D2: any of the JDK's zones, reachable by
 * abbreviation, city or IANA id.
 *
 * <p>Anything the abbreviation table knows comes first, so typing "EST" finds US Eastern
 * rather than burying it under the IANA ids that happen to contain those letters. An
 * ambiguous token lists all of its meanings as separate results -- which is D6's "did you
 * mean" prompt in the form a list of buttons naturally takes.
 *
 * <p>Kept out of the Swing class so the ranking can be tested without a display.
 */
public final class ZoneSearch
{
	/** Every zone the JDK knows, in id order, with a search-ready copy alongside. */
	private static final String[] ZONE_IDS = sortedZoneIds();
	private static final String[] SEARCHABLE_ZONE_IDS = normaliseAll(ZONE_IDS);

	private ZoneSearch()
	{
	}

	/**
	 * @param query what the user typed; blank finds nothing
	 * @param limit how many results the panel has room for
	 */
	public static List<ZoneEntry> search(String query, int limit)
	{
		if (query == null || query.trim().isEmpty() || limit <= 0)
		{
			return Collections.emptyList();
		}

		String needle = normalise(query);

		// LinkedHashSet keeps the ranking below while dropping the duplicates that arise
		// when a token and an IANA id point at the same zone.
		Set<ZoneEntry> results = new LinkedHashSet<>();

		ZoneLookup lookup = ZoneRegistry.lookup(query.trim());
		if (lookup.isResolved())
		{
			results.add(lookup.getZone());
		}
		else if (lookup.isAmbiguous())
		{
			results.addAll(lookup.getCandidates());
		}

		for (int i = 0; i < ZONE_IDS.length && results.size() < limit; i++)
		{
			if (SEARCHABLE_ZONE_IDS[i].contains(needle))
			{
				results.add(ZoneRegistry.entryFor(ZoneId.of(ZONE_IDS[i])));
			}
		}

		List<ZoneEntry> capped = new ArrayList<>(results);
		return capped.size() > limit ? capped.subList(0, limit) : capped;
	}

	/**
	 * Lower-cases and turns the id's punctuation into spaces, so "New_York", "new york"
	 * and "NEW YORK" all match "America/New_York".
	 */
	private static String normalise(String text)
	{
		String spaced = text.toLowerCase(Locale.ENGLISH).replace('_', ' ').replace('/', ' ');
		return spaced.trim().replaceAll("\\s+", " ");
	}

	private static String[] sortedZoneIds()
	{
		Set<String> sorted = new TreeSet<>(ZoneId.getAvailableZoneIds());
		return sorted.toArray(new String[0]);
	}

	private static String[] normaliseAll(String[] zoneIds)
	{
		String[] normalised = new String[zoneIds.length];
		for (int i = 0; i < zoneIds.length; i++)
		{
			normalised[i] = normalise(zoneIds[i]);
		}
		return normalised;
	}
}
