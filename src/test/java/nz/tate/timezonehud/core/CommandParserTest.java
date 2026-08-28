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

import java.time.LocalTime;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * D5's grammar: optional source, optional target, bare times, "now", three separators,
 * case-insensitive throughout.
 */
public class CommandParserTest
{
	private final CommandParser parser = new CommandParser();

	private ConversionRequest request(String line)
	{
		ParsedCommand parsed = parser.parse(line);
		assertTrue(line + " should parse but was " + parsed, parsed.isValid());
		return parsed.getRequest();
	}

	private ParsedCommand.Problem problem(String line)
	{
		ParsedCommand parsed = parser.parse(line);
		assertFalse(line + " should not parse but gave " + parsed, parsed.isValid());
		return parsed.getProblem();
	}

	// -- The full form and its optional pieces ---------------------------------------

	@Test
	public void fullFormCarriesSourceTimeAndTarget()
	{
		ConversionRequest request = request("!TZ EST 20:30 > NZST");

		assertEquals("EST", request.getSourceToken());
		assertEquals("NZST", request.getTargetToken());
		assertEquals(LocalTime.of(20, 30), request.getTime());
		assertTrue(request.hasSource());
		assertTrue(request.hasTarget());
		assertFalse(request.isNow());
	}

	@Test
	public void anOmittedSourceMeansLocal()
	{
		ConversionRequest request = request("!tz 20:30 > NZST");

		assertNull(request.getSourceToken());
		assertFalse(request.hasSource());
		assertEquals("NZST", request.getTargetToken());
		assertEquals(LocalTime.of(20, 30), request.getTime());
	}

	@Test
	public void anOmittedTargetMeansLocal()
	{
		ConversionRequest request = request("!tz EST 20:30");

		assertEquals("EST", request.getSourceToken());
		assertNull(request.getTargetToken());
		assertFalse(request.hasTarget());
		assertEquals(LocalTime.of(20, 30), request.getTime());
	}

	@Test
	public void anOmittedTimeMeansNow()
	{
		ConversionRequest request = request("!tz EST > NZST");

		assertEquals("EST", request.getSourceToken());
		assertEquals("NZST", request.getTargetToken());
		assertNull(request.getTime());
		assertTrue(request.isNow());
	}

	@Test
	public void theWordNowIsTheSameAsOmittingTheTime()
	{
		assertEquals(request("!tz EST > NZST"), request("!tz EST now > NZST"));
		assertEquals(request("!tz > NZST"), request("!tz now > NZST"));
	}

	@Test
	public void aBareSourceWithNothingElseIsAllowed()
	{
		ConversionRequest request = request("!tz tokyo");

		assertEquals("tokyo", request.getSourceToken());
		assertNull(request.getTargetToken());
		assertTrue(request.isNow());
	}

	// -- Separators and case ----------------------------------------------------------

	@Test
	public void allThreeSeparatorsMeanTheSameThing()
	{
		ConversionRequest expected = request("!tz EST 20:30 > NZST");

		assertEquals(expected, request("!tz EST 20:30 -> NZST"));
		assertEquals(expected, request("!tz EST 20:30 to NZST"));
		assertEquals(expected, request("!tz EST 20:30 TO NZST"));
	}

	@Test
	public void triggersAreCaseInsensitiveAndBothFormsWork()
	{
		ConversionRequest expected = request("!tz EST 20:30 > NZST");

		assertEquals(expected, request("!TZ EST 20:30 > NZST"));
		assertEquals(expected, request("!Tz EST 20:30 > NZST"));
		assertEquals(expected, request("::tz EST 20:30 > NZST"));
		assertEquals(expected, request("::TZ EST 20:30 > NZST"));
	}

	@Test
	public void extraWhitespaceIsIgnored()
	{
		assertEquals(request("!tz EST 20:30 > NZST"), request("   !tz   EST   20:30   >   NZST   "));
	}

