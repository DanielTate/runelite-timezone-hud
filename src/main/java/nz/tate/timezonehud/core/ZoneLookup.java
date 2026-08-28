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

import java.util.Objects;

/**
 * The outcome of asking {@link ZoneRegistry} about a token the user typed.
 *
 * <p>Callers must be able to tell the failure modes apart, because they produce different
 * messages: an unrecognised token is a typo, whereas a recognised but contested one needs a
 * "did you mean" listing the candidates.
 */
public final class ZoneLookup
{
	public enum Status
	{
		RESOLVED,
		UNKNOWN
	}

	private final Status status;
	private final String token;
	private final ZoneEntry zone;

	private ZoneLookup(Status status, String token, ZoneEntry zone)
	{
		this.status = status;
		this.token = token;
		this.zone = zone;
	}

	public static ZoneLookup resolved(String token, ZoneEntry zone)
	{
		Objects.requireNonNull(zone, "zone");
		return new ZoneLookup(Status.RESOLVED, token, zone);
	}

	public static ZoneLookup unknown(String token)
	{
		return new ZoneLookup(Status.UNKNOWN, token, null);
	}

	public Status getStatus()
	{
		return status;
	}

	/**
	 * The token as the user typed it, for echoing back in an error message.
	 */
	public String getToken()
	{
		return token;
	}

	public boolean isResolved()
	{
		return status == Status.RESOLVED;
	}

	public boolean isUnknown()
	{
		return status == Status.UNKNOWN;
	}

	/**
	 * @return the matched zone, or null unless {@link #isResolved()}
	 */
	public ZoneEntry getZone()
	{
		return zone;
	}

	@Override
	public String toString()
	{
		return status + "(" + token + (zone == null ? "" : " -> " + zone) + ")";
	}
}
