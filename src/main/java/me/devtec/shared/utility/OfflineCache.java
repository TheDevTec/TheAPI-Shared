package me.devtec.shared.utility;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.json.Json;

public class OfflineCache {

	private static final String USER_FORMAT = "https://api.ashcon.app/mojang/v2/user/%s";

	protected final Map<String, Query> values = new ConcurrentHashMap<>();

	private final boolean onlineMode;

	public OfflineCache(boolean onlineMode) {
		this.onlineMode = onlineMode;
	}

	public Collection<Query> getQueries() {
		return Collections.unmodifiableCollection(values.values());
	}

	/**
	 * Searches cached players similarly to TAB completion.
	 *
	 * Case-insensitive and matches anywhere in the player name.
	 *
	 * Safe to call asynchronously. The returned List is an independent
	 * snapshot and is not backed by the ConcurrentHashMap.
	 */
	public List<Query> lookupQueries(String name) {
		if (values.isEmpty())
			return Collections.emptyList();

		if (name == null)
			name = "";

		List<Query> result = new ArrayList<>();

		if (name.isEmpty()) {
			result.addAll(values.values());
			return result;
		}

		for (Query query : values.values()) {
			if (query == null)
				continue;

			String queryName = query.name;

			if (queryName != null && containsIgnoreCase(queryName, name))
				result.add(query);
		}

		return result.isEmpty()
				? Collections.<Query>emptyList()
						: result;
	}

	/*
	 * Player names are ASCII, so don't allocate:
	 *
	 * text.toLowerCase().contains(search.toLowerCase())
	 *
	 * on every lookup.
	 */
	private static boolean containsIgnoreCase(String text, String search) {
		int searchLength = search.length();

		if (searchLength == 0)
			return true;

		int textLength = text.length();

		if (searchLength > textLength)
			return false;

		char first = lowerAscii(search.charAt(0));
		int max = textLength - searchLength;

		for (int i = 0; i <= max; ++i) {
			if (lowerAscii(text.charAt(i)) != first)
				continue;

			int x = 1;

			while (x < searchLength
					&& lowerAscii(text.charAt(i + x))
					== lowerAscii(search.charAt(x)))
				++x;

			if (x == searchLength)
				return true;
		}

		return false;
	}

	private static char lowerAscii(char c) {
		return c >= 'A' && c <= 'Z'
				? (char) (c + 32)
						: c;
	}

	public UUID lookupId(String name) {
		Query o = values.get(name.toLowerCase());

		if (o == null) {
			UUID uuid = onlineMode
					? lookupIdFromMojang(name)
							: UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());

			values.put(name.toLowerCase(), new Query(name, uuid));

			return uuid;
		}

		return o.uuid;
	}

	@SuppressWarnings("unchecked")
	public String lookupNameFromMojang(String name) {
		try {
			return (String) ((Map<String, Object>) Json.reader()
					.simpleRead(StreamUtils.fromStream(
							new URL(String.format(USER_FORMAT, name))
							.openStream())))
					.get("username");
		} catch (Exception ignored) {
		}

		return name;
	}

	@SuppressWarnings("unchecked")
	public UUID lookupIdFromMojang(String name) {
		try {
			return UUID.fromString(
					(String) ((Map<String, Object>) Json.reader()
							.simpleRead(StreamUtils.fromStream(
									new URL(String.format(USER_FORMAT, name))
									.openStream())))
					.get("uuid"));
		} catch (Exception ignored) {
		}

		return UUID.nameUUIDFromBytes(
				("OfflinePlayer:" + name).getBytes());
	}

	public String lookupNameById(UUID id) {
		for (Query query : values.values())
			if (id.equals(query.uuid))
				return query.name;

		return null;
	}

	public Query lookupQuery(String name) {
		return values.get(name.toLowerCase());
	}

	public Query lookupQuery(UUID id) {
		for (Query query : values.values())
			if (id.equals(query.uuid))
				return query;

		return null;
	}

	public String lookupName(String name) {
		Query get = values.get(name.toLowerCase());

		String result = name;

		if (get == null) {
			UUID uuid = onlineMode
					? lookupIdFromMojang(name)
							: UUID.nameUUIDFromBytes(
									("OfflinePlayer:" + name).getBytes());

			values.put(
					result.toLowerCase(),
					new Query(result, uuid));
		} else
			result = get.name;

		return result;
	}

	public void setLookup(UUID uuid, String name) {
		if (uuid == null || name == null)
			return;

		Query get = values.get(name.toLowerCase());

		if (get == null || get.uuid == null) {
			values.put(
					name.toLowerCase(),
					new Query(name, uuid));

			return;
		}

		if (!get.uuid.equals(uuid)
				|| !get.name.equals(name)) {

			get.name = name;
			get.uuid = uuid;
		}
	}

	public Config saveToConfig() {
		Config data = new Config();

		for (Query query : values.values())
			data.set(
					query.uuid.toString(),
					query.name);

		return data;
	}

	public static class Query {

		/*
		 * Query objects can be updated after they've already been
		 * inserted into ConcurrentHashMap.
		 *
		 * volatile guarantees visibility for async readers.
		 */
		public volatile String name;
		public volatile UUID uuid;

		public Query(String name, UUID uuid) {
			this.name = name;
			this.uuid = uuid;
		}

		public String getName() {
			return name;
		}

		public UUID getUUID() {
			return uuid;
		}

		public void setName(String name) {
			this.name = name;
		}

		public void setUUID(UUID uuid) {
			this.uuid = uuid;
		}
	}
}