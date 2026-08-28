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
import java.util.List;

/**
 * Stands in for the config-backed store. Copies on the way in and out, the same as a real
 * serialising implementation would, so a test cannot accidentally hold a live reference.
 */
class InMemoryClockStore implements ClockStore
{
	private List<WorldClock> clocks = new ArrayList<>();
	private boolean seeded;
	private int saveCount;

	@Override
	public List<WorldClock> loadClocks()
	{
		return new ArrayList<>(clocks);
	}

	@Override
	public void saveClocks(List<WorldClock> clocks)
	{
		this.clocks = new ArrayList<>(clocks);
		this.saveCount++;
	}

	@Override
	public boolean isSeeded()
	{
		return seeded;
	}

	@Override
	public void markSeeded()
	{
		this.seeded = true;
	}

	int getSaveCount()
	{
		return saveCount;
	}
}
