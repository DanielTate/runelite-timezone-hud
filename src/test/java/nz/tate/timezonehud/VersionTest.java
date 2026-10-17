package nz.tate.timezonehud;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class VersionTest
{
	@Test
	public void sidebarShowsTheReleasedVersion() throws IOException
	{
		Properties properties = new Properties();
		try (InputStream in = new FileInputStream("runelite-plugin.properties"))
		{
			properties.load(in);
		}
		assertEquals(properties.getProperty("version"), TimezoneHudPanel.VERSION);
	}
}
