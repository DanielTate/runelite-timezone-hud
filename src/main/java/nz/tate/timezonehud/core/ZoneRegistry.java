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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns what a user typed ("EST", "new york", "Pacific/Auckland") into a real IANA zone.
 *
 * <p>We ship our own table rather than using {@link ZoneId#SHORT_IDS}. That JDK map is
 * legacy junk: it maps EST to a <em>fixed</em> -05:00 offset with no daylight saving, and
 * maps BST to Asia/Dhaka. D1 requires abbreviations to mean DST-aware zones, so EST must
 * mean America/New_York and render "EDT" for most of the year.
 *
 * <p>The table is built once into static maps. Lookups are user-driven and rare, but the
 * side panel search may hit them in a loop, so nothing here scans a list.
 */
public final class ZoneRegistry
{
	private static final Map<String, ZoneEntry> ALIASES = buildAliases();

	/** IANA id to its curated entry, so a stored zone id gets its hand-written label back. */
	private static final Map<String, ZoneEntry> BY_ZONE_ID = buildByZoneId();

	/** Lower-cased IANA id to its canonical spelling, so "pacific/auckland" resolves. */
	private static final Map<String, String> IANA_IDS = buildIanaIds();

	private static final int MAX_TOKEN_WORDS = longestTokenWordCount();

	private ZoneRegistry()
	{
	}

	/**
	 * Look a token up. Never throws and never guesses.
	 */
	public static ZoneLookup lookup(String token)
	{
		if (token == null)
		{
			return ZoneLookup.unknown(null);
		}

		String trimmed = token.trim();
		if (trimmed.isEmpty())
		{
			return ZoneLookup.unknown(token);
		}

		// Our curated table wins over the raw IANA ids so that we control the display name.
		ZoneEntry alias = ALIASES.get(normalise(trimmed));
		if (alias != null)
		{
			return ZoneLookup.resolved(trimmed, alias);
		}

		String canonicalId = IANA_IDS.get(trimmed.toLowerCase());
		if (canonicalId != null)
		{
			return ZoneLookup.resolved(trimmed, entryFor(ZoneId.of(canonicalId)));
		}

		return ZoneLookup.unknown(trimmed);
	}

	/**
	 * The entry for a zone we already hold, e.g. one read back out of the saved clock list.
	 * Falls back to a city-derived label for the zones outside our curated table.
	 */
	public static ZoneEntry entryFor(ZoneId zone)
	{
		ZoneEntry curated = BY_ZONE_ID.get(zone.getId());
		return curated != null ? curated : new ZoneEntry(zone, displayNameForIanaId(zone.getId()));
	}

	/**
	 * True if the token means something to us, whether or not it resolves cleanly. The
	 * command parser uses this to decide how many words of "new york city 8pm" are the zone.
	 */
	public static boolean isKnownToken(String token)
	{
		return !lookup(token).isUnknown();
	}

	/**
	 * The word count of our longest multi-word alias, e.g. "los angeles" is two. The parser
	 * needs this to bound its longest-match search.
	 */
	public static int maxTokenWords()
	{
		return MAX_TOKEN_WORDS;
	}

	/**
	 * Lower-cases and squashes the separators users vary on, so "New_York", "new york" and
	 * "NEW  YORK" all hit the same key.
	 */
	static String normalise(String token)
	{
		StringBuilder builder = new StringBuilder(token.length());
		boolean lastWasSpace = true;

		for (int i = 0; i < token.length(); i++)
		{
			char c = token.charAt(i);
			if (c == '_' || c == '-' || Character.isWhitespace(c))
			{
				if (!lastWasSpace)
				{
					builder.append(' ');
					lastWasSpace = true;
				}
			}
			else
			{
				builder.append(Character.toLowerCase(c));
				lastWasSpace = false;
			}
		}

		// A trailing separator leaves one space behind.
		if (builder.length() > 0 && builder.charAt(builder.length() - 1) == ' ')
		{
			builder.setLength(builder.length() - 1);
		}
		return builder.toString();
	}

	/**
	 * Fallback label for a zone the user typed as a raw IANA id. "Pacific/Auckland" has a
	 * curated name; "America/Argentina/Ushuaia" does not, so we use the last path segment.
	 */
	private static String displayNameForIanaId(String zoneId)
	{
		int slash = zoneId.lastIndexOf('/');
		String city = slash < 0 ? zoneId : zoneId.substring(slash + 1);
		return city.replace('_', ' ');
	}

	private static Map<String, ZoneEntry> buildByZoneId()
	{
		Map<String, ZoneEntry> byId = new HashMap<>();
		for (ZoneEntry entry : ALIASES.values())
		{
			byId.put(entry.getZoneId(), entry);
		}
		return Collections.unmodifiableMap(byId);
	}

	private static Map<String, String> buildIanaIds()
	{
		Set<String> available = ZoneId.getAvailableZoneIds();
		Map<String, String> ids = new HashMap<>(available.size() * 2);
		for (String id : available)
		{
			ids.put(id.toLowerCase(), id);
		}
		return Collections.unmodifiableMap(ids);
	}

	private static int longestTokenWordCount()
	{
		int longest = 1;
		for (String key : ALIASES.keySet())
		{
			int words = 1;
			for (int i = 0; i < key.length(); i++)
			{
				if (key.charAt(i) == ' ')
				{
					words++;
				}
			}
			if (words > longest)
			{
				longest = words;
			}
		}
		return longest;
	}

	/**
	 * Adds one zone under several spellings. Sorted map so the table is easy to eyeball in
	 * a debugger; the cost is irrelevant at this size.
	 */
	private static void put(Map<String, ZoneEntry> table, String zoneId, String displayName, String... tokens)
	{
		ZoneEntry entry = new ZoneEntry(zoneId, displayName);
		for (String token : tokens)
		{
			table.put(normalise(token), entry);
		}
	}

	private static Map<String, ZoneEntry> buildAliases()
	{
		Map<String, ZoneEntry> table = new TreeMap<>();

		// -- North America ------------------------------------------------------------
		put(table, "America/New_York", "US Eastern", "est", "edt", "et", "new york", "nyc", "boston", "miami", "atlanta");
		put(table, "America/Chicago", "US Central", "cdt", "ct", "chicago", "dallas", "houston");
		put(table, "America/Denver", "US Mountain", "mst", "mdt", "mt", "denver", "salt lake city");
		put(table, "America/Los_Angeles", "US Pacific", "pst", "pdt", "pt", "los angeles", "la", "san francisco", "seattle");
		put(table, "America/Phoenix", "US Arizona", "arizona", "phoenix");
		put(table, "America/Anchorage", "US Alaska", "akst", "akdt", "alaska", "anchorage");
		put(table, "Pacific/Honolulu", "US Hawaii", "hst", "hawaii", "honolulu");
		put(table, "America/Toronto", "Canada Eastern", "toronto", "ottawa", "montreal");
		put(table, "America/Halifax", "Atlantic Canada", "adt", "halifax");
		put(table, "America/Mexico_City", "Mexico", "mexico", "mexico city");
		put(table, "America/Sao_Paulo", "Brazil", "brt", "brazil", "sao paulo");

		// -- Europe / Africa ----------------------------------------------------------
		put(table, "Europe/London", "UK", "gmt", "uk", "london", "britain", "england", "edinburgh", "cardiff", "belfast");
		put(table, "Europe/Dublin", "Ireland", "ireland", "dublin");
		put(table, "Europe/Lisbon", "Portugal", "wet", "portugal", "lisbon");
		put(table, "Europe/Paris", "Central Europe", "cet", "cest", "paris", "france");
		put(table, "Europe/Berlin", "Central Europe", "berlin", "germany", "hamburg", "munich");
		put(table, "Europe/Amsterdam", "Central Europe", "amsterdam", "netherlands");
		put(table, "Europe/Madrid", "Central Europe", "madrid", "spain");
		put(table, "Europe/Rome", "Central Europe", "rome", "italy");
		put(table, "Europe/Stockholm", "Central Europe", "stockholm", "sweden");
		put(table, "Europe/Oslo", "Central Europe", "oslo", "norway");
		put(table, "Europe/Warsaw", "Central Europe", "warsaw", "poland");
		put(table, "Europe/Helsinki", "Eastern Europe", "eet", "eest", "helsinki", "finland");
		put(table, "Europe/Athens", "Eastern Europe", "athens", "greece");
		put(table, "Europe/Moscow", "Moscow", "msk", "moscow", "russia");
		put(table, "Africa/Johannesburg", "South Africa", "sast", "johannesburg", "south africa", "cape town");
		put(table, "Africa/Lagos", "West Africa", "wat", "lagos", "nigeria");
		put(table, "Africa/Cairo", "Egypt", "cairo", "egypt");

		// -- Asia / Middle East -------------------------------------------------------
		put(table, "Asia/Kolkata", "India", "india", "kolkata", "calcutta", "mumbai", "delhi", "new delhi", "bangalore");
		put(table, "Asia/Jerusalem", "Israel", "idt", "israel", "jerusalem", "tel aviv");
		put(table, "Asia/Dubai", "Gulf", "gst", "dubai", "uae", "abu dhabi");
		put(table, "Asia/Riyadh", "Arabia", "riyadh", "saudi arabia");
		put(table, "Asia/Karachi", "Pakistan", "pkt", "karachi", "pakistan", "lahore");
		put(table, "Asia/Dhaka", "Bangladesh", "bdt", "dhaka", "bangladesh");
		put(table, "Asia/Bangkok", "Thailand", "ict", "bangkok", "thailand");
		put(table, "Asia/Shanghai", "China", "china", "shanghai", "beijing");
		put(table, "Asia/Hong_Kong", "Hong Kong", "hkt", "hong kong");
		put(table, "Asia/Singapore", "Singapore", "sgt", "singapore");
		put(table, "Asia/Tokyo", "Japan", "jst", "japan", "tokyo", "osaka");
		put(table, "Asia/Seoul", "Korea", "kst", "korea", "seoul", "south korea");
		put(table, "Asia/Manila", "Philippines", "pht", "manila", "philippines");
		put(table, "Asia/Jakarta", "Indonesia", "wib", "jakarta", "indonesia");

		// -- Oceania ------------------------------------------------------------------
		put(table, "Pacific/Auckland", "New Zealand", "nzst", "nzdt", "nz", "new zealand", "auckland", "wellington", "christchurch");
		put(table, "Pacific/Chatham", "Chatham Islands", "chast", "chadt", "chatham");
		put(table, "Australia/Sydney", "Australia Eastern", "aest", "aedt", "aet", "sydney", "melbourne", "canberra", "hobart");
		put(table, "Australia/Brisbane", "Australia Eastern (QLD)", "brisbane", "queensland");
		put(table, "Australia/Adelaide", "Australia Central", "acdt", "adelaide");
		put(table, "Australia/Darwin", "Australia Central (NT)", "darwin", "northern territory");
		put(table, "Australia/Perth", "Australia Western", "awst", "perth", "western australia");
		put(table, "Pacific/Fiji", "Fiji", "fjt", "fiji", "suva");

		// -- Offsets ------------------------------------------------------------------
		put(table, "UTC", "UTC", "utc", "z", "zulu", "jagex", "game time");

		return Collections.unmodifiableMap(table);
	}
}
