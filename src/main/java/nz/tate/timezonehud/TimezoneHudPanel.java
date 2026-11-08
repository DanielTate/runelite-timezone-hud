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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import nz.tate.timezonehud.core.WorldClock;
import nz.tate.timezonehud.core.ZoneEntry;

/**
 * The clock editor, per D2: search every zone the JDK knows, add one, rename it, reorder it,
 * remove it. Panel order is HUD order (D3), so the arrows are meaningful rather than
 * decorative. Under the clocks sit switches for the two display settings, so they can be
 * flipped without opening the config panel.
 *
 * <p>All of this runs on the Swing thread and none of it touches {@code Client}, so nothing
 * here needs {@code clientThread.invoke()}. Every decision worth testing -- what a search
 * finds, what a row says -- lives in {@link ZoneSearch} and {@link ClockLineCache}, which do
 * not need a display.
 */
public class TimezoneHudPanel extends PluginPanel
{
	private static final int MAX_SEARCH_RESULTS = 8;

	/**
	 * D9 leaves seconds off the display, so a row can only change once a minute. Ticking
	 * every second anyway keeps the change within a second of the real one, and the cache
	 * makes all but one of those ticks free.
	 */
	private static final int REFRESH_MILLIS = 1000;

	/** Must match runelite-plugin.properties; a test checks it does. */
	static final String VERSION = "1.1";

	/** Splits "14:05 NZDT  Sun 9" or "2:05 PM NZDT" into the time and the rest. */
	private static final Pattern TIME = Pattern.compile("^(\\d{1,2}:\\d{2}(?: [AaPp][Mm])?)\\s+(.*)$");

	private final ClockListModel clockList;
	private final TimezoneHudConfig config;
	private final ClockLineCache lineCache;
	private final BiConsumer<String, Boolean> setSetting;

	private final IconTextField searchField = new IconTextField();
	private final JPanel searchResults = PanelStyle.column(2);
	private final JPanel clockRows = PanelStyle.column(4);
	private final JLabel clockCount = PanelStyle.small("");
	private final PanelStyle.Switch showOverlay;
	private final PanelStyle.Switch twentyFourHour;
	private final List<JLabel[]> timeLabels = new ArrayList<>();
	private final Timer refreshTimer;
	private final Runnable clockListListener = this::onClockListChanged;

	private List<ClockLine> shownLines;

	/** {@code setSetting} changes a boolean config key, for the display switches. */
	public TimezoneHudPanel(ClockListModel clockList, TimezoneHudConfig config, java.time.Clock clock,
		BiConsumer<String, Boolean> setSetting)
	{
		this.clockList = Objects.requireNonNull(clockList, "clockList");
		this.config = Objects.requireNonNull(config, "config");
		this.lineCache = new ClockLineCache(clock);
		this.setSetting = Objects.requireNonNull(setSetting, "setSetting");
		showOverlay = new PanelStyle.Switch(config.showOverlay());
		twentyFourHour = new PanelStyle.Switch(config.twentyFourHour());

		setLayout(new BorderLayout(0, PanelStyle.GAP));
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel sections = PanelStyle.column(PanelStyle.GAP);
		sections.add(header());
		sections.add(PanelStyle.card("World clocks", clockCount, clockRows));
		sections.add(PanelStyle.card("Add a clock", null, buildSearchArea()));
		sections.add(PanelStyle.card("Display", null, buildDisplayArea()));
		add(sections, BorderLayout.NORTH);
		add(versionLabel(), BorderLayout.SOUTH);

		refreshTimer = new Timer(REFRESH_MILLIS, e -> refreshTimes());
		clockList.addListener(clockListListener);
		rebuildClockRows();
	}

	private static JComponent header()
	{
		JLabel title = new JLabel("Timezone HUD");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
		title.setForeground(Color.WHITE);
		JPanel header = PanelStyle.column(2);
		header.add(title);
		header.add(PanelStyle.small("Clocks for anywhere, shown in game"));
		return header;
	}

