package me.devtec.shared;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devtec.shared.Ref.ServerType;
import me.devtec.shared.commands.manager.CommandsRegister;
import me.devtec.shared.commands.manager.SelectorUtils;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.DataType;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.cache.TempList;
import me.devtec.shared.events.EventManager;
import me.devtec.shared.events.api.users.UserDataLoadEvent;
import me.devtec.shared.events.api.users.UserDataUnloadEvent;
import me.devtec.shared.placeholders.PlaceholderAPI;
import me.devtec.shared.scheduler.Scheduler;
import me.devtec.shared.scheduler.Tasker;
import me.devtec.shared.utility.ColorUtils;
import me.devtec.shared.utility.LibraryLoader;
import me.devtec.shared.utility.OfflineCache;
import me.devtec.shared.utility.ParseUtils;
import me.devtec.shared.utility.TimeUtils;
import me.devtec.shared.utility.TimeUtils.TimeFormat;
import me.devtec.shared.utility.TimeUtils.TimeFormatter;
import me.devtec.shared.utility.colors.ArrowsFinder;
import me.devtec.shared.utility.colors.ArrowsWithExclamationFinder;
import me.devtec.shared.utility.colors.ClassicArrowsFinder;
import me.devtec.shared.utility.colors.ExclamationArrowsFinder;
import me.devtec.shared.utility.colors.ExclamationFinder;
import me.devtec.shared.utility.colors.RegexFinder;

public class API {

	// Commands api

	public static CommandsRegister commandsRegister;

	@SuppressWarnings("rawtypes")

	public static SelectorUtils selectorUtils;

	// Library

	public static LibraryLoader library;

	// Offline users cache

	private static OfflineCache cache;

	private static final Map<UUID, Config> users = new ConcurrentHashMap<>();

	public static final int THREAD_COUNT = Math.max(1, (int) (Runtime.getRuntime().availableProcessors() / 1.5));

	private static ExecutorService EXECUTOR;

	private static final List<Pair> savingQueue = new TempList<Pair>(600).setCallback(pair -> {

		Config cached = (Config) pair.getValue();

		UserDataUnloadEvent event = new UserDataUnloadEvent((UUID) pair.getKey(), cached);

		EventManager.call(event);

		cached.save("yaml");

	}); // 30s

	private static int savingScheduler;

	public static boolean AUTOMATICALLY_USER_SAVING_TASK;

	// Other cool things

	private static final Basics basics = new Basics();

	private static volatile boolean enabled = true;

	public static ExecutorService getExecutor() {

		if (EXECUTOR == null)
			EXECUTOR = Executors.newFixedThreadPool(API.THREAD_COUNT);

		return EXECUTOR;

	}

	public static void initOfflineCache(boolean onlineMode, Config rawData) {

		API.cache = new OfflineCache(onlineMode);

		for (String uuid : rawData.getKeys())
			try {

				API.cache.setLookup(UUID.fromString(uuid), rawData.getString(uuid));

			} catch (Exception ignored) {

			}

	}

	public static OfflineCache offlineCache() {

		return API.cache;

	}

	public static Config getUser(String playerName) {

		if (API.cache == null || playerName == null || playerName.isEmpty())
			return null;

		UUID id = API.cache.lookupId(playerName);

		return getUser(id);

	}

	public static Config getUser(UUID id) {

		if (API.cache == null || id == null)
			return null;

		Config cached = API.users.get(id);

		if (cached == null) {

			Iterator<Pair> itr = savingQueue.iterator();

			while (itr.hasNext()) {

				Pair pair = itr.next();

				if (pair.getKey().equals(id)) {

					itr.remove();

					API.users.put(id, cached = (Config) pair.getValue());

					return cached;

				}

			}

			API.users.put(id, cached = new Config("plugins/TheAPI/Users/" + id + ".yml"));

			UserDataLoadEvent event = new UserDataLoadEvent(id, cached);

			EventManager.call(event);

		}

		return cached;

	}

	public static Config removeCache(UUID id) {

		if (id == null)
			return null;

		Config file = API.users.remove(id);

		if (file != null)
			savingQueue.add(Pair.of(id, file));

		return file;

	}

	public static void setEnabled(boolean status) {

		API.enabled = status;

		if (!status) {

			if (EXECUTOR != null)
				EXECUTOR.shutdown();

			EXECUTOR = null;

			if (savingScheduler != 0)
				Scheduler.cancelTask(savingScheduler);

			savingScheduler = 0;

			// Save all players

			for (Config config : API.users.values())
				config.save("yaml");

			// Clear cache

			API.users.clear();

			// Saving queue

			while (!savingQueue.isEmpty()) {

				Pair pair = savingQueue.remove(0);

				Config cached = (Config) pair.getValue();

				UserDataUnloadEvent event = new UserDataUnloadEvent((UUID) pair.getKey(), cached);

				EventManager.call(event);

				cached.save("yaml");

			}

			// Unregister placeholders

			PlaceholderAPI.unregisterAll();

			Scheduler.cancelAll();

		} else if (AUTOMATICALLY_USER_SAVING_TASK && savingScheduler == 0)
			savingScheduler = new Tasker() {

				@Override

				public void run() {

					// Save all players

					for (Config config : API.users.values())
						config.save("yaml");

				}

			}.runRepeating(432000, 432000); // Every 6 hours

	}

