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

import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import nz.tate.timezonehud.core.ClockRepository;

/**
 * Wires the plugin together: storage, the shared clock list, and the in-game overlay.
 *
 * <p>Everything with any logic in it lives in a class that imports nothing from RuneLite,
 * so this file stays close to pure wiring. There are no timers or background tasks here --
 * the overlay reads the time as it draws.
 */
@Slf4j
@PluginDescriptor(
	name = "Timezone HUD",
	description = "World clocks in a movable overlay and side panel, with a !TZ time converter",
	tags = {"time", "timezone", "clock", "converter", "utc"}
)
public class TimezoneHudPlugin extends Plugin
{
	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Gson gson;

	@Inject
	private TimezoneHudConfig config;

	private ClockListModel clockList;
	private TimezoneHudOverlay overlay;

	@Override
	protected void startUp()
	{
		// The one place the real time enters the plugin. Everything downstream takes this
		// Clock as an argument, which is what lets the tests pin a fixed instant.
		java.time.Clock systemClock = java.time.Clock.systemDefaultZone();

		ConfigClockStore store = new ConfigClockStore(new ConfigManagerValues(configManager), gson);
		clockList = new ClockListModel(new ClockRepository(store, systemClock));

		overlay = new TimezoneHudOverlay(this, config, clockList, systemClock);
		overlayManager.add(overlay);

		log.debug("Timezone HUD started with {} clocks", clockList.getClocks().size());
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlay = null;
		clockList = null;
	}

	@Provides
	TimezoneHudConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TimezoneHudConfig.class);
	}
}
