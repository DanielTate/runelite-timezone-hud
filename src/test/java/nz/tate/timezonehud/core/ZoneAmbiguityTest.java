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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * D6: a token with more than one plausible meaning is refused with a candidate list,
 * and refusal must not look like a parse failure.
 */
public class ZoneAmbiguityTest
{
	private static List<String> candidateZoneIds(String token)
	{
		ZoneLookup lookup = ZoneRegistry.lookup(token);
		assertTrue(token + " should be ambiguous but was " + lookup, lookup.isAmbiguous());

		List<String> zoneIds = new ArrayList<>();
		for (ZoneEntry candidate : lookup.getCandidates())
		{
			zoneIds.add(candidate.getZoneId());
		}
		return zoneIds;
	}

	// -- The contested set ----------------------------------------------------------

	@Test
	public void istListsIndiaIrelandAndIsrael()
	{
		assertEquals(Arrays.asList("Asia/Kolkata", "Europe/Dublin", "Asia/Jerusalem"), candidateZoneIds("IST"));
	}

	@Test
	public void cstListsUsCentralChinaAndCuba()
	{
		assertEquals(Arrays.asList("America/Chicago", "Asia/Shanghai", "America/Havana"), candidateZoneIds("CST"));
	}

	@Test
	public void bstListsBritishSummerTimeAndBangladesh()
	{
		assertEquals(Arrays.asList("Europe/London", "Asia/Dhaka"), candidateZoneIds("BST"));
	}

	@Test
	public void theRestOfTheContestedSetIsCovered()
	{
		assertEquals(Arrays.asList("America/Halifax", "Asia/Riyadh"), candidateZoneIds("AST"));
		assertEquals(Arrays.asList("Australia/Adelaide", "Australia/Darwin"), candidateZoneIds("ACST"));
		assertEquals(Arrays.asList("America/Manaus", "Asia/Yerevan"), candidateZoneIds("AMT"));
	}

	@Test
	public void contestedTokensAreCaseInsensitiveToo()
	{
		assertTrue(ZoneRegistry.lookup("ist").isAmbiguous());
		assertTrue(ZoneRegistry.lookup("Cst").isAmbiguous());
		assertTrue(ZoneRegistry.lookup("  bst  ").isAmbiguous());
	}

	@Test
	public void everyCandidateCarriesADisplayName()
	{
		String[] contested = {"IST", "CST", "BST", "AST", "ACST", "AMT"};
		for (String token : contested)
		{
			for (ZoneEntry candidate : ZoneRegistry.lookup(token).getCandidates())
			{
				assertFalse(token + " candidate has no display name", candidate.getDisplayName().isEmpty());
			}
		}
	}

	// -- The "did you mean" has to name something the user can type -----------------

	@Test
	public void everyCandidateIsReachableByAnUnambiguousToken()
	{
		String[] contested = {"IST", "CST", "BST", "AST", "ACST", "AMT"};
		for (String token : contested)
		{
			for (ZoneEntry candidate : ZoneRegistry.lookup(token).getCandidates())
			{
				String example = ZoneRegistry.exampleToken(candidate);
				ZoneLookup retry = ZoneRegistry.lookup(example);

				assertTrue("suggesting '" + example + "' for " + token + " does not resolve", retry.isResolved());
				assertEquals(candidate.getZoneId(), retry.getZone().getZoneId());
			}
		}
	}

	@Test
	public void suggestedTokensAreNamesRatherThanAbbreviations()
	{
		assertEquals("kolkata", ZoneRegistry.exampleToken(ZoneRegistry.lookup("IST").getCandidates().get(0)));
		assertEquals("london", ZoneRegistry.exampleToken(ZoneRegistry.lookup("BST").getCandidates().get(0)));
		assertEquals("chicago", ZoneRegistry.exampleToken(ZoneRegistry.lookup("CST").getCandidates().get(0)));
	}

	// -- The common path is unaffected ----------------------------------------------

	@Test
	public void unambiguousAbbreviationsStillResolveWithNoPrompt()
	{
		String[] fine = {"EST", "EDT", "PST", "PDT", "MST", "NZST", "NZDT", "JST", "GMT", "UTC", "CET", "CEST", "AEST", "AEDT"};
		for (String token : fine)
		{
			ZoneLookup lookup = ZoneRegistry.lookup(token);
			assertTrue(token + " should resolve but was " + lookup, lookup.isResolved());
			assertFalse(token + " should not be ambiguous", lookup.isAmbiguous());
		}
	}

	// -- Ambiguous and unknown must be tellable apart -------------------------------

	@Test
	public void ambiguousAndUnknownAreDistinguishable()
	{
		ZoneLookup ambiguous = ZoneRegistry.lookup("IST");
		ZoneLookup unknown = ZoneRegistry.lookup("qqzz");

		assertEquals(ZoneLookup.Status.AMBIGUOUS, ambiguous.getStatus());
		assertEquals(ZoneLookup.Status.UNKNOWN, unknown.getStatus());

		assertTrue(ambiguous.isAmbiguous());
		assertFalse(ambiguous.isUnknown());
		assertFalse(ambiguous.isResolved());

		assertTrue(unknown.isUnknown());
		assertFalse(unknown.isAmbiguous());

		// Both carry the token back for the message, but only one has candidates.
		assertEquals("IST", ambiguous.getToken());
		assertEquals("qqzz", unknown.getToken());
		assertFalse(ambiguous.getCandidates().isEmpty());
		assertTrue(unknown.getCandidates().isEmpty());
		assertNull(ambiguous.getZone());
	}

	@Test
	public void bstIsRefusedRatherThanSilentlyMeaningDhaka()
	{
		// ZoneId.SHORT_IDS maps BST straight to Asia/Dhaka. That is the bug D6 prevents.
		ZoneLookup lookup = ZoneRegistry.lookup("BST");
		assertTrue(lookup.isAmbiguous());
		assertNull(lookup.getZone());
	}

	// -- ZoneLookup's own contract ---------------------------------------------------

	@Test
	public void candidateListsAreCopiedAndUnmodifiable()
	{
		List<ZoneEntry> source = new ArrayList<>();
		source.add(new ZoneEntry("UTC", "UTC"));
		source.add(new ZoneEntry("Europe/London", "UK"));

		ZoneLookup lookup = ZoneLookup.ambiguous("XYZ", source);
		source.clear();

		assertEquals(2, lookup.getCandidates().size());

		try
		{
			lookup.getCandidates().add(new ZoneEntry("UTC", "UTC"));
			throw new AssertionError("candidate list should be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// expected
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void anAmbiguityNeedsAtLeastTwoCandidates()
	{
		ZoneLookup.ambiguous("XYZ", Collections.singletonList(new ZoneEntry("UTC", "UTC")));
	}
}
