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
import java.util.List;
import nz.tate.timezonehud.core.CommandParser;
import nz.tate.timezonehud.core.Conversion;
import nz.tate.timezonehud.core.ConversionRequest;
import nz.tate.timezonehud.core.ParsedCommand;
import nz.tate.timezonehud.core.TimeConverter;
import nz.tate.timezonehud.core.ZoneEntry;
import nz.tate.timezonehud.core.ZoneLookup;
import nz.tate.timezonehud.core.ZoneRegistry;

/**
 * Turns a typed command into the line to show. Both {@code !TZ} and {@code ::tz} come
 * through here, which is what makes D8's promise of byte-identical output true rather than
 * merely intended -- and it matters, because {@code !TZ} only ever rewrites what is shown
 * locally, so a divergence would be invisible to the person who typed it.
 *
 * <p>Nothing in this class touches the client, so every input-to-output pair is testable.
 */
public class CommandResponder
{
	static final String USAGE =
		"Usage: !TZ EST 20:30 > NZST -- source, time and target are each optional. ::tz answers privately.";

	private final CommandParser parser = new CommandParser();
	private final TimeConverter converter;

	public CommandResponder(java.time.Clock clock)
	{
		this.converter = new TimeConverter(clock);
	}

	/**
	 * The {@code ::tz} path. RuneLite's CommandExecuted event has already split the
	 * arguments and stripped the trigger.
	 */
	public CommandResponse respondToArguments(String[] arguments)
	{
		return respond(parser.parseArguments(arguments));
	}

	/**
	 * The {@code !TZ} path, which arrives as the whole chat line with the trigger on it.
	 */
	public CommandResponse respondToMessage(String message)
	{
		return respond(parser.parse(message));
	}

	private CommandResponse respond(ParsedCommand parsed)
	{
		if (!parsed.isValid())
		{
			return explainProblem(parsed);
		}

		ConversionRequest request = parsed.getRequest();

		ZoneId source = null;
		if (request.hasSource())
		{
			ZoneLookup lookup = ZoneRegistry.lookup(request.getSourceToken());
			if (!lookup.isResolved())
			{
				return CommandResponse.problem(explainZone(lookup));
			}
			source = lookup.getZone().getZone();
		}

		ZoneId target = null;
		if (request.hasTarget())
		{
			ZoneLookup lookup = ZoneRegistry.lookup(request.getTargetToken());
			if (!lookup.isResolved())
			{
				return CommandResponse.problem(explainZone(lookup));
			}
			target = lookup.getZone().getZone();
		}

		Conversion conversion = converter.convert(request, source, target);
		return CommandResponse.conversion(conversion.format());
	}

	private static CommandResponse explainProblem(ParsedCommand parsed)
	{
		switch (parsed.getProblem())
		{
			case NOT_A_COMMAND:
				return CommandResponse.silent();
			case BAD_TIME:
				return CommandResponse.problem(
					"\"" + parsed.getDetail() + "\" is not a time. Try 20:30, 8:30pm or now.");
			default:
				// NO_ARGUMENTS and NO_ZONE_GIVEN are both "you have not said enough yet".
				return CommandResponse.problem(USAGE);
		}
	}

	/**
	 * D6: an ambiguous token is refused with its candidates and a token that works, and it
	 * has to read differently from a token we simply do not know.
	 */
	private static String explainZone(ZoneLookup lookup)
	{
		if (lookup.isAmbiguous())
		{
			return "\"" + lookup.getToken() + "\" is ambiguous: " + describeCandidates(lookup.getCandidates())
				+ ". Use one of those instead.";
		}

		return "Unknown zone \"" + lookup.getToken()
			+ "\". Try a city, an abbreviation, or an id such as Europe/Paris.";
	}

	private static String describeCandidates(List<ZoneEntry> candidates)
	{
		StringBuilder described = new StringBuilder();

		for (int i = 0; i < candidates.size(); i++)
		{
			if (i > 0)
			{
				described.append(i == candidates.size() - 1 ? " or " : ", ");
			}

			ZoneEntry candidate = candidates.get(i);
			described.append(candidate.getDisplayName())
				.append(" (")
				.append(ZoneRegistry.exampleToken(candidate))
				.append(')');
		}

		return described.toString();
	}
}