	public static boolean isEnabled() {

		return API.enabled;

	}

	public static class Basics {

		final int[][] EMPTY_ARRAY = {};

		public void load() {

			String path = Ref.type().isBukkit() || Ref.type() == ServerType.BUNGEECORD
					|| Ref.type() == ServerType.VELOCITY ? "plugins/TheAPI/" : "TheAPI/";

			try (Config tags = new Config(path + "tags.yml")) {

				tags.setIfAbsent("hexTagPrefix", "!", Arrays.asList("# <hexTagPrefix><tagName>", "# For ex.: !fire"));

				tags.setIfAbsent("gradient-mode", 1,

						Arrays.asList("# Modes: 1, 2, 3, 4, REGEX", "# ", "# Mode 1: !#rrggbb[TEXT]!#rrggbb",
								"# Mode 2: <#hex>[TEXT]<#secondHex>", "# Mode 3: <!#hex>[TEXT]<!#secondHex>",

								"# Mode 4: <#hex>[TEXT]</#secondHex>",
								"# Mode REGEX: [prefix1]#rrggbb[suffix1][TEXT][prefix2]#rrggbb[suffix2] - Settings in the gradient section"));

				tags.setIfAbsent("hex-mode", 1,
						Arrays.asList("# Modes: 1, 2", "# ", "# Mode 1: &#rrggbb or #rrggbb", "# Mode 2: <#rrggbb>"));

				tags.setIfAbsent("gradient.firstHex.prefix", "!",
						Collections.singletonList("# !#rrggbb TEXT !#rrggbb"));

				tags.setIfAbsent("gradient.firstHex.suffix", "");

				tags.setIfAbsent("gradient.secondHex.prefix", "!");

				tags.setIfAbsent("gradient.secondHex.suffix", "");

				if (tags.getInt("version") == 0)
					tags.remove("tags");

				if (!tags.exists("tags")) {

					tags.setIfAbsent("tags.baby_blue", "#0fd2f6");

					tags.setIfAbsent("tags.beige", "#ffc8a9");

					tags.setIfAbsent("tags.blush", "#e69296");

					tags.setIfAbsent("tags.amaranth", "#e52b50");

					tags.setIfAbsent("tags.brown", "#964b00");

					tags.setIfAbsent("tags.crimson", "#dc143c");

					tags.setIfAbsent("tags.dandelion", "#ffc31c");

					tags.setIfAbsent("tags.eggshell", "#f0ecc7");

					tags.setIfAbsent("tags.fire", "#ff0000");

					tags.setIfAbsent("tags.ice", "#bddeec");

					tags.setIfAbsent("tags.indigo", "#726eff");

					tags.setIfAbsent("tags.lavender", "#4b0082");

					tags.setIfAbsent("tags.leaf", "#618a3d");

					tags.setIfAbsent("tags.lilac", "#c8a2c8");

					tags.setIfAbsent("tags.lime", "#b7ff00");

					tags.setIfAbsent("tags.midnight", "#007bff");

					tags.setIfAbsent("tags.mint", "#50c878");

					tags.setIfAbsent("tags.olive", "#929d40");

					tags.setIfAbsent("tags.royal_purple", "#7851a9");

					tags.setIfAbsent("tags.rust", "#b45019");

					tags.setIfAbsent("tags.sky", "#00c8ff");

					tags.setIfAbsent("tags.smoke", "#708c98");

					tags.setIfAbsent("tags.tangerine", "#ef8e38");

					tags.setIfAbsent("tags.violet", "#9c6eff");

				}

				tags.setIfAbsent("version", 1);

				tags.save(DataType.YAML);

				ColorUtils.tagPrefix = tags.getString("hexTagPrefix");

				// Unsupported mode

				if ("REGEX".equalsIgnoreCase(tags.getString("gradient-mode"))) {

					String firstPrefix = tags.getString("gradient.firstHex.prefix");

					String secondPrefix = tags.getString("gradient.secondHex.prefix");

					String firstSuffix = tags.getString("gradient.firstHex.suffix");

					String secondSuffix = tags.getString("gradient.secondHex.suffix");

					ColorUtils.gradientFinderConstructor = RegexFinder::new;

					RegexFinder.init(firstPrefix, firstSuffix, secondPrefix, secondSuffix);

				} else
					switch (tags.getInt("gradient-mode")) {

					case 1:

						ColorUtils.gradientFinderConstructor = ExclamationFinder::new;

						break;

					case 2:

						ColorUtils.gradientFinderConstructor = ArrowsFinder::new;

						break;

					case 3:

						ColorUtils.gradientFinderConstructor = ArrowsWithExclamationFinder::new;

						break;

					case 5:

						ColorUtils.gradientFinderConstructor = ExclamationArrowsFinder::new;

						break;

					default:

						ColorUtils.gradientFinderConstructor = ClassicArrowsFinder::new;

						break;

					}

				switch (tags.getInt("hex-mode")) {
				case 1:
					ColorUtils.hexReplacer = (text, start, end) -> {
						int i = Math.max(0, start);
						int limit = Math.min(end, text.length());
						while (i < limit) {
							char c = text.charAt(i);
							if (c == '&' && i + 7 < limit && text.charAt(i + 1) == '#' && validHex(text, i + 2)) {
								int added = replaceHex(text, i, 8, 2);
								limit += added;
								i += 14;
								continue;
							}
							if (c == '#' && i + 6 < limit && validHex(text, i + 1)) {
								int added = replaceHex(text, i, 7, 1);
								limit += added;
								i += 14;
								continue;
							}
							++i;
						}
					};
					break;
				case 2:
					ColorUtils.hexReplacer = (text, start, end) -> {
						int i = Math.max(0, start);
						int limit = Math.min(end, text.length());
						while (i < limit) {
							if (text.charAt(i) == '<' && i + 8 < limit && text.charAt(i + 1) == '#'
									&& text.charAt(i + 8) == '>' && validHex(text, i + 2)) {
								int added = replaceHex(text, i, 9, 2);
								limit += added;
								i += 14;
								continue;
							}
							++i;
						}
					};
					break;
				}

				for (String tag : tags.getKeys("tags"))
					ColorUtils.registerColorTag(ColorUtils.tagPrefix + tag, tags.getString("tags." + tag));

			}

			try (Config config = new Config(path + "config.yml")) {

				config.setIfAbsent("timeConvertor.settings.defaultlyDigits", false, Collections
						.singletonList("# If plugin isn't using own split, use defaulty digitals? 300 -> 5:00"));

				config.setIfAbsent("timeConvertor.settings.defaultSplit", " ",
						Collections.singletonList("# If plugin isn't using own split, api'll use this split"));

				config.setIfAbsent("timeConvertor.years.matcher", "y|years?",
						Collections.singletonList("# Pattern matcher (regex)"));

				config.setIfAbsent("timeConvertor.years.convertor", Arrays.asList("<=1  year", ">1  years"),
						Arrays.asList("# >=X value is higher or equals to X", "# <=X value is lower or equals to X",

								"# >X value is higher than X", "# <X value is lower than X", "# ==X value equals to X",
								"# !=X value doesn't equals to X"));

				config.setIfAbsent("timeConvertor.months.matcher", "mo|mon|months?");

				config.setIfAbsent("timeConvertor.months.convertor", Arrays.asList("<=1  month", ">1  months"));

				config.setIfAbsent("timeConvertor.days.matcher", "d|days?");

				config.setIfAbsent("timeConvertor.days.convertor", Arrays.asList("<=1  day", ">1  days"));

				config.setIfAbsent("timeConvertor.hours.matcher", "h|hours?");

				config.setIfAbsent("timeConvertor.hours.convertor", Arrays.asList("<=1  hour", ">1  hours"));

				config.setIfAbsent("timeConvertor.minutes.matcher", "m|mi|min|minut|minutes?");

				config.setIfAbsent("timeConvertor.minutes.convertor", Arrays.asList("<=1  minute", ">1  minutes"));

				config.setIfAbsent("timeConvertor.seconds.matcher", "s|sec|seconds?");

				config.setIfAbsent("timeConvertor.seconds.convertor", Arrays.asList("<=1  second", ">1  seconds"));

				if (config.exists("timeConvertor.weeks"))
					config.remove("timeConvertor.weeks");

				if (Ref.type().isBukkit())
					config.setIfAbsent("nmsProvider-use-directly-jar", false, Arrays.asList("",
							"# In some cases Java isn't able to compile .java file and we have to use .jar file instead"));

				config.setIfAbsent("automatically-save-user-files", true,
						Arrays.asList("", "# Save all loaded user files (in memory) every 6 hours"));

				config.setIfAbsent("default-json-handler", "TheAPI",

						Arrays.asList("", "# Default Json reader & writer for reading & writing Config files",
								"# Guava - From Google (Default)", "# TheAPI - Our own project"));

				AUTOMATICALLY_USER_SAVING_TASK = config.getBoolean("automatically-save-user-files");

				if (Ref.type().isBukkit())
					config.setIfAbsent("fallback-scoreboard-support", false,

							Arrays.asList("",
									"# Scoreboard lines will be split into 3 parts as it is on version 1.12.2 or lower,",

									"# so that players with older client (1.12.2-) can see the scoreboard as well as players with client 1.13+ (text length is limited to 48 chars)",

									"# This requires a bit more CPU usage and sends more packets as a result.",

									"# Enable this only if you have installed ViaVersion/ProtocolSupport and allows connection for 1.12.2 and older clients"));

				config.save(DataType.YAML);

				TimeUtils.timeSplit = config.getString("timeConvertor.settings.defaultSplit");

				for (TimeFormat format : TimeFormat.values())
					TimeUtils.timeConvertor.put(format, new TimeFormatter() {

						final Pattern pattern = Pattern.compile("[+-]?[ ]*[0-9]+[ ]*("
								+ config.getString("timeConvertor." + format.name().toLowerCase() + ".matcher") + ")");

						@Override

						public Matcher matcher(String text) {

							return pattern.matcher(text);

						}

						@Override

						public String toString(long value) {

							for (String action : config
									.getStringList("timeConvertor." + format.name().toLowerCase() + ".convertor"))
								if (matchAction(action, value)) {

									action = action.substring(action.indexOf(" "));

									if (action.startsWith(" "))
										action = action.substring(1);

									return value + action;

								}

							return value + format.getDefaultSuffix();

						}

					});
			}

			if (AUTOMATICALLY_USER_SAVING_TASK && savingScheduler == 0)
				savingScheduler = new Tasker() {

					@Override
					public void run() {

						// Save all players

						for (Config config : API.users.values())
							config.save("yaml");

					}

				}.runRepeating(432000, 432000); // Every 6 hours

			// Init libraries without waiting

			if (library != null) {

				File libraries = new File(path + "libraries");

				if (libraries.exists())
					for (File file : libraries.listFiles())
						library.load(file);

			}

		}