	@Test
	public void triggerAndSeparatorRecognitionIsExposed()
	{
		assertTrue(CommandParser.isTrigger("!tz"));
		assertTrue(CommandParser.isTrigger("::TZ"));
		assertFalse(CommandParser.isTrigger("!kc"));
		assertFalse(CommandParser.isTrigger(null));

		assertTrue(CommandParser.isSeparator(">"));
		assertTrue(CommandParser.isSeparator("->"));
		assertTrue(CommandParser.isSeparator("TO"));
		assertFalse(CommandParser.isSeparator("=>"));
	}

	// -- Multi-word city names (longest match, OPEN-QUESTIONS O4) ---------------------

	@Test
	public void multiWordCitiesSurviveTheWhitespaceSplit()
	{
		ConversionRequest request = request("!tz new york 8:30pm > new zealand");

		assertEquals("new york", request.getSourceToken());
		assertEquals("new zealand", request.getTargetToken());
		assertEquals(LocalTime.of(20, 30), request.getTime());
	}

	@Test
	public void longestMatchWinsOverAShorterPrefix()
	{
		// "los" alone means nothing; "los angeles" does.
		assertEquals("los angeles", request("!tz los angeles > tokyo").getSourceToken());
		assertEquals("salt lake city", request("!tz salt lake city 09:00 > tokyo").getSourceToken());
	}

	@Test
	public void anUnknownMultiWordSourceIsHandedBackWhole()
	{
		// The parser does not resolve zones; the caller reports the unknown token.
		assertEquals("foo bar", request("!tz foo bar 20:30 > NZST").getSourceToken());
	}

	// -- Time forms --------------------------------------------------------------------

	@Test
	public void readsTheTwentyFourHourForms()
	{
		assertEquals(LocalTime.of(20, 30), CommandParser.parseTime("20:30"));
		assertEquals(LocalTime.of(8, 30), CommandParser.parseTime("8:30"));
		assertEquals(LocalTime.of(20, 30), CommandParser.parseTime("2030"));
		assertEquals(LocalTime.of(8, 30), CommandParser.parseTime("830"));
		assertEquals(LocalTime.of(0, 0), CommandParser.parseTime("00:00"));
		assertEquals(LocalTime.of(23, 59), CommandParser.parseTime("2359"));
	}

	@Test
	public void readsTheTwelveHourForms()
	{
		assertEquals(LocalTime.of(20, 30), CommandParser.parseTime("8:30pm"));
		assertEquals(LocalTime.of(8, 30), CommandParser.parseTime("8:30am"));
		assertEquals(LocalTime.of(20, 0), CommandParser.parseTime("8pm"));
		assertEquals(LocalTime.of(8, 0), CommandParser.parseTime("8AM"));
		assertEquals(LocalTime.of(12, 0), CommandParser.parseTime("12pm"));
		assertEquals(LocalTime.of(0, 0), CommandParser.parseTime("12am"));
		assertEquals(LocalTime.of(0, 30), CommandParser.parseTime("12:30am"));
	}

	@Test
	public void bareTimesWithoutAmPmAreTwentyFourHour()
	{
		// D5 accepts this ambiguity explicitly: 8:30 is morning, not evening.
		assertEquals(LocalTime.of(8, 30), CommandParser.parseTime("8:30"));
	}

	@Test
	public void rejectsThingsThatAreNotTimes()
	{
		assertNull(CommandParser.parseTime(null));
		assertNull(CommandParser.parseTime(""));
		assertNull(CommandParser.parseTime("EST"));
		assertNull(CommandParser.parseTime("now"));
		assertNull(CommandParser.parseTime("25:00"));
		assertNull(CommandParser.parseTime("20:60"));
		assertNull(CommandParser.parseTime("2460"));
		assertNull(CommandParser.parseTime("13pm"));
		assertNull(CommandParser.parseTime("0pm"));
		assertNull(CommandParser.parseTime("20:3"));
		assertNull(CommandParser.parseTime("20-30"));
	}

	@Test
	public void bareOneAndTwoDigitNumbersAreNotTimes()
	{
		// Otherwise "8" would swallow a zone token and silently mean 08:00.
		assertNull(CommandParser.parseTime("8"));
		assertNull(CommandParser.parseTime("20"));
	}

