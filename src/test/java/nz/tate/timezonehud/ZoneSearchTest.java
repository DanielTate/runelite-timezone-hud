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

import java.util.ArrayList;
import java.util.List;
import nz.tate.timezonehud.core.ZoneEntry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ZoneSearchTest
{
	private static List<String> zoneIdsFound(String query, int limit)
	{
		List<String> ids = new ArrayList<>();
		for (ZoneEntry entry : ZoneSearch.search(query, limit))
		{
			ids.add(entry.getZoneId());
		}
		return ids;
	}

	@Test
	public void blankQueriesFindNothing()
	{
		assertTrue(ZoneSearch.search(null, 8).isEmpty());
		assertTrue(ZoneSearch.search("", 8).isEmpty());
		assertTrue(ZoneSearch.search("   ", 8).isEmpty());
	}

	@Test
	public void aCityNameFindsItsZone()
	{
		assertTrue(zoneIdsFound("auckland", 8).contains("Pacific/Auckland"));
	}

	@Test
	public void anAbbreviationComesFirst()
	{
		// "EST" also appears inside several IANA ids; the curated meaning has to win.
		assertEquals("America/New_York", zoneIdsFound("EST", 8).get(0));
	}

	@Test
	public void anAmbiguousAbbreviationOffersEveryMeaning()
	{
		// D6: the panel's equivalent of the chat prompt is simply listing the candidates.
		List<String> found = zoneIdsFound("IST", 8);

		assertTrue(found.contains("Asia/Kolkata"));
		assertTrue(found.contains("Europe/Dublin"));
		assertTrue(found.contains("Asia/Jerusalem"));
	}

	@Test
	public void underscoresAndSpacesAreInterchangeable()
	{
		assertTrue(zoneIdsFound("new york", 8).contains("America/New_York"));
		assertTrue(zoneIdsFound("New_York", 8).contains("America/New_York"));
		assertTrue(zoneIdsFound("america/new york", 8).contains("America/New_York"));
	}

	@Test
	public void searchIsCaseInsensitive()
	{
		assertEquals(zoneIdsFound("TOKYO", 8), zoneIdsFound("tokyo", 8));
	}

	@Test
	public void resultsAreCappedAtTheLimit()
	{
		assertEquals(3, ZoneSearch.search("america", 3).size());
	}

	@Test
	public void nonsenseFindsNothing()
	{
		assertTrue(ZoneSearch.search("qqzzx", 8).isEmpty());
	}

	@Test
	public void aZoneIsNeverListedTwice()
	{
		List<String> found = zoneIdsFound("auckland", 8);

		assertEquals(1, found.size());
		assertEquals("Pacific/Auckland", found.get(0));
	}
}
