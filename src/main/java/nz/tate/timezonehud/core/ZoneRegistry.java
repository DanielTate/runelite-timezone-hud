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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
	/** IANA id to a token the user can actually type for it. Filled in by buildAliases(). */
	private static final Map<String, String> EXAMPLE_TOKENS = new HashMap<>();

	private static final Map<String, ZoneEntry> ALIASES = buildAliases();

	/** IANA id to its curated entry, so a stored zone id gets its hand-written label back. */
	private static final Map<String, ZoneEntry> BY_ZONE_ID = buildByZoneId();

	/** Tokens that mean more than one place. D6 refuses these rather than picking one. */
	private static final Map<String, List<ZoneEntry>> CONTESTED = buildContested();

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

		String key = normalise(trimmed);

		// Contested first: "CST" must never quietly become one of its three meanings.
		List<ZoneEntry> candidates = CONTESTED.get(key);
		if (candidates != null)
		{
			return ZoneLookup.ambiguous(trimmed, candidates);
		}

		// Our curated table wins over the raw IANA ids so that we control the display name.
		ZoneEntry alias = ALIASES.get(key);
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
	 * command parser uses this to decide how many words of "new york 8pm" are the zone.
	 */
	public static boolean isKnownToken(String token)
	{
		return !lookup(token).isUnknown();
	}

	/**
	 * A token the user can type to reach this zone unambiguously, for D6's "did you mean".
	 * Display names such as "US Eastern" are not themselves valid input, so an ambiguity
	 * message has to offer something else.
	 */
	public static String exampleToken(ZoneEntry entry)
	{
		String example = EXAMPLE_TOKENS.get(entry.getZoneId());
		return example != null ? example : entry.getZoneId();
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
	 * Adds one zone under several spellings. The <em>first</em> token is the one D6's "did
	 * you mean" offers back to the user, so it is always a name rather than an abbreviation.
	 * Sorted map so the table is easy to eyeball in a debugger; the cost is irrelevant here.
	 */
	private static void put(Map<String, ZoneEntry> table, String zoneId, String displayName, String... tokens)
	{
		ZoneEntry entry = new ZoneEntry(zoneId, displayName);
		for (String token : tokens)
		{
			table.put(normalise(token), entry);
		}
		EXAMPLE_TOKENS.put(zoneId, tokens[0]);
	}

	/**
	 * Records a token that means more than one place. D6 refuses to guess between them,
	 * because a wrong guess produces a plausible-looking time in the wrong country.
	 */
	private static void contest(Map<String, List<ZoneEntry>> table, String token, String... zoneIds)
	{
		List<ZoneEntry> candidates = new ArrayList<>(zoneIds.length);
		for (String zoneId : zoneIds)
		{
			ZoneEntry entry = BY_ZONE_ID.get(zoneId);
			if (entry == null)
			{
				throw new IllegalStateException("contested zone " + zoneId + " is missing from the alias table");
			}
			candidates.add(entry);
		}
		table.put(normalise(token), Collections.unmodifiableList(candidates));
	}

	private static Map<String, List<ZoneEntry>> buildContested()
	{
		Map<String, List<ZoneEntry>> table = new TreeMap<>();

		contest(table, "IST", "Asia/Kolkata", "Europe/Dublin", "Asia/Jerusalem");
		contest(table, "CST", "America/Chicago", "Asia/Shanghai", "America/Havana");
		contest(table, "BST", "Europe/London", "Asia/Dhaka");
		contest(table, "AST", "America/Halifax", "Asia/Riyadh");
		contest(table, "ACST", "Australia/Adelaide", "Australia/Darwin");
		contest(table, "AMT", "America/Manaus", "Asia/Yerevan");

		return Collections.unmodifiableMap(table);
	}

	private static Map<String, ZoneEntry> buildAliases()
	{
		Map<String, ZoneEntry> table = new TreeMap<>();

		// -- North America ------------------------------------------------------------
		put(table, "America/New_York", "US Eastern", "new york", "est", "edt", "et", "nyc", "boston", "miami", "atlanta");
		put(table, "America/Chicago", "US Central", "chicago", "cdt", "ct", "dallas", "houston");
		put(table, "America/Denver", "US Mountain", "denver", "mst", "mdt", "mt", "salt lake city");
		put(table, "America/Los_Angeles", "US Pacific", "los angeles", "pst", "pdt", "pt", "la", "san francisco", "seattle");
		put(table, "America/Phoenix", "US Arizona", "arizona", "phoenix");
		put(table, "America/Anchorage", "US Alaska", "alaska", "akst", "akdt", "anchorage");
		put(table, "Pacific/Honolulu", "US Hawaii", "hawaii", "hst", "honolulu");
		put(table, "America/Toronto", "Canada Eastern", "toronto", "ottawa", "montreal");
		put(table, "America/Halifax", "Atlantic Canada", "halifax", "adt");
		put(table, "America/Havana", "Cuba", "havana", "cuba");
		put(table, "America/Mexico_City", "Mexico", "mexico city", "mexico");
		put(table, "America/Sao_Paulo", "Brazil", "sao paulo", "brt", "brazil");
		put(table, "America/Manaus", "Amazon", "manaus", "amazon");

		// -- Europe / Africa ----------------------------------------------------------
		put(table, "Europe/London", "UK", "london", "gmt", "uk", "britain", "england", "edinburgh", "cardiff", "belfast");
		put(table, "Europe/Dublin", "Ireland", "dublin", "ireland");
		put(table, "Europe/Lisbon", "Portugal", "lisbon", "wet", "portugal");
		put(table, "Europe/Paris", "Central Europe", "paris", "cet", "cest", "france");
		put(table, "Europe/Berlin", "Central Europe", "berlin", "germany", "hamburg", "munich");
		put(table, "Europe/Amsterdam", "Central Europe", "amsterdam", "netherlands");
		put(table, "Europe/Madrid", "Central Europe", "madrid", "spain");
		put(table, "Europe/Rome", "Central Europe", "rome", "italy");
		put(table, "Europe/Stockholm", "Central Europe", "stockholm", "sweden");
		put(table, "Europe/Oslo", "Central Europe", "oslo", "norway");
		put(table, "Europe/Warsaw", "Central Europe", "warsaw", "poland");
		put(table, "Europe/Helsinki", "Eastern Europe", "helsinki", "eet", "eest", "finland");
		put(table, "Europe/Athens", "Eastern Europe", "athens", "greece");
		put(table, "Europe/Moscow", "Moscow", "moscow", "msk", "russia");
		put(table, "Africa/Johannesburg", "South Africa", "johannesburg", "sast", "south africa", "cape town");
		put(table, "Africa/Lagos", "West Africa", "lagos", "wat", "nigeria");
		put(table, "Africa/Cairo", "Egypt", "cairo", "egypt");

		// -- Asia / Middle East -------------------------------------------------------
		put(table, "Asia/Kolkata", "India", "kolkata", "india", "calcutta", "mumbai", "delhi", "new delhi", "bangalore");
		put(table, "Asia/Jerusalem", "Israel", "jerusalem", "idt", "israel", "tel aviv");
		put(table, "Asia/Dubai", "Gulf", "dubai", "gst", "uae", "abu dhabi");
		put(table, "Asia/Riyadh", "Arabia", "riyadh", "saudi arabia");
		put(table, "Asia/Yerevan", "Armenia", "yerevan", "armenia");
		put(table, "Asia/Karachi", "Pakistan", "karachi", "pkt", "pakistan", "lahore");
		put(table, "Asia/Dhaka", "Bangladesh", "dhaka", "bdt", "bangladesh");
		put(table, "Asia/Bangkok", "Thailand", "bangkok", "ict", "thailand");
		put(table, "Asia/Shanghai", "China", "shanghai", "china", "beijing");
		put(table, "Asia/Hong_Kong", "Hong Kong", "hong kong", "hkt");
		put(table, "Asia/Singapore", "Singapore", "singapore", "sgt");
		put(table, "Asia/Tokyo", "Japan", "tokyo", "jst", "japan", "osaka");
		put(table, "Asia/Seoul", "Korea", "seoul", "kst", "korea", "south korea");
		put(table, "Asia/Manila", "Philippines", "manila", "pht", "philippines");
		put(table, "Asia/Jakarta", "Indonesia", "jakarta", "wib", "indonesia");

		// -- Oceania ------------------------------------------------------------------
		put(table, "Pacific/Auckland", "New Zealand", "new zealand", "nzst", "nzdt", "nz", "auckland", "wellington", "christchurch");
		put(table, "Pacific/Chatham", "Chatham Islands", "chatham", "chast", "chadt");
		put(table, "Australia/Sydney", "Australia Eastern", "sydney", "aest", "aedt", "aet", "melbourne", "canberra", "hobart");
		put(table, "Australia/Brisbane", "Australia Eastern (QLD)", "brisbane", "queensland");
		put(table, "Australia/Adelaide", "Australia Central", "adelaide", "acdt");
		put(table, "Australia/Darwin", "Australia Central (NT)", "darwin", "northern territory");
		put(table, "Australia/Perth", "Australia Western", "perth", "awst", "western australia");
		put(table, "Pacific/Fiji", "Fiji", "fiji", "fjt", "suva");

		// -- Offsets ------------------------------------------------------------------
		put(table, "UTC", "UTC", "utc", "z", "zulu", "jagex", "game time");

		return Collections.unmodifiableMap(table);
	}
}
