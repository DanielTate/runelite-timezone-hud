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
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import nz.tate.timezonehud.core.ClockRepository;

/**
 * Wires the plugin together: storage, the shared clock list, the in-game overlay, the side
 * panel and the two chat commands.
 *
 * <p>Everything with any logic in it lives in a class that imports nothing from RuneLite,
 * so this file stays close to pure wiring. The only background task is the side panel's
 * Swing timer, which runs while the panel is open; the overlay reads the time as it draws.
 */
@Slf4j
@PluginDescriptor(
	name = "Timezone HUD",
	description = "World clocks in a movable overlay and side panel, with a !TZ time converter",
	tags = {"time", "timezone", "clock", "converter", "utc"}
)
public class TimezoneHudPlugin extends Plugin
{
	/** RuneLite strips the "::" before it hands the command over. */
	private static final String LOCAL_COMMAND = "tz";

	/** Matched case-insensitively by ChatCommandManager, so !TZ and !tz both work. */
	private static final String PUBLIC_COMMAND = "!tz";

	@Inject
	private Client client;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Gson gson;

	@Inject
	private TimezoneHudConfig config;

	private ClockListModel clockList;
	private CommandResponder responder;
	private TimezoneHudOverlay overlay;
	private TimezoneHudPanel panel;
	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		// The one place the real time enters the plugin. Everything downstream takes this
		// Clock as an argument, which is what lets the tests pin a fixed instant.
		java.time.Clock systemClock = java.time.Clock.systemDefaultZone();

		ConfigClockStore store = new ConfigClockStore(new ConfigManagerValues(configManager), gson);
		clockList = new ClockListModel(new ClockRepository(store, systemClock));
		responder = new CommandResponder(systemClock);

		overlay = new TimezoneHudOverlay(this, config, clockList, systemClock);
		overlayManager.add(overlay);

		panel = new TimezoneHudPanel(clockList, config, systemClock,
			(key, value) -> configManager.setConfiguration(TimezoneHudConfig.GROUP, key, value));
		navigationButton = NavigationButton.builder()
			.tooltip("World clocks")
			.icon(ImageUtil.loadImageResource(TimezoneHudPlugin.class, "icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		// Only the execute half is supplied. The other half of registerCommand is the
		// before-send hook, which is the rule-breaking path -- see ChatDisplayRewriter.
		chatCommandManager.registerCommand(PUBLIC_COMMAND, this::onPublicCommand);

		log.debug("Timezone HUD started with {} clocks", clockList.getClocks().size());
	}

	@Override
	protected void shutDown()
	{
		chatCommandManager.unregisterCommand(PUBLIC_COMMAND);
		clientToolbar.removeNavigation(navigationButton);
		overlayManager.remove(overlay);

		// The panel may still be on screen; stopping its timer has to happen on the EDT.
		SwingUtilities.invokeLater(panel::stop);
	}

	/**
	 * D4's local path. {@code ::tz} is a client-only command -- RuneLite never sends it to
	 * the server -- so the answer is simply printed for the player who typed it.
	 */
	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!LOCAL_COMMAND.equalsIgnoreCase(event.getCommand()))
		{
			return;
		}

		CommandResponse response = responder.respondToArguments(event.getArguments());
		if (response.hasText())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", response.getText(), null);
		}
	}

	/**
	 * D4's public path. The message has already gone to the server exactly as typed; all
	 * that happens here is that the copy this client received is drawn differently.
	 * {@link ChatDisplayRewriter} explains at length why it must stay that way.
	 */
	private void onPublicCommand(ChatMessage chatMessage, String message)
	{
		CommandResponse response = responder.respondToMessage(message);

		if (ChatDisplayRewriter.rewrite(chatMessage, response))
		{
			client.refreshChat();
		}
	}

	/** Keeps the side panel's switches and times in step with settings changed anywhere. */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (TimezoneHudConfig.GROUP.equals(event.getGroup()) && panel != null)
		{
			TimezoneHudPanel shown = panel;
			SwingUtilities.invokeLater(shown::settingsChanged);
		}
	}

	@Provides
	TimezoneHudConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TimezoneHudConfig.class);
	}
}
