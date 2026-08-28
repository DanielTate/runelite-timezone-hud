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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The converter's one output string, per D8:
 *
 * <pre>
 * 20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)
 * </pre>
 *
 * <p>Both sides always carry their day, whether or not the conversion rolls over. Printing
 * the day only on a rollover would make its <em>absence</em> the signal, and a chat line is
 * read once and skimmed. Showing both means the reader never has to notice anything.
 *
 * <p>{@code !TZ} and {@code ::tz} both come through here, so the public and local forms
 * cannot drift apart -- which matters because the {@code !TZ} path only ever rewrites the
 * locally displayed message, so the sender would never see a divergence.
 *
 * <p>Each abbreviation is formatted from its own {@link ZonedDateTime}, so it is the one
 * live at that instant. Converting into January from August prints EST, not EDT.
 */
public final class ConversionFormat
{
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter ABBREVIATION = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

	private ConversionFormat()
	{
	}

	public static String format(ZonedDateTime source, ZonedDateTime target)
	{
		return side(source) + " = " + side(target);
	}

	/**
	 * One half of the line: {@code 20:30 EDT (Fri 28 Aug)}. No year -- D8 omits it, which
	 * stays safe only while the grammar cannot express a far-off date.
	 */
	public static String side(ZonedDateTime when)
	{
		return TIME.format(when) + " " + ABBREVIATION.format(when) + " (" + DATE.format(when) + ")";
	}
}