		private boolean validHex(StringContainer text, int start) {
			if (start < 0 || start + 6 > text.length())
				return false;
			for (int i = 0; i < 6; ++i)
				if (hexValue(text.charAt(start + i)) == -1)
					return false;
			return true;
		}

		private int replaceHex(StringContainer text, int start, int sourceLength, int digitOffset) {
			int oldLength = text.length();
			int added = 14 - sourceLength;
			char d0 = lower(text.charAt(start + digitOffset));
			char d1 = lower(text.charAt(start + digitOffset + 1));
			char d2 = lower(text.charAt(start + digitOffset + 2));
			char d3 = lower(text.charAt(start + digitOffset + 3));
			char d4 = lower(text.charAt(start + digitOffset + 4));
			char d5 = lower(text.charAt(start + digitOffset + 5));
			text.ensureCapacity(oldLength + added);
			char[] data = text.getValueWithoutTrim();
			System.arraycopy(data, start + sourceLength, data, start + 14, oldLength - start - sourceLength);
			data[start] = '§';
			data[start + 1] = 'x';
			data[start + 2] = '§';
			data[start + 3] = d0;
			data[start + 4] = '§';
			data[start + 5] = d1;
			data[start + 6] = '§';
			data[start + 7] = d2;
			data[start + 8] = '§';
			data[start + 9] = d3;
			data[start + 10] = '§';
			data[start + 11] = d4;
			data[start + 12] = '§';
			data[start + 13] = d5;
			text.increaseCount(added);
			return added;
		}

