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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Objects;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * The in-game clock panel: every clock in the list, in list order, per D3.
 *
 * <p>DETACHED is what makes the overlay user-movable (it sets {@code movable}) and what
 * makes RuneLite remember where the user put it between sessions. It is marked deprecated
 * in the client but is still the only position that is movable without snapping to a
 * corner, which is what D3 asks for -- see RISKS R12.
 *
 * <p>D11's master toggle is checked here rather than by adding and removing the overlay,
 * so turning it off and on again does not lose the position the user dragged it to.
 */
public class TimezoneHudOverlay extends OverlayPanel
{
	private final TimezoneHudConfig config;
	private final ClockListModel clockList;
	private final ClockLineCache lineCache;

	/** The rows currently loaded into the panel, so an unchanged frame does no work. */
	private List<ClockLine> drawnLines;

	public TimezoneHudOverlay(TimezoneHudPlugin plugin, TimezoneHudConfig config,
		ClockListModel clockList, java.time.Clock clock)
	{
		super(plugin);
		this.config = Objects.requireNonNull(config, "config");
		this.clockList = Objects.requireNonNull(clockList, "clockList");
		this.lineCache = new ClockLineCache(clock);

		setPosition(OverlayPosition.DETACHED);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_LOW);

		// OverlayPanel throws its components away after every frame by default, which
		// would force a rebuild each time and undo the caching below.
		setClearChildren(false);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		List<ClockLine> lines = lineCache.getLines(clockList.getClocks(), config.twentyFourHour());
		if (lines.isEmpty())
		{
			return null;
		}

		// The cache hands back the same list object until something actually changes, so
		// this comparison is how a normal frame gets away with drawing what is already
		// there instead of rebuilding the panel's components.
		if (lines != drawnLines)
		{
			panelComponent.getChildren().clear();
			for (ClockLine line : lines)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(line.getLabel())
					.right(line.getTime())
					.build());
			}
			drawnLines = lines;
		}

		return super.render(graphics);
	}
}