	@Test
	public void nowCountsAsATimeTokenEvenThoughItHasNoValue()
	{
		assertTrue(CommandParser.isTimeToken("now"));
		assertTrue(CommandParser.isTimeToken("NOW"));
		assertTrue(CommandParser.isTimeToken("20:30"));
		assertFalse(CommandParser.isTimeToken("EST"));
		assertNull(CommandParser.parseTime("now"));
	}

	@Test
	public void theTimeMayAlsoComeBeforeTheZone()
	{
		ConversionRequest request = request("!tz 8pm foo bar > NZST");

		assertEquals("foo bar", request.getSourceToken());
		assertEquals(LocalTime.of(20, 0), request.getTime());
	}

	// -- Malformed input ---------------------------------------------------------------

	@Test
	public void aLineWithoutOurTriggerIsNotOurs()
	{
		assertEquals(ParsedCommand.Problem.NOT_A_COMMAND, problem("hello everyone"));
		assertEquals(ParsedCommand.Problem.NOT_A_COMMAND, problem("!kc zulrah"));
		assertEquals(ParsedCommand.Problem.NOT_A_COMMAND, problem(""));
		assertEquals(ParsedCommand.Problem.NOT_A_COMMAND, problem(null));
	}

	@Test
	public void theTriggerAloneHasNoArguments()
	{
		assertEquals(ParsedCommand.Problem.NO_ARGUMENTS, problem("!tz"));
		assertEquals(ParsedCommand.Problem.NO_ARGUMENTS, problem("::tz   "));
	}

	@Test
	public void aTimeWithNoZoneOnEitherSideIsDegenerate()
	{
		// D5: print the usage hint rather than echoing the input back.
		assertEquals(ParsedCommand.Problem.NO_ZONE_GIVEN, problem("!tz 20:30"));
		assertEquals(ParsedCommand.Problem.NO_ZONE_GIVEN, problem("!tz now"));
	}

	@Test
	public void junkWhereTheTimeShouldBeIsReportedWithTheOffendingText()
	{
		ParsedCommand parsed = parser.parse("!tz EST banana > NZST");

		assertFalse(parsed.isValid());
		assertEquals(ParsedCommand.Problem.BAD_TIME, parsed.getProblem());
		assertEquals("banana", parsed.getDetail());
		assertNull(parsed.getRequest());
	}

	@Test
	public void aTrailingSeparatorLeavesTheTargetLocal()
	{
		ConversionRequest request = request("!tz EST 20:30 >");

		assertEquals("EST", request.getSourceToken());
		assertNull(request.getTargetToken());
	}

	// -- The pre-split path used by ::tz -------------------------------------------------

	@Test
	public void argumentsFromCommandExecutedParseTheSameWay()
	{
		ParsedCommand parsed = parser.parseArguments(new String[]{"EST", "20:30", ">", "NZST"});

		assertTrue(parsed.isValid());
		assertEquals(request("!tz EST 20:30 > NZST"), parsed.getRequest());
	}

	@Test
	public void emptyOrNullArgumentsAreNoArguments()
	{
		assertEquals(ParsedCommand.Problem.NO_ARGUMENTS, parser.parseArguments(new String[0]).getProblem());
		assertEquals(ParsedCommand.Problem.NO_ARGUMENTS, parser.parseArguments(null).getProblem());
		assertEquals(ParsedCommand.Problem.NO_ARGUMENTS, parser.parseArguments(new String[]{"", "  "}).getProblem());
	}

	// -- The request value type ----------------------------------------------------------

	@Test
	public void requestsCompareByValue()
	{
		ConversionRequest one = new ConversionRequest("EST", "NZST", LocalTime.of(20, 30));
		ConversionRequest same = new ConversionRequest("EST", "NZST", LocalTime.of(20, 30));

		assertEquals(one, same);
		assertEquals(one.hashCode(), same.hashCode());
		assertFalse(one.equals(new ConversionRequest("PST", "NZST", LocalTime.of(20, 30))));
		assertFalse(one.equals(new ConversionRequest("EST", "NZST", null)));
		assertFalse(one.equals("EST"));
	}
}
