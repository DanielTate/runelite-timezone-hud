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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Owns the clock list: what is in it, what order it is in, and what a brand new user starts
 * with. Persistence is delegated to a {@link ClockStore} so none of this depends on RuneLite.
 *
 * <p>D10: on first run the list is pre-populated with Local and Jagex (UTC) as ordinary,
 * deletable, renameable rows. An empty panel would teach a new user nothing, and game time
 * is the single most useful clock for coordinating anything in RuneScape.
 *
 * <p>Seeding is guarded by the store's first-run flag rather than by "is the list empty",
 * so a list the user has deliberately cleared stays cleared. An empty panel is a legitimate
 * resting state.
 */
public class ClockRepository
{
	public static final String LOCAL_LABEL = "Local";

	/** D10 names this after the game rather than after the zone, deliberately. */
	public static final String JAGEX_LABEL = "Jagex (UTC)";

	private final ClockStore store;
	private final java.time.Clock clock;

	public ClockRepository(ClockStore store, java.time.Clock clock)
	{
		this.store = Objects.requireNonNull(store, "store");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * The clocks in display order, seeding first if this is the user's first run.
	 *
	 * @return an unmodifiable snapshot; use the methods below to change anything
	 */
	public List<WorldClock> getClocks()
	{
		if (!store.isSeeded())
		{
			List<WorldClock> seeded = seedClocks(clock.getZone());
			store.saveClocks(seeded);
			store.markSeeded();
			return Collections.unmodifiableList(seeded);
		}

		return Collections.unmodifiableList(new ArrayList<>(store.loadClocks()));
	}

	/**
	 * What a new user starts with. Pure, so the seeding rule can be tested on its own.
	 *
	 * <p>Local is a real zone, resolved from the user's machine, rather than a formatting
	 * shortcut -- see RISKS R2 for the consequence of resolving it once at seeding time.
	 */
	public static List<WorldClock> seedClocks(ZoneId localZone)
	{
		List<WorldClock> seeded = new ArrayList<>(2);
		seeded.add(new WorldClock(localZone, LOCAL_LABEL));
		seeded.add(new WorldClock(ZoneId.of("UTC"), JAGEX_LABEL));
		return seeded;
	}

	public void addClock(WorldClock added)
	{
		Objects.requireNonNull(added, "added");
		List<WorldClock> clocks = mutableClocks();
		clocks.add(added);
		setClocks(clocks);
	}

	public void removeClock(int index)
	{
		List<WorldClock> clocks = mutableClocks();
		clocks.remove(index);
		setClocks(clocks);
	}

	public void renameClock(int index, String label)
	{
		List<WorldClock> clocks = mutableClocks();
		clocks.set(index, clocks.get(index).withLabel(label));
		setClocks(clocks);
	}

	/**
	 * Reorders one clock. D3 makes panel order and HUD order the same thing, so this is
	 * directly meaningful rather than cosmetic.
	 */
	public void moveClock(int from, int to)
	{
		List<WorldClock> clocks = mutableClocks();
		if (to < 0 || to >= clocks.size())
		{
			throw new IndexOutOfBoundsException("no such position: " + to);
		}
		clocks.add(to, clocks.remove(from));
		setClocks(clocks);
	}

	/**
	 * Replaces the whole list. Also marks the store seeded, so clearing the list here is
	 * respected rather than undone on the next read.
	 */
	public void setClocks(List<WorldClock> clocks)
	{
		Objects.requireNonNull(clocks, "clocks");
		store.saveClocks(new ArrayList<>(clocks));
		store.markSeeded();
	}

	private List<WorldClock> mutableClocks()
	{
		return new ArrayList<>(getClocks());
	}
}
