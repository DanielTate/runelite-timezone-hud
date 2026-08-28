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

import java.util.List;

/**
 * Where the clock list lives between sessions.
 *
 * <p>This is the seam between the pure core and RuneLite: {@link ClockRepository} owns the
 * rules (ordering, seeding, the first-run flag) and knows nothing about how any of it is
 * written down. The ConfigManager-backed implementation arrives with the config interface.
 *
 * <p>The seeded flag is stored separately from the list on purpose. D10 requires seeding to
 * happen exactly once, guarded by a flag rather than by "is the list empty" -- otherwise a
 * user who deliberately clears every clock gets Local and Jagex back on the next start.
 */
public interface ClockStore
{
	/**
	 * @return the saved clocks in display order; empty if there are none
	 */
	List<WorldClock> loadClocks();

	void saveClocks(List<WorldClock> clocks);

	/**
	 * @return true once seeding has happened, whether or not the clocks survived
	 */
	boolean isSeeded();

	void markSeeded();
}
