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
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import nz.tate.timezonehud.core.WorldClock;
import nz.tate.timezonehud.core.ZoneEntry;

/**
 * The clock editor, per D2: search every zone the JDK knows, add one, rename it, reorder it,
 * remove it. Panel order is HUD order (D3), so the arrows are meaningful rather than
 * decorative.
 *
 * <p>There is no per-clock visibility switch here. D3 is explicit that adding a zone is the
 * act of showing it, and D11's single overlay toggle lives in the config panel instead.
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

	private final ClockListModel clockList;
	private final TimezoneHudConfig config;
	private final ClockLineCache lineCache;

	private final JTextField searchField = new JTextField();
	private final JPanel searchResults = new JPanel();
	private final JPanel clockRows = new JPanel();
	private final List<JLabel> timeLabels = new ArrayList<>();
	private final Timer refreshTimer;
	private final Runnable clockListListener = this::onClockListChanged;

	private List<ClockLine> shownLines;

	public TimezoneHudPanel(ClockListModel clockList, TimezoneHudConfig config, java.time.Clock clock)
	{
		this.clockList = Objects.requireNonNull(clockList, "clockList");
		this.config = Objects.requireNonNull(config, "config");
		this.lineCache = new ClockLineCache(clock);

		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildSearchArea(), BorderLayout.NORTH);
		add(buildClockArea(), BorderLayout.CENTER);

		refreshTimer = new Timer(REFRESH_MILLIS, e -> refreshTimes());
		clockList.addListener(clockListListener);
		rebuildClockRows();
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

	private JPanel buildSearchArea()
	{
		JPanel area = new JPanel();
		area.setLayout(new BoxLayout(area, BoxLayout.Y_AXIS));
		area.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Add a clock");
		title.setForeground(ColorScheme.TEXT_COLOR);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		searchField.setToolTipText("City, abbreviation or IANA zone id -- try auckland, EST or Europe/Paris");
		searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
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

		searchResults.setLayout(new BoxLayout(searchResults, BoxLayout.Y_AXIS));
		searchResults.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchResults.setAlignmentX(Component.LEFT_ALIGNMENT);

		area.add(title);
		area.add(Box.createVerticalStrut(4));
		area.add(searchField);
		area.add(Box.createVerticalStrut(4));
		area.add(searchResults);
		return area;
	}

	private JPanel buildClockArea()
	{
		clockRows.setLayout(new BoxLayout(clockRows, BoxLayout.Y_AXIS));
		clockRows.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Wrapping in BorderLayout.NORTH keeps the rows at the top rather than stretching
		// them down the panel when there are only a few.
		JPanel area = new JPanel(new BorderLayout());
		area.setBackground(ColorScheme.DARK_GRAY_COLOR);
		area.add(clockRows, BorderLayout.NORTH);
		return area;
	}

	private void updateSearchResults()
	{
		searchResults.removeAll();

		for (ZoneEntry entry : ZoneSearch.search(searchField.getText(), MAX_SEARCH_RESULTS))
		{
			JButton add = new JButton(entry.getDisplayName());
			add.setToolTipText(entry.getZoneId());
			add.setAlignmentX(Component.LEFT_ALIGNMENT);
			add.addActionListener(e -> addClock(entry));
			searchResults.add(add);
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

		clockRows.removeAll();
		timeLabels.clear();

		if (lines.isEmpty())
		{
			JLabel empty = new JLabel("No clocks yet.");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			clockRows.add(empty);
		}

		for (int index = 0; index < lines.size(); index++)
		{
			clockRows.add(buildClockRow(index, lines.get(index), lines.size()));
			clockRows.add(Box.createVerticalStrut(4));
		}

		shownLines = lines;
		clockRows.revalidate();
		clockRows.repaint();
	}

	private JPanel buildClockRow(int index, ClockLine line, int total)
	{
		JLabel label = new JLabel(line.getLabel());
		label.setForeground(ColorScheme.TEXT_COLOR);

		JLabel time = new JLabel(line.getTime());
		time.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		timeLabels.add(time);

		JPanel heading = new JPanel(new BorderLayout());
		heading.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		heading.add(label, BorderLayout.WEST);
		heading.add(time, BorderLayout.EAST);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		buttons.add(button("^", "Move up", index > 0, () -> clockList.moveClock(index, index - 1)));
		buttons.add(button("v", "Move down", index < total - 1, () -> clockList.moveClock(index, index + 1)));
		buttons.add(button("Rename", "Rename this clock", true, () -> renameClock(index, line.getLabel())));
		buttons.add(button("X", "Remove this clock", true, () -> clockList.removeClock(index)));

		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.add(heading, BorderLayout.NORTH);
		row.add(buttons, BorderLayout.SOUTH);
		return row;
	}

	private JButton button(String text, String tooltip, boolean enabled, Runnable action)
	{
		JButton button = new JButton(text);
		button.setToolTipText(tooltip);
		button.setEnabled(enabled);
		button.setMargin(new java.awt.Insets(0, 4, 0, 4));
		button.addActionListener(e -> action.run());
		return button;
	}

	private void renameClock(int index, String currentLabel)
	{
		String label = JOptionPane.showInputDialog(this, "Label for this clock:", currentLabel);
		if (label != null && !label.trim().isEmpty())
		{
			clockList.renameClock(index, label.trim());
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
			timeLabels.get(index).setText(lines.get(index).getTime());
		}

		shownLines = lines;
	}
}
