package me.devtec.shared;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
	private static Map<UUID, Config> users = new ConcurrentHashMap<>();
	private static List<Pair> savingQueue = new TempList<Pair>(600).setCallback(pair -> {
		Config cached = (Config) pair.getValue();
		UserDataUnloadEvent event = new UserDataUnloadEvent((UUID) pair.getKey(), cached);
		EventManager.call(event);
		cached.save("yaml");
	}); // 30s
	private static int savingScheduler;
	public static boolean AUTOMATICALLY_USER_SAVING_TASK;

	// Other cool things
	private static final Basics basics = new Basics();
	private static boolean enabled = true;

	public static void initOfflineCache(boolean onlineMode, Config rawData) {
		API.cache = new OfflineCache(onlineMode);
		for (String uuid : rawData.getKeys())
			try {
				API.cache.setLookup(UUID.fromString(uuid), rawData.getString(uuid));
			} catch (Exception err) {
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

		int[][] EMPTY_ARRAY = {};

		public void load() {
			String path = Ref.serverType().isBukkit() || Ref.serverType() == ServerType.BUNGEECORD || Ref.serverType() == ServerType.VELOCITY ? "plugins/TheAPI/" : "TheAPI/";

			Config tags = new Config(path + "tags.yml");
			tags.setIfAbsent("hexTagPrefix", "!", Arrays.asList("# <hexTagPrefix><tagName>", "# For ex.: !fire"));
			tags.setIfAbsent("gradient-mode", 1,
					Arrays.asList("# Modes: 1, 2, 3, 4, REGEX", "# ", "# Mode 1: !#rrggbb[TEXT]!#rrggbb", "# Mode 2: <#hex>[TEXT]<#secondHex>", "# Mode 3: <!#hex>[TEXT]<!#secondHex>",
							"# Mode 4: <#hex>[TEXT]</#secondHex>", "# Mode REGEX: [prefix1]#rrggbb[suffix1][TEXT][prefix2]#rrggbb[suffix2] - Settings in the gradient section"));
			tags.setIfAbsent("hex-mode", 1, Arrays.asList("# Modes: 1, 2", "# ", "# Mode 1: &#rrggbb or #rrggbb", "# Mode 2: <#rrggbb>"));
			tags.setIfAbsent("gradient.firstHex.prefix", "!", Arrays.asList("# !#rrggbb TEXT !#rrggbb"));
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
			if (tags.getString("gradient-mode").equalsIgnoreCase("REGEX")) {
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
				case 4:
					ColorUtils.gradientFinderConstructor = ClassicArrowsFinder::new;
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
					charLoop: for (int i = 0; i < text.length(); ++i) {
						char c = text.charAt(i);
						if (c == '&' && i + 7 < text.length() && text.charAt(i + 1) == '#') {
							for (int ic = 2; ic < 8; ++ic) {
								char cn = text.charAt(i + ic);
								if (!(cn >= 64 && cn <= 70 || cn >= 97 && cn <= 102 || cn >= 48 && cn <= 57))
									continue charLoop;
							}
							text.setCharAt(i, '§');
							text.setCharAt(++i, 'x');
							for (int run = 0; run < 6; ++run) {
								text.insert(++i, '§');
								++i;
								text.setCharAt(i, Character.toLowerCase(text.charAt(i)));
							}
						} else if (c == '#' && i + 6 < text.length()) {
							for (int ic = 1; ic < 7; ++ic) {
								char cn = text.charAt(i + ic);
								if (!(cn >= 64 && cn <= 70 || cn >= 97 && cn <= 102 || cn >= 48 && cn <= 57))
									continue charLoop;
							}
							text.setCharAt(i, '§');
							text.insert(++i, 'x');
							for (int run = 0; run < 6; ++run) {
								text.insert(++i, '§');
								++i;
								text.setCharAt(i, Character.toLowerCase(text.charAt(i)));
							}
						}
					}
				};
				break;
			case 2:
				ColorUtils.hexReplacer = (text, start, end) -> {
					charLoop: for (int i = 0; i < text.length(); ++i) {
						char c = text.charAt(i);
						if (c == '<' && i + 8 < text.length() && text.charAt(i + 1) == '#') {
							for (int ic = 2; ic < 8; ++ic) {
								char cn = text.charAt(i + ic);
								if (!(cn >= 64 && cn <= 70 || cn >= 97 && cn <= 102 || cn >= 48 && cn <= 57))
									continue charLoop;
							}
							if (text.charAt(i + 8) == '>') {
								text.deleteCharAt(i + 8);
								text.setCharAt(i, '§');
								text.setCharAt(++i, 'x');
								for (int run = 0; run < 6; ++run) {
									text.insert(++i, '§');
									++i;
									text.setCharAt(i, Character.toLowerCase(text.charAt(i)));
								}
							}
						}
					}
				};
				break;
			}

			for (String tag : tags.getKeys("tags"))
				ColorUtils.registerColorTag(ColorUtils.tagPrefix + tag, tags.getString("tags." + tag));
			Config config = new Config(path + "config.yml");
			config.setIfAbsent("timeConvertor.settings.defaultlyDigits", false, Arrays.asList("# If plugin isn't using own split, use defaulty digitals? 300 -> 5:00"));
			config.setIfAbsent("timeConvertor.settings.defaultSplit", " ", Arrays.asList("# If plugin isn't using own split, api'll use this split"));
			config.setIfAbsent("timeConvertor.years.matcher", "y|years?", Arrays.asList("# Pattern matcher (regex)"));
			config.setIfAbsent("timeConvertor.years.convertor", Arrays.asList("<=1  year", ">1  years"), Arrays.asList("# >=X value is higher or equals to X", "# <=X value is lower or equals to X",
					"# >X value is higher than X", "# <X value is lower than X", "# ==X value equals to X", "# !=X value doesn't equals to X"));
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
			if (Ref.serverType().isBukkit())
				config.setIfAbsent("nmsProvider-use-directly-jar", false, Arrays.asList("", "# In some cases Java isn't able to compile .java file and we have to use .jar file instead"));
			config.setIfAbsent("automatically-save-user-files", true, Arrays.asList("", "# Save all loaded user files (in memory) every 6 hours"));
			config.setIfAbsent("default-json-handler", "TheAPI",
					Arrays.asList("", "# Default Json reader & writer for reading & writing Config files", "# Guava - From Google (Default)", "# TheAPI - Our own project"));
			AUTOMATICALLY_USER_SAVING_TASK = config.getBoolean("automatically-save-user-files");
			if (Ref.serverType().isBukkit())
				config.setIfAbsent("fallback-scoreboard-support", false,
						Arrays.asList("", "# Scoreboard lines will be split into 3 parts as it is on version 1.12.2 or lower,",
								"# so that players with older client (1.12.2-) can see the scoreboard as well as players with client 1.13+ (text length is limited to 48 chars)",
								"# This requires a bit more CPU usage and sends more packets as a result.",
								"# Enable this only if you have installed ViaVersion/ProtocolSupport and allows connection for 1.12.2 and older clients"));
			config.save(DataType.YAML);
			if (AUTOMATICALLY_USER_SAVING_TASK && savingScheduler == 0)
				savingScheduler = new Tasker() {

					@Override
					public void run() {
						// Save all players
						for (Config config : API.users.values())
							config.save("yaml");
					}
				}.runRepeating(432000, 432000); // Every 6 hours

			TimeUtils.timeSplit = config.getString("timeConvertor.settings.defaultSplit");

			for (TimeFormat format : TimeFormat.values())
				TimeUtils.timeConvertor.put(format, new TimeFormatter() {
					Pattern pattern = Pattern.compile("[+-]?[ ]*[0-9]+[ ]*(" + config.getString("timeConvertor." + format.name().toLowerCase() + ".matcher") + ")");

					@Override
					public Matcher matcher(String text) {
						return pattern.matcher(text);
					}

					@Override
					public String toString(long value) {
						for (String action : config.getStringList("timeConvertor." + format.name().toLowerCase() + ".convertor"))
							if (matchAction(action, value)) {
								action = action.substring(action.indexOf(" "));
								if (action.startsWith(" "))
									action = action.substring(1);
								return value + action;
							}
						return value + format.getDefaultSuffix();
					}
				});
			// Init libraries without waiting
			if (library != null) {
				File libraries = new File(path + "libraries");
				if (libraries.exists())
					for (File file : libraries.listFiles())
						library.load(file);
			}
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
								if (cn >= 64 && cn <= 70 || cn >= 97 && cn <= 102 || cn >= 48 && cn <= 57) {
									color.append(cn);
									continue;
								}
								// invalid hex
								--i;
								color.clear();
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
						if (i + 6 < input.length()) {
							color.append('#');
							for (int count = 0; count < 6; ++count) {
								char cn = input.charAt(++i);
								if (cn >= 64 && cn <= 70 || cn >= 97 && cn <= 102 || cn >= 48 && cn <= 57) {
									color.append(cn);
									continue;
								}
								--i;
								color.clear();
								break;
							}
						}
						break;
					default:
						break;
					}
				} else if (c == '#' && i + 6 < input.length()) {
					color.append('#');
					for (int count = 0; count < 6; ++count) {
						char cn = input.charAt(++i);
						if (cn >= 64 && cn <= 70 || cn >= 97 && cn <= 102 || cn >= 48 && cn <= 57) {
							color.append(cn);
							continue;
						}
						--i;
						color.clear();
						break;
					}
				}
			}
			return new String[] { color.length() == 0 ? null : color.toString(), formats.length() == 0 ? null : formats.toString() };
		}

		public String rainbow(String text, String firstHex, String secondHex, List<String> protectedStrings) {
			if (text == null)
				return text;
			return rainbow(text, 0, text.length(), firstHex, secondHex, protectedStrings);
		}

		public String rainbow(String text, int start, int end, String firstHex, String secondHex, List<String> protectedStrings) {
			if (text == null)
				return text;
			StringContainer container = new StringContainer(text);
			rawGradient(container, start, end, firstHex, secondHex, false, protectedStrings);
			return container.toString();
		}

		public String gradient(String text, String firstHex, String secondHex, List<String> protectedStrings) {
			if (text == null)
				return text;
			return gradient(text, 0, text.length(), firstHex, secondHex, protectedStrings);
		}

		public String gradient(String text, int start, int end, String firstHex, String secondHex, List<String> protectedStrings) {
			if (text == null)
				return text;
			StringContainer container = new StringContainer(text);
			rawGradient(container, start, end, firstHex, secondHex, true, protectedStrings);
			return container.toString();
		}

		public void rainbow(StringContainer container, String firstHex, String secondHex, List<String> protectedStrings) {
			rainbow(container, 0, container.length(), firstHex, secondHex, protectedStrings);
		}

		public void rainbow(StringContainer container, int start, int end, String firstHex, String secondHex, List<String> protectedStrings) {
			rawGradient(container, start, end, firstHex, secondHex, false, protectedStrings);
		}

		public void gradient(StringContainer container, String firstHex, String secondHex, List<String> protectedStrings) {
			gradient(container, 0, container.length(), firstHex, secondHex, protectedStrings);
		}

		public void gradient(StringContainer container, int start, int end, String firstHex, String secondHex, List<String> protectedStrings) {
			rawGradient(container, start, end, firstHex, secondHex, true, protectedStrings);
		}

		private char[] EMPTY_CHAR_ARRAY = {};
		private char[] RESET_CHAR_ARRAY = { '§', 'r' };

		private void rawGradient(StringContainer container, int start, int end, String firstHex, String secondHex, boolean defaultRainbow, List<String> protectedStrings) {
			boolean inRainbow = defaultRainbow;
			char[] formats = EMPTY_CHAR_ARRAY;

			// Skip regions
			int[][] skipRegions = EMPTY_ARRAY;
			byte allocated = 0;
			int currentSkipAt = -1;
			byte skipId = 0;

			// Cache chars instance
			char[] chars = new char[14];
			chars[0] = '§';
			chars[1] = 'x';
			chars[2] = '§';
			chars[4] = '§';
			chars[6] = '§';
			chars[8] = '§';
			chars[10] = '§';
			chars[12] = '§';

			int totalSize = end - start;
			if (protectedStrings != null && !protectedStrings.isEmpty()) {
				for (String protect : protectedStrings) {
					int size = protect.length();

					int num = start;
					while (true) {
						int position = container.indexOf(protect, num);
						if (position == -1 || position > end)
							break;
						num = position + size;
						if (allocated == 0 || allocated >= skipRegions.length - 1) {
							int[][] copy = new int[(allocated << 1) + 1][];
							if (allocated > 0)
								System.arraycopy(skipRegions, 0, copy, 0, skipRegions.length);
							skipRegions = copy;
						}
						totalSize -= size;
						skipRegions[allocated++] = new int[] { position, size };
					}
				}
				if (allocated > 0)
					currentSkipAt = skipRegions[0][0];
			}

			// FastMath
			double mathPart1 = Math.PI / (2 * totalSize);

			// R-G-B
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
				int rgb = parseHex(firstHex.charAt(0) == '§' ? toHex(firstHex) : firstHex);
				r = rgb >> 16 & 0xFF;
				g = rgb >> 8 & 0xFF;
				b = rgb & 0xFF;
				rgb = parseHex(secondHex.charAt(0) == '§' ? toHex(secondHex) : secondHex);
				intervalR = ((rgb >> 16 & 0xFF) - r) / (float) (totalSize - 1);
				intervalG = ((rgb >> 8 & 0xFF) - g) / (float) (totalSize - 1);
				intervalB = ((rgb & 0xFF) - b) / (float) (totalSize - 1);
				firstHex = null;
				secondHex = null;
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
					int rgb = parseHex(firstHex.charAt(0) == '§' ? toHex(firstHex) : firstHex);
					r = rgb >> 16 & 0xFF;
					g = rgb >> 8 & 0xFF;
					b = rgb & 0xFF;
					rgb = parseHex(secondHex.charAt(0) == '§' ? toHex(secondHex) : secondHex);
					intervalR = ((rgb >> 16 & 0xFF) - r) / (float) (totalSize - 1);
					intervalG = ((rgb >> 8 & 0xFF) - g) / (float) (totalSize - 1);
					intervalB = ((rgb & 0xFF) - b) / (float) (totalSize - 1);
					continue;
				}

				if (inRainbow)
					switch (c) {
					case ' ':
						if (formats.length == 2 && formats[1] == 'r') {
							container.insertMultipleChars(i, formats);
							formats = EMPTY_CHAR_ARRAY;
							i += 2;
							int aStep = (int) Math.round(Math.abs(2 * Math.asin(Math.sin(step * mathPart1)) / Math.PI * totalSize));
							insertHex(container, i, hexPiece(aStep, r, intervalR), hexPiece(aStep, g, intervalG), hexPiece(aStep, b, intervalB), chars);
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
							int aStep = (int) Math.round(Math.abs(2 * Math.asin(Math.sin(step * mathPart1)) / Math.PI * totalSize));
							insertHex(container, i, hexPiece(aStep, r, intervalR), hexPiece(aStep, g, intervalG), hexPiece(aStep, b, intervalB), chars);
							i += 14;
						} else {
							int aStep = (int) Math.round(Math.abs(2 * Math.asin(Math.sin(step * mathPart1)) / Math.PI * totalSize));
							insertHex(container, i, hexPiece(aStep, r, intervalR), hexPiece(aStep, g, intervalG), hexPiece(aStep, b, intervalB), chars);
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

		private int parseHex(String chars) {
			return Integer.decode(chars);
		}

		private int hexPiece(int step, int channelStart, float interval) {
			return Math.min(Math.max(Math.round(interval * step + channelStart), 0), 255);
		}

		private boolean isColor(int charAt) {
			return charAt >= 97 && charAt <= 102 || charAt >= 65 && charAt <= 70 || charAt >= 48 && charAt <= 57;
		}

		private boolean isFormat(int charAt) {
			return charAt >= 107 && charAt <= 111 || charAt == 114;
		}

		private void insertHex(StringContainer builder, int pos, int r, int g, int b, char[] chars) {
			int temp;
			for (int i = 0; i < 2; i++) {
				temp = r >> (1 - i) * 4 & 0xF;
				chars[3 + i * 2] = temp < 10 ? (char) ('0' + temp) : (char) ('a' + temp - 10);
			}

			for (int i = 0; i < 2; i++) {
				temp = g >> (1 - i) * 4 & 0xF;
				chars[7 + i * 2] = temp < 10 ? (char) ('0' + temp) : (char) ('a' + temp - 10);
			}

			for (int i = 0; i < 2; i++) {
				temp = b >> (1 - i) * 4 & 0xF;
				chars[11 + i * 2] = temp < 10 ? (char) ('0' + temp) : (char) ('a' + temp - 10);
			}
			builder.insertMultipleChars(pos, chars);
		}

		private String toHex(String from) {
			if (from.length() != 14)
				return "#000000";
			return "#" + from.charAt(3) + from.charAt(5) + from.charAt(7) + from.charAt(9) + from.charAt(11) + from.charAt(13);
		}
	}

	public static Basics basics() {
		return API.basics;
	}
}
