package nz.tate.timezonehud;

import net.runelite.client.ui.overlay.components.ComponentConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The width rule behind the overlay. Measuring text needs a live Graphics2D, so the
 * measurement itself is verified in-game; what is tested here is what the panel does with
 * the number once it has it.
 */
public class TimezoneHudOverlayTest
{
	@Test
	public void shortRowsKeepTheStandardWidth()
	{
		// A narrow list should still look like every other overlay rather than shrinking
		// to fit.
		assertEquals(ComponentConstants.STANDARD_WIDTH, TimezoneHudOverlay.panelWidth(10));
		assertEquals(ComponentConstants.STANDARD_WIDTH, TimezoneHudOverlay.panelWidth(0));
	}

	@Test
	public void longRowsWidenThePanelInsteadOfWrapping()
	{
		// "New Zealand 00:30 NZST" is comfortably wider than the default panel, which is
		// what pushed the time onto a second line.
		int wide = ComponentConstants.STANDARD_WIDTH + 60;
		assertTrue(TimezoneHudOverlay.panelWidth(wide) > wide);
	}

	@Test
	public void everyRowKeepsAGapBetweenLabelAndTime()
	{
		// Without the gap the two columns butt up against each other at exactly the
		// width where wrapping used to start.
		for (int row = 0; row < 500; row += 7)
		{
			assertTrue("row " + row, TimezoneHudOverlay.panelWidth(row) >= row);
		}
	}

	@Test
	public void widthGrowsWithContent()
	{
		int narrow = TimezoneHudOverlay.panelWidth(200);
		int wider = TimezoneHudOverlay.panelWidth(300);
		assertEquals(100, wider - narrow);
	}
}