		private boolean matchAction(String action, long value) {

			String[] split = action.split(" ");

			if (action.startsWith("=="))
				return value == ParseUtils.getLong(split[0]);

			if (action.startsWith("!="))
				return value != ParseUtils.getLong(split[0]);

			if (action.startsWith(">="))
				return value >= ParseUtils.getLong(split[0]);

			if (action.startsWith("<="))
				return value <= ParseUtils.getLong(split[0]);

			if (action.startsWith(">"))
				return value > ParseUtils.getLong(split[0]);

			if (action.startsWith("<"))
				return value < ParseUtils.getLong(split[0]);

			return false; // invalid

		}

		public String[] getLastColors(String input) {

			StringContainer color = new StringContainer(14);

			StringContainer formats = new StringContainer(5);

			for (int i = 0; i < input.length(); i++) {

				char c = input.charAt(i);

				if (c == '§' && i + 1 < input.length()) {

					c = Character.toLowerCase(input.charAt(++i));

					switch (c) {

					case 'r':

						formats.clear();

						break;

					case 'k':

					case 'l':

					case 'm':

					case 'n':

					case 'o':

						if (formats.indexOf(c) == -1)
							formats.append(c);

						break;

					case '0':

					case '1':

					case '2':

					case '3':

					case '4':

					case '5':

					case '7':

					case '8':

					case '9':

					case 'a':

					case 'b':

					case 'c':

					case 'd':

					case 'e':

					case 'f':

						color.clear();

						formats.clear();

						color.append(c);

						break;

					case 'x':

						color.clear();

						formats.clear();

						if (i + 12 < input.length()) {

							color.append('x');

							for (int count = 0; count < 6; ++count) {

								char cn = input.charAt(++i);

								if (cn != '§') { // invalid hex

									--i;

									color.clear();

									break;

								}

								cn = Character.toLowerCase(input.charAt(++i));

								if (checkIfValidHexColor(cn, color))
									continue;

								--i;

								break;

							}

						}

						break;

					}

				} else if (c == '&' && i + 1 < input.length()) {

					c = Character.toLowerCase(input.charAt(++i));

					switch (c) {

					case 'u':

						color.clear();

						formats.clear();

						color.append('u');

						break;

					case '#':

						color.clear();

						formats.clear();

						if (i + 6 < input.length())
							i = appendHexColor(input, color, i);

						break;

					default:

						break;

					}

				} else if (c == '#' && i + 6 < input.length())
					i = appendHexColor(input, color, i);

			}

			return new String[] { color.isEmpty() ? null : color.toString(),
					formats.isEmpty() ? null : formats.toString() };

		}

