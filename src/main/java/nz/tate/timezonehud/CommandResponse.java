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

/**
 * What a {@code !TZ} or {@code ::tz} line should produce: a conversion, a complaint, or
 * nothing at all.
 *
 * <p>The two are kept apart because the two commands want different things from a
 * complaint. {@code ::tz} is private, so it prints the hint. {@code !TZ} is a message
 * everyone else already sees as typed, and quietly replacing it locally with an error
 * would be more confusing than leaving it alone.
 */
public final class CommandResponse
{
	private static final CommandResponse SILENT = new CommandResponse(false, null);

	private final boolean conversion;
	private final String text;

	private CommandResponse(boolean conversion, String text)
	{
		this.conversion = conversion;
		this.text = text;
	}

	/** The line was not for us. */
	public static CommandResponse silent()
	{
		return SILENT;
	}

	/** A successful conversion, formatted per D8. */
	public static CommandResponse conversion(String text)
	{
		return new CommandResponse(true, text);
	}

	/** A usage hint or an explanation of what went wrong. */
	public static CommandResponse problem(String text)
	{
		return new CommandResponse(false, text);
	}

	public boolean hasText()
	{
		return text != null;
	}

	public boolean isConversion()
	{
		return conversion;
	}

	/**
	 * @return the text to show, or null when there is nothing to say
	 */
	public String getText()
	{
		return text;
	}

	@Override
	public String toString()
	{
		return text == null ? "silent" : text;
	}
}
