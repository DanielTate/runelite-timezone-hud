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

/**
 * The result of parsing a {@code !TZ} or {@code ::tz} line: either a request, or a reason
 * the line could not become one.
 *
 * <p>The reasons are kept apart because they want different responses. A line that is not a
 * command at all should be ignored silently; a malformed one should get a usage hint.
 */
public final class ParsedCommand
{
	public enum Problem
	{
		NONE,
		/** The line does not begin with one of our triggers. Say nothing. */
		NOT_A_COMMAND,
		/** The trigger with nothing after it. See OPEN-QUESTIONS O4 for what to do. */
		NO_ARGUMENTS,
		/** Neither a source nor a target zone, e.g. "!tz 20:30". D5 wants a usage hint. */
		NO_ZONE_GIVEN,
		/** Something sat where a time should be but is not one. */
		BAD_TIME
	}

	private final Problem problem;
	private final String detail;
	private final ConversionRequest request;

	private ParsedCommand(Problem problem, String detail, ConversionRequest request)
	{
		this.problem = problem;
		this.detail = detail;
		this.request = request;
	}

	public static ParsedCommand of(ConversionRequest request)
	{
		return new ParsedCommand(Problem.NONE, null, request);
	}

	public static ParsedCommand rejected(Problem problem, String detail)
	{
		return new ParsedCommand(problem, detail, null);
	}

	public boolean isValid()
	{
		return problem == Problem.NONE;
	}

	public Problem getProblem()
	{
		return problem;
	}

	/**
	 * @return the offending text, for echoing back in the error, or null if there is none
	 */
	public String getDetail()
	{
		return detail;
	}

	/**
	 * @return the parsed request, or null unless {@link #isValid()}
	 */
	public ConversionRequest getRequest()
	{
		return request;
	}

	@Override
	public String toString()
	{
		return isValid() ? request.toString() : problem + "(" + detail + ")";
	}
}