		private int appendHexColor(String input, StringContainer color, int i) {

			color.append('#');

			for (int count = 0; count < 6; ++count) {

				char cn = input.charAt(++i);

				if (checkIfValidHexColor(cn, color))
					continue;

				--i;

				break;

			}

			return i;

		}

		private boolean checkIfValidHexColor(char cn, StringContainer color) {

			if (cn >= 64 && cn <= 70 || cn >= 97 && cn <= 102 || cn >= 48 && cn <= 57) {

				color.append(cn);

				return true;

			}

			// invalid hex

			color.clear();

			return false;

		}

		public String rainbow(String text, String firstHex, String secondHex, List<String> protectedStrings) {

			if (text == null)
				return null;

			return rainbow(text, 0, text.length(), firstHex, secondHex, protectedStrings);

		}

		public String rainbow(String text, int start, int end, String firstHex, String secondHex,
				List<String> protectedStrings) {

			if (text == null)
				return null;

			StringContainer container = new StringContainer(text);

			rawGradient(container, start, end, firstHex, secondHex, false, protectedStrings);

			return container.toString();

		}

		public String gradient(String text, String firstHex, String secondHex, List<String> protectedStrings) {

			if (text == null)
				return null;

			return gradient(text, 0, text.length(), firstHex, secondHex, protectedStrings);

		}

		public String gradient(String text, int start, int end, String firstHex, String secondHex,
				List<String> protectedStrings) {

			if (text == null)
				return null;

			StringContainer container = new StringContainer(text);

			rawGradient(container, start, end, firstHex, secondHex, true, protectedStrings);

			return container.toString();

		}

		public void rainbow(StringContainer container, String firstHex, String secondHex,
				List<String> protectedStrings) {

			rainbow(container, 0, container.length(), firstHex, secondHex, protectedStrings);

		}

		public void rainbow(StringContainer container, int start, int end, String firstHex, String secondHex,
				List<String> protectedStrings) {

			rawGradient(container, start, end, firstHex, secondHex, false, protectedStrings);

		}

		public void gradient(StringContainer container, String firstHex, String secondHex,
				List<String> protectedStrings) {

			gradient(container, 0, container.length(), firstHex, secondHex, protectedStrings);

		}

		public void gradient(StringContainer container, int start, int end, String firstHex, String secondHex,
				List<String> protectedStrings) {

			rawGradient(container, start, end, firstHex, secondHex, true, protectedStrings);

		}

		/**
		 * Gradient overload for fixed finders which already parsed both colors. The
		 * common path stays allocation-free. Only the uncommon fallback path
		 * materializes #RRGGBB strings for the legacy formatter-aware algorithm.
		 */
		public void gradient(StringContainer container, int start, int end, int firstRGB, int secondRGB,
				List<String> protectedStrings) {
			if (container == null || container.isEmpty())
				return;

			if (start < 0)
				start = 0;

			int length = container.length();
			if (end > length)
				end = length;

			if (start >= end || (protectedStrings == null || protectedStrings.isEmpty())
					&& gradientFast(container, start, end, firstRGB, secondRGB))
				return;

			rawGradient(container, start, end, toHexString(firstRGB), toHexString(secondRGB), true, protectedStrings);
		}

		/**
		 * Fast gradient path for finders which already parsed both HEX colors.
		 *
		 * @return true when the range was handled by the fast path, false when the
		 *         caller should use the regular gradient implementation
		 */
		public boolean gradientFast(StringContainer container, int start, int end, int firstRGB, int secondRGB) {
			if (container == null || container.isEmpty())
				return true;

			if (start < 0)
				start = 0;

			int length = container.length();
			if (end > length)
				end = length;

			if (start >= end)
				return true;

			int colored = fastGradientColorCount(container, start, end);
			if (colored < 0)
				return false;

			fastGradient(container, start, end, firstRGB, secondRGB, colored);
			return true;
		}

		private static final char[] EMPTY_CHAR_ARRAY = {};
		private static final char[] RESET_CHAR_ARRAY = { '§', 'r' };
		private static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
				'e', 'f' };

