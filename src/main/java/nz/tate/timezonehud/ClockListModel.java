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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import nz.tate.timezonehud.core.ClockRepository;
import nz.tate.timezonehud.core.WorldClock;

/**
 * The one live copy of the clock list, shared by the overlay and the side panel.
 *
 * <p>It exists for one reason: the overlay's {@code render()} runs every frame and must not
 * touch config. The list is read from storage once at start-up and after each edit, and
 * every reader works off the in-memory copy.
 *
 * <p>Edits arrive from the Swing panel and reads come from the client thread, so the field
 * is volatile and every edit swaps in a whole new list rather than mutating the old one.
 * Readers therefore always see a complete list, never a half-applied edit.
 */
public class ClockListModel
{
	private final ClockRepository repository;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	private volatile List<WorldClock> clocks;

	public ClockListModel(ClockRepository repository)
	{
		this.repository = Objects.requireNonNull(repository, "repository");
		this.clocks = repository.getClocks();
	}

	/**
	 * @return the clocks in display order; unmodifiable, and replaced wholesale on an edit
	 */
	public List<WorldClock> getClocks()
	{
		return clocks;
	}

	public void addClock(WorldClock clock)
	{
		repository.addClock(clock);
		reload();
	}

	public void removeClock(int index)
	{
		repository.removeClock(index);
		reload();
	}

	public void renameClock(int index, String label)
	{
		repository.renameClock(index, label);
		reload();
	}

	public void moveClock(int from, int to)
	{
		repository.moveClock(from, to);
		reload();
	}

	/**
	 * Runs after every edit, on whichever thread made it. Used by the panel to redraw its
	 * rows; the overlay notices changes on its own by comparing lists.
	 */
	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Runnable listener)
	{
		listeners.remove(listener);
	}

	private void reload()
	{
		clocks = repository.getClocks();

		for (Runnable listener : listeners)
		{
			listener.run();
		}
	}
}