	private static JLabel versionLabel()
	{
		JLabel version = new JLabel("v" + VERSION, SwingConstants.RIGHT);
		version.setFont(FontManager.getRunescapeSmallFont());
		version.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
		return version;
	}

	@Override
	public void onActivate()
	{
		// Only tick while the user is looking at the panel.
		refreshTimes();
		refreshTimer.start();
	}

	@Override
	public void onDeactivate()
	{
		refreshTimer.stop();
	}

	/**
	 * Releases the timer and the model listener. Called when the plugin shuts down, which
	 * can happen while the panel is still open.
	 */
	public void stop()
	{
		refreshTimer.stop();
		clockList.removeListener(clockListListener);
	}

	/** Keeps the switches and the times in step when a setting changes elsewhere. */
	public void settingsChanged()
	{
		showOverlay.setOn(config.showOverlay());
		twentyFourHour.setOn(config.twentyFourHour());
		rebuildClockRows();
	}

	private JComponent buildSearchArea()
	{
		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(0, 30));
		searchField.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.setToolTipText("City, abbreviation or IANA zone id -- try auckland, EST or Europe/Paris");
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateSearchResults();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateSearchResults();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateSearchResults();
			}
		});
		searchField.addClearListener(this::updateSearchResults);

		JPanel area = PanelStyle.column(6);
		area.add(PanelStyle.fixedHeight(searchField));
		area.add(PanelStyle.small("Try a city, EST or Europe/Paris"));
		area.add(searchResults);
		updateSearchResults();
		return area;
	}

	private JComponent buildDisplayArea()
	{
		showOverlay.onChange(on -> setSetting.accept("showOverlay", on));
		twentyFourHour.onChange(on -> setSetting.accept("twentyFourHour", on));
		JPanel area = PanelStyle.column(10);
		area.add(PanelStyle.toggle(showOverlay, "Clocks on screen", "The clock box over the game"));
		area.add(PanelStyle.toggle(twentyFourHour, "24-hour time", "Off shows 2:05 PM"));
		return area;
	}

	private void updateSearchResults()
	{
		searchResults.removeAll();
		String query = searchField.getText();
		List<ZoneEntry> found = ZoneSearch.search(query, MAX_SEARCH_RESULTS);
		searchResults.setVisible(!query.trim().isEmpty());
		if (found.isEmpty())
		{
			searchResults.add(PanelStyle.small("No time zones match."));
		}
		for (ZoneEntry entry : found)
		{
			JPanel row = PanelStyle.hoverRow(new BorderLayout(0, 1), () -> addClock(entry));
			row.setToolTipText("Add a clock for " + entry.getDisplayName());
			row.add(PanelStyle.text(entry.getDisplayName()), BorderLayout.NORTH);
			row.add(PanelStyle.small(entry.getZoneId()), BorderLayout.SOUTH);
			searchResults.add(PanelStyle.fixedHeight(row));
		}

		searchResults.revalidate();
		searchResults.repaint();
	}

	private void addClock(ZoneEntry entry)
	{
		clockList.addClock(WorldClock.forZone(entry));
		searchField.setText("");
	}

	private void onClockListChanged()
	{
		// Edits come from this panel today, but a model change must reach Swing safely
		// whoever made it.
		SwingUtilities.invokeLater(this::rebuildClockRows);
	}

	private void rebuildClockRows()
	{
		List<ClockLine> lines = lineCache.getLines(clockList.getClocks(), config.twentyFourHour());
		List<WorldClock> clocks = clockList.getClocks();

		clockRows.removeAll();
		timeLabels.clear();
		clockCount.setText(lines.size() == 1 ? "1 clock" : lines.size() + " clocks");

		if (lines.isEmpty())
		{
			clockRows.add(PanelStyle.small("No clocks yet. Search below to add one."));
		}

		for (int index = 0; index < lines.size(); index++)
		{
			String zone = index < clocks.size() ? clocks.get(index).getZoneId() : "";
			clockRows.add(buildClockRow(index, lines.get(index), zone, lines.size()));
		}

		shownLines = lines;
		clockRows.revalidate();
		clockRows.repaint();
	}

	/**
	 * One clock: its name, then the time large with the zone and any date beside it, and the
	 * actions at the end of that line.
	 */
	private JComponent buildClockRow(int index, ClockLine line, String zone, int total)
	{
		JLabel label = PanelStyle.text(line.getLabel());
		label.setToolTipText(zone);

		JLabel time = new JLabel();
		time.setFont(FontManager.getRunescapeBoldFont());
		time.setForeground(Color.WHITE);
		JLabel rest = PanelStyle.small("");
		showTime(line.getTime(), time, rest);
		timeLabels.add(new JLabel[]{time, rest});

		JPanel timeLine = new JPanel(new BorderLayout(6, 0));
		timeLine.setOpaque(false);
		timeLine.add(time, BorderLayout.WEST);
		timeLine.add(rest, BorderLayout.CENTER);
		JPanel timeAndActions = new JPanel(new BorderLayout(4, 0));
		timeAndActions.setOpaque(false);
		timeAndActions.add(timeLine, BorderLayout.CENTER);

		JLabel up = PanelStyle.link("\u25B4", "Move up", () -> clockList.moveClock(index, index - 1));
		PanelStyle.enable(up, index > 0);
		JLabel down = PanelStyle.link("\u25BE", "Move down", () -> clockList.moveClock(index, index + 1));
		PanelStyle.enable(down, index < total - 1);
		JLabel rename = PanelStyle.link("\u270E", "Rename this clock", () -> renameClock(index, line.getLabel()));
		JLabel remove = PanelStyle.link("\u2715", "Remove this clock", () -> confirmRemove(index, line.getLabel()));
		JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 5, 0));
		actions.setOpaque(false);
		actions.add(up);
		actions.add(down);
		actions.add(rename);
		actions.add(remove);

		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(label, BorderLayout.CENTER);
		timeAndActions.add(actions, BorderLayout.EAST);

		JPanel row = PanelStyle.column(2);
		row.setOpaque(true);
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(6, 8, 6, 8));
		row.add(top);
		row.add(timeAndActions);
		return PanelStyle.fixedHeight(row);
	}

	/** Puts the time in {@code time} and the zone and any date in {@code rest}. */
	private static void showTime(String formatted, JLabel time, JLabel rest)
	{
		Matcher m = TIME.matcher(formatted);
		if (m.matches())
		{
			time.setText(m.group(1));
			rest.setText(m.group(2).trim().replaceAll("\\s{2,}", " \u00B7 "));
		}
		else
		{
			time.setText(formatted);
			rest.setText("");
		}
	}

	private void renameClock(int index, String currentLabel)
	{
		Object label = JOptionPane.showInputDialog(this, "Name:", "Rename clock", JOptionPane.PLAIN_MESSAGE,
			null, null, currentLabel);
		if (label != null && !label.toString().trim().isEmpty())
		{
			clockList.renameClock(index, label.toString().trim());
		}
	}

	private void confirmRemove(int index, String label)
	{
		int answer = JOptionPane.showConfirmDialog(this, "Remove the " + label + " clock?", "Remove clock",
			JOptionPane.YES_NO_OPTION);
		if (answer == JOptionPane.YES_OPTION)
		{
			clockList.removeClock(index);
		}
	}

	private void refreshTimes()
	{
		List<ClockLine> lines = lineCache.getLines(clockList.getClocks(), config.twentyFourHour());
		if (lines == shownLines)
		{
			return;
		}

		if (lines.size() != timeLabels.size())
		{
			rebuildClockRows();
			return;
		}

		for (int index = 0; index < lines.size(); index++)
		{
			JLabel[] labels = timeLabels.get(index);
			showTime(lines.get(index).getTime(), labels[0], labels[1]);
		}

		shownLines = lines;
	}
}