		private void rawGradient(StringContainer container, int start, int end, String firstHex, String secondHex,
				boolean defaultRainbow, List<String> protectedStrings) {
			if (container == null || container.isEmpty())
				return;
			if (start < 0)
				start = 0;
			if (end > container.length())
				end = container.length();
			if (start >= end)
				return;
			if (defaultRainbow && (protectedStrings == null || protectedStrings.isEmpty())) {
				int colored = fastGradientColorCount(container, start, end);
				if (colored >= 0) {
					fastGradient(container, start, end, firstHex, secondHex, colored);
					return;
				}
			}

			boolean inRainbow = defaultRainbow;
			char[] formats = EMPTY_CHAR_ARRAY;
			int[][] skipRegions = EMPTY_ARRAY;
			int allocated = 0;
			int currentSkipAt = -1;
			int skipId = 0;
			char[] chars = new char[14];
			int totalSize = end - start;

			if (protectedStrings != null && !protectedStrings.isEmpty()) {
				for (String protect : protectedStrings) {
					if (protect == null || protect.isEmpty())
						continue;
					int size = protect.length();
					int num = start;
					while (true) {
						int position = container.indexOf(protect, num);
						if (position == -1 || position >= end)
							break;
						num = position + size;
						if (allocated >= skipRegions.length) {
							int[][] copy = new int[Math.max(1, allocated << 1)][];
							if (allocated > 0)
								System.arraycopy(skipRegions, 0, copy, 0, allocated);
							skipRegions = copy;
						}
						totalSize -= size;
						skipRegions[allocated++] = new int[] { position, size };
					}
				}
				if (allocated > 0)
					currentSkipAt = skipRegions[0][0];
			}

			if (totalSize <= 0)
				return;

			int r = 0;
			int g = 0;
			int b = 0;
			float intervalR = 0;
			float intervalG = 0;
			float intervalB = 0;

			if (inRainbow) {
				if (firstHex == null || secondHex == null) {
					firstHex = ColorUtils.color.generateColor();
					secondHex = ColorUtils.color.generateColor();
				}
				int rgb = parseHex(firstHex);
				r = rgb >> 16 & 0xFF;
				g = rgb >> 8 & 0xFF;
				b = rgb & 0xFF;
				rgb = parseHex(secondHex);
				if (totalSize > 1) {
					intervalR = ((rgb >> 16 & 0xFF) - r) / (float) (totalSize - 1);
					intervalG = ((rgb >> 8 & 0xFF) - g) / (float) (totalSize - 1);
					intervalB = ((rgb & 0xFF) - b) / (float) (totalSize - 1);
				}
			}

			int i = start - 1;
			for (int step = 0; step < totalSize; ++step) {
				char c = container.charAt(++i);
				if (currentSkipAt == step) {
					int skipForChars = skipRegions[skipId++][1] - 1;
					currentSkipAt = skipId == allocated ? -1 : skipRegions[skipId][0];
					i += skipForChars;
					continue;
				}
				if (c == '&' && i + 1 < container.length() && container.charAt(i + 1) == 'u') {
					container.delete(i, i + 2);
					--i;
					++step;
					inRainbow = true;
					firstHex = ColorUtils.color.generateColor();
					secondHex = ColorUtils.color.generateColor();
					int rgb = parseHex(firstHex);
					r = rgb >> 16 & 0xFF;
					g = rgb >> 8 & 0xFF;
					b = rgb & 0xFF;
					rgb = parseHex(secondHex);
					if (totalSize > 1) {
						intervalR = ((rgb >> 16 & 0xFF) - r) / (float) (totalSize - 1);
						intervalG = ((rgb >> 8 & 0xFF) - g) / (float) (totalSize - 1);
						intervalB = ((rgb & 0xFF) - b) / (float) (totalSize - 1);
					} else
						intervalR = intervalG = intervalB = 0;
					continue;
				}
				if (!inRainbow)
					continue;

				switch (c) {
				case ' ':
					if (formats.length == 2 && formats[1] == 'r') {
						container.insertMultipleChars(i, formats);
						formats = EMPTY_CHAR_ARRAY;
						i += 2;
						insertHex(container, i, hexPiece(step, r, intervalR), hexPiece(step, g, intervalG),
								hexPiece(step, b, intervalB), chars);
						i += 14;
					}
					continue;
				case '§':
					if (i + 1 < container.length()) {
						c = container.charAt(++i);
						++step;
						if (isFormat(c)) {
							container.delete(i - 1, i + 1);
							i -= 2;
							if (c == 'r')
								formats = RESET_CHAR_ARRAY;
							else if (formats.length == 0)
								formats = new char[] { '§', c };
							else {
								char[] copy = new char[formats.length + 2];
								System.arraycopy(formats, 0, copy, 0, formats.length);
								formats = copy;
								formats[formats.length - 2] = '§';
								formats[formats.length - 1] = c;
							}
							break;
						}
						if (isColor(c) || c == 'x')
							inRainbow = false;
						break;
					}
				default:
					if (formats.length == 2 && formats[1] == 'r') {
						container.insertMultipleChars(i, formats);
						formats = EMPTY_CHAR_ARRAY;
						i += 2;
						insertHex(container, i, hexPiece(step, r, intervalR), hexPiece(step, g, intervalG),
								hexPiece(step, b, intervalB), chars);
						i += 14;
					} else {
						insertHex(container, i, hexPiece(step, r, intervalR), hexPiece(step, g, intervalG),
								hexPiece(step, b, intervalB), chars);
						i += 14;
						if (formats.length != 0) {
							container.insertMultipleChars(i, formats);
							i += formats.length;
						}
					}
					break;
				}
			}
		}

