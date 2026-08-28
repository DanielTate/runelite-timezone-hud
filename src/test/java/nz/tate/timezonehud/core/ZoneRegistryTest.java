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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ZoneRegistryTest
{
	private static final DateTimeFormatter ABBREVIATION = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH);

	private static final Instant AUGUST = Instant.parse("2026-08-28T12:00:00Z");
	private static final Instant JANUARY = Instant.parse("2026-01-15T12:00:00Z");

	private static String zoneIdOf(String token)
	{
		ZoneLookup lookup = ZoneRegistry.lookup(token);
		assertTrue(token + " should resolve but was " + lookup, lookup.isResolved());
		return lookup.getZone().getZoneId();
	}

	private static String abbreviationAt(String token, Instant instant)
	{
		ZoneId zone = ZoneRegistry.lookup(token).getZone().getZone();
		return ABBREVIATION.format(instant.atZone(zone));
	}

	// -- D1: abbreviations mean DST-aware zones -------------------------------------

	@Test
	public void estResolvesToTheUsEasternZone()
	{
		assertEquals("America/New_York", zoneIdOf("EST"));
	}

	@Test
	public void easternRendersEdtInSummerAndEstInWinter()
	{
		assertEquals("EDT", abbreviationAt("EST", AUGUST));
		assertEquals("EST", abbreviationAt("EST", JANUARY));
	}

	@Test
	public void newZealandRendersNzstInAugustAndNzdtInJanuary()
	{
		assertEquals("NZST", abbreviationAt("NZST", AUGUST));
		assertEquals("NZDT", abbreviationAt("NZST", JANUARY));
	}

	@Test
	public void ukRendersBstInSummerAndGmtInWinter()
	{
		assertEquals("BST", abbreviationAt("london", AUGUST));
		assertEquals("GMT", abbreviationAt("london", JANUARY));
	}

	@Test
	public void summerAndWinterSpellingsOfTheSameZoneAgree()
	{
		assertEquals(zoneIdOf("EST"), zoneIdOf("EDT"));
		assertEquals(zoneIdOf("PST"), zoneIdOf("PDT"));
		assertEquals(zoneIdOf("NZST"), zoneIdOf("NZDT"));
		assertEquals(zoneIdOf("AEST"), zoneIdOf("AEDT"));
		assertEquals(zoneIdOf("CET"), zoneIdOf("CEST"));
	}

	// -- The table we ship instead of ZoneId.SHORT_IDS ------------------------------

	@Test
	public void weNeverUseTheLegacyShortIdsTable()
	{
		// SHORT_IDS maps EST to a fixed -05:00 with no daylight saving, and BST to Dhaka.
		assertEquals("-05:00", ZoneId.SHORT_IDS.get("EST"));
		assertEquals("Asia/Dhaka", ZoneId.SHORT_IDS.get("BST"));

		assertNotEquals("-05:00", zoneIdOf("EST"));
		assertEquals("America/New_York", zoneIdOf("EST"));

		ZoneLookup bst = ZoneRegistry.lookup("BST");
		assertFalse("BST must never silently mean Asia/Dhaka",
			bst.isResolved() && "Asia/Dhaka".equals(bst.getZone().getZoneId()));
	}

	// -- D7: every entry carries a human display name -------------------------------

	@Test
	public void displayNamesAreZoneNamesNotCitiesOrIanaIds()
	{
		assertEquals("US Eastern", ZoneRegistry.lookup("new york").getZone().getDisplayName());
		assertEquals("New Zealand", ZoneRegistry.lookup("auckland").getZone().getDisplayName());
		assertEquals("UK", ZoneRegistry.lookup("london").getZone().getDisplayName());
		assertEquals("Japan", ZoneRegistry.lookup("tokyo").getZone().getDisplayName());
		assertEquals("UTC", ZoneRegistry.lookup("UTC").getZone().getDisplayName());
	}

	@Test
	public void everyCuratedTokenResolvesAndHasANonEmptyDisplayName()
	{
		String[] tokens = {
			"EST", "EDT", "ET", "PST", "PDT", "MST", "MDT", "CDT", "CT",
			"NZST", "NZDT", "NZ", "GMT", "UTC", "CET", "CEST", "JST",
			"AEST", "AEDT", "AWST", "ACDT", "HST", "AKST", "SGT", "HKT", "KST", "IDT",
			"london", "new york", "auckland", "tokyo", "sydney", "kolkata", "dublin",
			"jerusalem", "chicago", "denver", "los angeles", "shanghai", "singapore",
			"berlin", "paris", "moscow", "dubai", "toronto", "sao paulo",
		};

		for (String token : tokens)
		{
			ZoneLookup lookup = ZoneRegistry.lookup(token);
			assertTrue(token + " should resolve but was " + lookup, lookup.isResolved());
			assertFalse(token + " has no display name", lookup.getZone().getDisplayName().isEmpty());
		}
	}

	@Test
	public void requiredCityNamesMapToTheExpectedZones()
	{
		assertEquals("Europe/London", zoneIdOf("london"));
		assertEquals("America/New_York", zoneIdOf("new york"));
		assertEquals("Pacific/Auckland", zoneIdOf("auckland"));
		assertEquals("Asia/Tokyo", zoneIdOf("tokyo"));
		assertEquals("Australia/Sydney", zoneIdOf("sydney"));
		assertEquals("Asia/Kolkata", zoneIdOf("kolkata"));
		assertEquals("Europe/Dublin", zoneIdOf("dublin"));
		assertEquals("Asia/Jerusalem", zoneIdOf("jerusalem"));
		assertEquals("America/Chicago", zoneIdOf("chicago"));
		assertEquals("America/Denver", zoneIdOf("denver"));
		assertEquals("America/Los_Angeles", zoneIdOf("los angeles"));
		assertEquals("Asia/Shanghai", zoneIdOf("shanghai"));
		assertEquals("Asia/Singapore", zoneIdOf("singapore"));
		assertEquals("Europe/Berlin", zoneIdOf("berlin"));
		assertEquals("Europe/Paris", zoneIdOf("paris"));
	}

	// -- Token normalisation --------------------------------------------------------

	@Test
	public void lookupIsCaseAndSeparatorInsensitive()
	{
		assertEquals("America/New_York", zoneIdOf("new york"));
		assertEquals("America/New_York", zoneIdOf("NEW YORK"));
		assertEquals("America/New_York", zoneIdOf("New_York"));
		assertEquals("America/New_York", zoneIdOf("new-york"));
		assertEquals("America/New_York", zoneIdOf("  new   york  "));
		assertEquals("America/New_York", zoneIdOf("eSt"));
	}

	@Test
	public void rawIanaIdsResolveWithACityDerivedName()
	{
		ZoneLookup lookup = ZoneRegistry.lookup("america/argentina/ushuaia");
		assertTrue(lookup.isResolved());
		assertEquals("America/Argentina/Ushuaia", lookup.getZone().getZoneId());
		assertEquals("Ushuaia", lookup.getZone().getDisplayName());
	}

	@Test
	public void ourTableWinsOverTheRawIanaIdSoDisplayNamesStayCurated()
	{
		// "Pacific/Auckland" is a valid IANA id, but we want our own label for it.
		assertEquals("New Zealand", ZoneRegistry.lookup("Pacific/Auckland").getZone().getDisplayName());
	}

	@Test
	public void entryForGivesTheCuratedLabelWhereWeHaveOne()
	{
		assertEquals("US Eastern", ZoneRegistry.entryFor(ZoneId.of("America/New_York")).getDisplayName());
		assertEquals("UTC", ZoneRegistry.entryFor(ZoneId.of("UTC")).getDisplayName());
		assertEquals("Ushuaia", ZoneRegistry.entryFor(ZoneId.of("America/Argentina/Ushuaia")).getDisplayName());
	}

	// -- Unknown tokens -------------------------------------------------------------

	@Test
	public void nonsenseIsUnknown()
	{
		ZoneLookup lookup = ZoneRegistry.lookup("qqzz");
		assertTrue(lookup.isUnknown());
		assertFalse(lookup.isResolved());
		assertNull(lookup.getZone());
		assertEquals("qqzz", lookup.getToken());
	}

	@Test
	public void nullAndBlankAreUnknownRatherThanAnException()
	{
		assertTrue(ZoneRegistry.lookup(null).isUnknown());
		assertTrue(ZoneRegistry.lookup("").isUnknown());
		assertTrue(ZoneRegistry.lookup("   ").isUnknown());
	}

	@Test
	public void isKnownTokenAgreesWithLookup()
	{
		assertTrue(ZoneRegistry.isKnownToken("EST"));
		assertTrue(ZoneRegistry.isKnownToken("new york"));
		assertFalse(ZoneRegistry.isKnownToken("qqzz"));
		assertFalse(ZoneRegistry.isKnownToken(null));
	}

	@Test
	public void maxTokenWordsCoversOurLongestAlias()
	{
		// "salt lake city" is three words, so the parser must be willing to look back three.
		assertTrue(ZoneRegistry.isKnownToken("salt lake city"));
		assertTrue(ZoneRegistry.maxTokenWords() >= 3);
	}

	@Test
	public void gmtMeansUtcNotUkLocalTime()
	{
		// GMT is a time scale meaning UTC+0. Resolving it to Europe/London would make a
		// summer conversion answer in BST, contradicting the token the user typed.
		assertEquals("UTC", zoneIdOf("gmt"));

		// Renders as UTC year-round. A different word from the one typed, but the same
		// instant -- unlike BST, which would have been a different time.
		assertEquals("UTC", abbreviationAt("gmt", AUGUST));
		assertEquals("UTC", abbreviationAt("gmt", JANUARY));
	}

	@Test
	public void ukTokensStillFollowBritishSummerTime()
	{
		assertEquals("Europe/London", zoneIdOf("london"));
		assertEquals("Europe/London", zoneIdOf("uk"));
		assertEquals("BST", abbreviationAt("london", AUGUST));
	}
}
