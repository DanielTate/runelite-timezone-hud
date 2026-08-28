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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CommandResponderTest
{
	/** Saturday 29 August 2026, 10:57 in Auckland -- still Friday evening in New York. */
	private static final Clock FIXED =
		Clock.fixed(Instant.parse("2026-08-28T22:57:00Z"), ZoneId.of("Pacific/Auckland"));

	private final CommandResponder responder = new CommandResponder(FIXED);

	private String local(String... arguments)
	{
		return responder.respondToArguments(arguments).getText();
	}

	@Test
	public void convertsBetweenTwoNamedZones()
	{
		assertEquals("20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)",
			local("EST", "20:30", ">", "NZST"));
	}

	@Test
	public void anOmittedSourceMeansYourOwnZone()
	{
		assertEquals("20:30 NZST (Sat 29 Aug) = 04:30 EDT (Sat 29 Aug)",
			local("20:30", ">", "EST"));
	}

	@Test
	public void anOmittedTargetMeansYourOwnZone()
	{
		assertEquals("20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)",
			local("EST", "20:30"));
	}

	@Test
	public void noTimeConvertsThisMoment()
	{
		assertEquals("10:57 NZST (Sat 29 Aug) = 22:57 UTC (Fri 28 Aug)", local("now", ">", "UTC"));
		assertEquals(local("now", ">", "UTC"), local(">", "UTC"));
	}

	@Test
	public void gmtAnswersInUtcRatherThanBritishSummerTime()
	{
		// D1's amendment: GMT is a time scale, not a place that observes daylight saving.
		assertEquals("20:30 UTC (Fri 28 Aug) = 08:30 NZST (Sat 29 Aug)",
			local("GMT", "20:30", ">", "NZST"));
	}

	@Test
	public void theTwoCommandsProduceTheSameString()
	{
		// D8: !TZ only rewrites what is rendered locally, so any divergence between the
		// two paths would be invisible to the person who typed it.
		assertEquals(local("EST", "20:30", ">", "NZST"),
			responder.respondToMessage("!TZ EST 20:30 > NZST").getText());
	}

	@Test
	public void aSuccessfulConversionIsMarkedAsOne()
	{
		assertTrue(responder.respondToArguments(new String[]{"EST", ">", "NZST"}).isConversion());
	}

	@Test
	public void anAmbiguousZoneNamesItsMeaningsAndAWorkingAlternative()
	{
		// D6: refusing without naming a token that works leaves the user stuck.
		assertEquals("\"IST\" is ambiguous: India (kolkata), Ireland (dublin) or Israel (jerusalem)."
			+ " Use one of those instead.", local("IST", "20:30", ">", "NZST"));
	}

	@Test
	public void anAmbiguousZoneIsNotAConversion()
	{
		assertFalse(responder.respondToArguments(new String[]{"IST", ">", "NZST"}).isConversion());
	}

	@Test
	public void anUnknownZoneReadsDifferentlyFromAnAmbiguousOne()
	{
		String unknown = local("narnia", "20:30", ">", "NZST");

		assertTrue(unknown.startsWith("Unknown zone \"narnia\""));
		assertFalse(unknown.contains("ambiguous"));
	}

	@Test
	public void aBadTimeSaysSoAndShowsWhatWorks()
	{
		assertEquals("\"half past\" is not a time. Try 20:30, 8:30pm or now.",
			local("EST", "half", "past", ">", "NZST"));
	}

	@Test
	public void tooLittleToGoOnGetsTheUsageHint()
	{
		assertEquals(CommandResponder.USAGE, local());
		assertEquals(CommandResponder.USAGE, local("20:30"));
	}

	@Test
	public void aLineThatIsNotOurCommandSaysNothingAtAll()
	{
		CommandResponse response = responder.respondToMessage("hello everyone");

		assertFalse(response.hasText());
		assertNull(response.getText());
	}

	@Test
	public void twelveHourInputIsAccepted()
	{
		assertEquals(local("EST", "20:30", ">", "NZST"), local("EST", "8:30pm", ">", "NZST"));
	}

	@Test
	public void theAbbreviationIsTheOneLiveAtTheConvertedInstant()
	{
		// D8: converting into a zone that is on standard time must not print its summer
		// abbreviation just because the source is on daylight saving.
		assertTrue(local("EST", "20:30", ">", "London").contains("BST"));
		assertTrue(local("EST", "20:30", ">", "Sydney").contains("AEST"));
	}
}