		public boolean gradientFastDelimited(StringContainer container, int firstMarkerStart, int contentStart,
				int contentEnd, int secondMarkerEnd, int firstRGB, int secondRGB) {

			if (container == null)
				return false;

			int oldLength = container.length();

			if (firstMarkerStart < 0 || contentStart < firstMarkerStart || contentEnd < contentStart
					|| secondMarkerEnd < contentEnd || secondMarkerEnd > oldLength)
				return false;

			int colored = fastGradientColorCount(container, contentStart, contentEnd);

			if (colored < 0)
				return false;

			int contentLength = contentEnd - contentStart;

			int firstMarkerLength = contentStart - firstMarkerStart;

			int secondMarkerLength = secondMarkerEnd - contentEnd;

			/*
			 * Every colored character gains one 14-char §x... prefix.
			 */
			long expansionLong = colored * 14L;

			int removed = firstMarkerLength + secondMarkerLength;

			long newLengthLong = oldLength + expansionLong - removed;

			if (expansionLong > Integer.MAX_VALUE || newLengthLong > Integer.MAX_VALUE)
				throw new OutOfMemoryError();

			int expansion = (int) expansionLong;
			int newLength = (int) newLengthLong;

			int delta = expansion - removed;

			/*
			 * No colored character = typically only spaces.
			 *
			 * In that case there's nothing to expand; compact content and suffix directly.
			 */
			if (colored == 0) {
				char[] data = container.getValueWithoutTrim();

				if (contentLength != 0)
					System.arraycopy(data, contentStart, data, firstMarkerStart, contentLength);

				int suffixLength = oldLength - secondMarkerEnd;

				if (suffixLength != 0)
					System.arraycopy(data, secondMarkerEnd, data, firstMarkerStart + contentLength, suffixLength);

				container.delete(newLength, oldLength);

				return true;
			}

			if (delta > 0)
				container.ensureCapacity(newLength);

			char[] data = container.getValueWithoutTrim();

			/*
			 * Final location immediately after expanded content.
			 *
			 * Output:
			 *
			 * prefix [expanded content] suffix
			 */
			int finalContentEnd = firstMarkerStart + contentLength + expansion;

			/*
			 * Move suffix ONCE.
			 *
			 * This simultaneously removes the second marker and makes room for the expanded
			 * gradient.
			 */
			int suffixLength = oldLength - secondMarkerEnd;

			if (suffixLength != 0)
				System.arraycopy(data, secondMarkerEnd, data, finalContentEnd, suffixLength);

			int firstR = firstRGB >> 16 & 0xFF;
			int firstG = firstRGB >> 8 & 0xFF;
			int firstB = firstRGB & 0xFF;

			float intervalR = 0;
			float intervalG = 0;
			float intervalB = 0;

			if (contentLength > 1) {
				float multiplier = 1.0F / (contentLength - 1);

				intervalR = ((secondRGB >> 16 & 0xFF) - firstR) * multiplier;

				intervalG = ((secondRGB >> 8 & 0xFF) - firstG) * multiplier;

				intervalB = ((secondRGB & 0xFF) - firstB) * multiplier;
			}

			/*
			 * Work backwards.
			 *
			 * Source content remains untouched until each character has been read, while
			 * destination grows to the right.
			 */
			int write = finalContentEnd;

			for (int read = contentEnd - 1; read >= contentStart; --read) {

				char c = data[read];

				data[--write] = c;

				if (c == ' ')
					continue;

				int step = read - contentStart;

				write -= 14;

				writeHex(data, write, hexPiece(step, firstR, intervalR), hexPiece(step, firstG, intervalG),
						hexPiece(step, firstB, intervalB));
			}

			/*
			 * write should now point exactly to the beginning of the first marker, which
			 * has therefore been overwritten.
			 */
			if (delta > 0)
				container.increaseCount(delta);
			else if (delta < 0)
				/*
				 * Tail delete only changes logical size here. No meaningful array shift is
				 * necessary.
				 */
				container.delete(newLength, oldLength);

			return true;
		}

		private int fastGradientColorCount(StringContainer container, int start, int end) {
			int colored = 0;
			for (int i = start; i < end; ++i) {
				char c = container.charAt(i);
				if (c == '§' || c == '&' && i + 1 < end && container.charAt(i + 1) == 'u')
					return -1;
				if (c != ' ')
					++colored;
			}
			return colored;
		}

		private void fastGradient(StringContainer container, int start, int end, String firstHex, String secondHex,
				int colored) {
			if (firstHex == null || secondHex == null) {
				firstHex = ColorUtils.color.generateColor();
				secondHex = ColorUtils.color.generateColor();
			}

			fastGradient(container, start, end, parseHex(firstHex), parseHex(secondHex), colored);
		}

