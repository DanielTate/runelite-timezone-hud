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

import java.time.ZoneId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

public class ZoneEntryTest
{
	@Test
	public void exposesTheZoneAndItsIanaId()
	{
		ZoneEntry entry = new ZoneEntry("America/New_York", "US Eastern");

		assertEquals(ZoneId.of("America/New_York"), entry.getZone());
		assertEquals("America/New_York", entry.getZoneId());
		assertEquals("US Eastern", entry.getDisplayName());
	}

	@Test
	public void theZoneIdConstructorMatchesTheZoneConstructor()
	{
		assertEquals(new ZoneEntry("UTC", "UTC"), new ZoneEntry(ZoneId.of("UTC"), "UTC"));
	}

	@Test
	public void equalityCoversBothTheZoneAndTheLabel()
	{
		ZoneEntry eastern = new ZoneEntry("America/New_York", "US Eastern");

		assertEquals(eastern, new ZoneEntry("America/New_York", "US Eastern"));
		assertEquals(eastern.hashCode(), new ZoneEntry("America/New_York", "US Eastern").hashCode());
		assertNotEquals(eastern, new ZoneEntry("America/New_York", "New York"));
		assertNotEquals(eastern, new ZoneEntry("America/Chicago", "US Eastern"));
		assertNotEquals(eastern, "America/New_York");
	}

	@Test
	public void toStringNamesBothHalves()
	{
		assertEquals("US Eastern (America/New_York)", new ZoneEntry("America/New_York", "US Eastern").toString());
	}

	@Test(expected = NullPointerException.class)
	public void aNullDisplayNameIsRejectedAtConstruction()
	{
		new ZoneEntry("UTC", null);
	}
}
