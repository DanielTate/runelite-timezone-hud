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
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the D5 grammar shared by {@code !TZ} and {@code ::tz}:
 *
 * <pre>
 * command := trigger [source] [time] [sep target]
 * trigger := "!tz" | "::tz"                (case-insensitive)
 * sep     := "&gt;" | "-&gt;" | "to"
 * time    := HH:MM | H:MM | HHMM | H[:MM]("am"|"pm") | "now"
 * source  := zone-token                    (default: local)
 * target  := zone-token                    (default: local)
 * </pre>
 *
 * <p>Both chat paths funnel through here so they cannot drift apart. {@code !TZ} arrives as
 * a whole raw line and uses {@link #parse(String)}; {@code ::tz} arrives already split into
 * arguments and uses {@link #parseArguments(String[])}.
 *
 * <p>Zone tokens are handed back as the user typed them and are <em>not</em> resolved here.
 * The registry is consulted for one thing only: deciding how many words of "new york 8pm"
 * belong to the zone, by longest match against the alias table (OPEN-QUESTIONS O4).
 */
public final class CommandParser
{
	private static final String[] TRIGGERS = {"!tz", "::tz"};
	private static final String[] SEPARATORS = {">", "->", "to"};
	private static final String NOW = "now";

	public CommandParser()
	{
	}

	/**
	 * Parse a whole chat line, trigger included.
	 */
	public ParsedCommand parse(String message)
	{
		if (message == null)
		{
			return ParsedCommand.rejected(ParsedCommand.Problem.NOT_A_COMMAND, null);
		}

		String[] words = message.trim().split("\\s+");
		if (words.length == 0 || !isTrigger(words[0]))
		{
			return ParsedCommand.rejected(ParsedCommand.Problem.NOT_A_COMMAND, message);
		}

		String[] arguments = new String[words.length - 1];
		System.arraycopy(words, 1, arguments, 0, arguments.length);
		return parseArguments(arguments);
	}

	/**
	 * Parse arguments that have already had the trigger stripped, as RuneLite's
	 * CommandExecuted event delivers them.
	 */
	public ParsedCommand parseArguments(String[] arguments)
	{
		List<String> words = withoutBlanks(arguments);
		if (words.isEmpty())
		{
			return ParsedCommand.rejected(ParsedCommand.Problem.NO_ARGUMENTS, null);
		}

		int separator = indexOfSeparator(words);
		List<String> left = separator < 0 ? words : words.subList(0, separator);
		List<String> right = separator < 0 ? new ArrayList<String>() : words.subList(separator + 1, words.size());

		String targetToken = right.isEmpty() ? null : join(right);

		// The left half is "[source] [time]", either of which may be missing.
		String sourceToken = null;
		LocalTime time = null;

		if (!left.isEmpty())
		{
			int zoneWords = leadingZoneWordCount(left);
			if (zoneWords > 0)
			{
				sourceToken = join(left.subList(0, zoneWords));
				List<String> remainder = left.subList(zoneWords, left.size());

				if (remainder.size() > 1 || (remainder.size() == 1 && !isTimeToken(remainder.get(0))))
				{
					return ParsedCommand.rejected(ParsedCommand.Problem.BAD_TIME, join(remainder));
				}
				if (remainder.size() == 1)
				{
					time = parseTime(remainder.get(0));
				}
			}
			else if (isTimeToken(left.get(left.size() - 1)))
			{
				// "20:30", "new york 8pm" where the zone was not in our table.
				time = parseTime(left.get(left.size() - 1));
				sourceToken = left.size() == 1 ? null : join(left.subList(0, left.size() - 1));
			}
			else if (isTimeToken(left.get(0)))
			{
				// "8pm est" -- the reverse order, accepted because it costs nothing.
				time = parseTime(left.get(0));
				sourceToken = left.size() == 1 ? null : join(left.subList(1, left.size()));
			}
			else
			{
				sourceToken = join(left);
			}
		}

		if (sourceToken == null && targetToken == null)
		{
			// D5: "!TZ 20:30" is degenerate -- there is nothing to convert between.
			return ParsedCommand.rejected(ParsedCommand.Problem.NO_ZONE_GIVEN, join(words));
		}

		return ParsedCommand.of(new ConversionRequest(sourceToken, targetToken, time));
	}

	public static boolean isTrigger(String word)
	{
		for (String trigger : TRIGGERS)
		{
			if (trigger.equalsIgnoreCase(word))
			{
				return true;
			}
		}
		return false;
	}

	public static boolean isSeparator(String word)
	{
		for (String separator : SEPARATORS)
		{
			if (separator.equalsIgnoreCase(word))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * "now" is a time in the grammar, but it carries no wall-clock value.
	 */
	public static boolean isTimeToken(String word)
	{
		return NOW.equalsIgnoreCase(word) || parseTime(word) != null;
	}

	/**
	 * Reads the D5 time forms. Bare one and two digit numbers are deliberately <em>not</em>
	 * times: "8" is far more likely to be a typo than 08:00, and treating it as a time would
	 * swallow a zone token.
	 *
	 * @return the time, or null if the token is not one (including "now")
	 */
	public static LocalTime parseTime(String word)
	{
		if (word == null)
		{
			return null;
		}

		String text = word.trim().toLowerCase();
		boolean twelveHour = text.endsWith("am") || text.endsWith("pm");
		boolean afternoon = text.endsWith("pm");
		if (twelveHour)
		{
			text = text.substring(0, text.length() - 2).trim();
		}

		int hour;
		int minute;
		int colon = text.indexOf(':');

		if (colon >= 0)
		{
			String hourText = text.substring(0, colon);
			String minuteText = text.substring(colon + 1);
			if (!isDigits(hourText) || hourText.length() > 2 || !isDigits(minuteText) || minuteText.length() != 2)
			{
				return null;
			}
			hour = Integer.parseInt(hourText);
			minute = Integer.parseInt(minuteText);
		}
		else if (isDigits(text) && twelveHour && text.length() <= 2)
		{
			hour = Integer.parseInt(text);
			minute = 0;
		}
		else if (isDigits(text) && !twelveHour && text.length() == 3)
		{
			hour = Integer.parseInt(text.substring(0, 1));
			minute = Integer.parseInt(text.substring(1));
		}
		else if (isDigits(text) && !twelveHour && text.length() == 4)
		{
			hour = Integer.parseInt(text.substring(0, 2));
			minute = Integer.parseInt(text.substring(2));
		}
		else
		{
			return null;
		}

		if (twelveHour)
		{
			if (hour < 1 || hour > 12)
			{
				return null;
			}
			if (afternoon && hour != 12)
			{
				hour += 12;
			}
			else if (!afternoon && hour == 12)
			{
				hour = 0;
			}
		}

		if (hour > 23 || minute > 59)
		{
			return null;
		}
		return LocalTime.of(hour, minute);
	}

	/**
	 * How many words at the front of the left half are a zone name, longest match first, so
	 * "new york" beats "new". Returns 0 when nothing matches.
	 */
	private int leadingZoneWordCount(List<String> words)
	{
		int longest = Math.min(words.size(), ZoneRegistry.maxTokenWords());
		for (int length = longest; length >= 1; length--)
		{
			if (ZoneRegistry.isKnownToken(join(words.subList(0, length))))
			{
				return length;
			}
		}
		return 0;
	}

	private static int indexOfSeparator(List<String> words)
	{
		for (int i = 0; i < words.size(); i++)
		{
			if (isSeparator(words.get(i)))
			{
				return i;
			}
		}
		return -1;
	}

	private static List<String> withoutBlanks(String[] arguments)
	{
		List<String> words = new ArrayList<>();
		if (arguments == null)
		{
			return words;
		}
		for (String argument : arguments)
		{
			if (argument != null && !argument.trim().isEmpty())
			{
				words.add(argument.trim());
			}
		}
		return words;
	}

	private static String join(List<String> words)
	{
		StringBuilder joined = new StringBuilder();
		for (String word : words)
		{
			if (joined.length() > 0)
			{
				joined.append(' ');
			}
			joined.append(word);
		}
		return joined.toString();
	}

	private static boolean isDigits(String text)
	{
		if (text.isEmpty())
		{
			return false;
		}
		for (int i = 0; i < text.length(); i++)
		{
			if (!Character.isDigit(text.charAt(i)))
			{
				return false;
			}
		}
		return true;
	}
}