		private void fastGradient(StringContainer container, int start, int end, int firstRGB, int secondRGB,
				int colored) {
			int totalSize = end - start;
			if (totalSize <= 0 || colored == 0)
				return;

			int r = firstRGB >> 16 & 0xFF;
			int g = firstRGB >> 8 & 0xFF;
			int b = firstRGB & 0xFF;

			float intervalR = 0;
			float intervalG = 0;
			float intervalB = 0;

			if (totalSize > 1) {
				float divisor = 1.0F / (totalSize - 1);
				intervalR = ((secondRGB >> 16 & 0xFF) - r) * divisor;
				intervalG = ((secondRGB >> 8 & 0xFF) - g) * divisor;
				intervalB = ((secondRGB & 0xFF) - b) * divisor;
			}

			long expansionLong = colored * 14L;
			long newLengthLong = container.length() + expansionLong;
			if (newLengthLong > Integer.MAX_VALUE)
				throw new OutOfMemoryError();

			int expansion = (int) expansionLong;
			int oldLength = container.length();

			container.ensureCapacity((int) newLengthLong);

			char[] data = container.getValueWithoutTrim();
			System.arraycopy(data, end, data, end + expansion, oldLength - end);

			int write = end + expansion;

			for (int i = end - 1; i >= start; --i) {
				char c = data[i];
				data[--write] = c;

				if (c == ' ')
					continue;

				int step = i - start;
				write -= 14;

				writeHex(data, write, hexPiece(step, r, intervalR), hexPiece(step, g, intervalG),
						hexPiece(step, b, intervalB));
			}

			container.increaseCount(expansion);
		}

		private String toHexString(int rgb) {
			char[] out = new char[7];
			out[0] = '#';
			out[1] = HEX_DIGITS[rgb >>> 20 & 0xF];
			out[2] = HEX_DIGITS[rgb >>> 16 & 0xF];
			out[3] = HEX_DIGITS[rgb >>> 12 & 0xF];
			out[4] = HEX_DIGITS[rgb >>> 8 & 0xF];
			out[5] = HEX_DIGITS[rgb >>> 4 & 0xF];
			out[6] = HEX_DIGITS[rgb & 0xF];
			return new String(out);
		}

		private int parseHex(String value) {
			if (value == null)
				return 0;
			int length = value.length();
			if (length == 14 && value.charAt(0) == '§' && lower(value.charAt(1)) == 'x') {
				int result = 0;
				for (int i = 3; i < 14; i += 2) {
					int hex = hexValue(value.charAt(i));
					if (hex == -1)
						return 0;
					result = result << 4 | hex;
				}
				return result;
			}
			int start = 0;
			if (length > 0 && value.charAt(0) == '#')
				start = 1;
			else if (length > 1 && value.charAt(0) == '0' && (value.charAt(1) == 'x' || value.charAt(1) == 'X'))
				start = 2;
			if (start + 6 > length)
				return 0;
			int result = 0;
			for (int i = 0; i < 6; ++i) {
				int hex = hexValue(value.charAt(start + i));
				if (hex == -1)
					return 0;
				result = result << 4 | hex;
			}
			return result;
		}

		private int hexValue(char c) {
			if (c >= '0' && c <= '9')
				return c - '0';
			if (c >= 'a' && c <= 'f')
				return c - 'a' + 10;
			if (c >= 'A' && c <= 'F')
				return c - 'A' + 10;
			return -1;
		}

		private char lower(char c) {
			return c >= 'A' && c <= 'Z' ? (char) (c + 32) : c;
		}

		private int hexPiece(int step, int channelStart, float interval) {
			int value = Math.round(interval * step + channelStart);
			if (value < 0)
				return 0;
			return value > 255 ? 255 : value;
		}

		private boolean isColor(int charAt) {
			return charAt >= 97 && charAt <= 102 || charAt >= 65 && charAt <= 70 || charAt >= 48 && charAt <= 57;
		}

		private boolean isFormat(int charAt) {
			return charAt >= 107 && charAt <= 111 || charAt == 114;
		}

		private void insertHex(StringContainer builder, int pos, int r, int g, int b, char[] chars) {
			writeHex(chars, 0, r, g, b);
			builder.insertMultipleChars(pos, chars);
		}

		private void writeHex(char[] chars, int pos, int r, int g, int b) {
			chars[pos] = '§';
			chars[pos + 1] = 'x';
			chars[pos + 2] = '§';
			chars[pos + 3] = HEX_DIGITS[r >>> 4];
			chars[pos + 4] = '§';
			chars[pos + 5] = HEX_DIGITS[r & 0xF];
			chars[pos + 6] = '§';
			chars[pos + 7] = HEX_DIGITS[g >>> 4];
			chars[pos + 8] = '§';
			chars[pos + 9] = HEX_DIGITS[g & 0xF];
			chars[pos + 10] = '§';
			chars[pos + 11] = HEX_DIGITS[b >>> 4];
			chars[pos + 12] = '§';
			chars[pos + 13] = HEX_DIGITS[b & 0xF];
		}

	}

	public static Basics basics() {

		return API.basics;

	}

}
