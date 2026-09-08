package me.devtec.shared.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import me.devtec.shared.Ref;
import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.annotations.Nullable;
import me.devtec.shared.components.base.Component;
import me.devtec.shared.components.base.ComponentEntity;
import me.devtec.shared.components.base.ComponentItem;
import me.devtec.shared.components.decorations.ClickEvent;
import me.devtec.shared.components.decorations.HoverEvent;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.json.Json;
import me.devtec.shared.text.TextRenderer;
import me.devtec.shared.utility.ColorUtils;

/**
 * Central conversion utility for TheAPI components.
 *
 * <p>
 * The class owns component-specific parsing and JSON traversal. Plain text
 * rendering is delegated to {@link TextRenderer}, which keeps placeholder and
 * color processing independent from the component representation.
 * </p>
 */
public class ComponentAPI {
	static final Map<String, ComponentTransformer<?>> transformers = new HashMap<>();
	static Boolean hexModeEnabled;

	/** Returns a registered platform component transformer by name. */
	public static ComponentTransformer<?> transformer(String name) {
		return ComponentAPI.transformers.get(name.toUpperCase());
	}

	/** Registers or replaces a platform component transformer. */
	public static ComponentTransformer<?> registerTransformer(String name, ComponentTransformer<?> transformer) {
		if (ComponentAPI.transformers.put(name.toUpperCase(), transformer) != null)
			Logger.getGlobal()
					.warning("[TheAPI/ComponentAPI] Overriding " + name.toUpperCase() + " component transformer.");
		return transformer;
	}

	/** Removes a registered platform component transformer. */
	public static ComponentTransformer<?> unregisterTransformer(String name) {
		return ComponentAPI.transformers.remove(name.toUpperCase());
	}

	/** Returns the BungeeCord transformer when it is registered. */
	public static ComponentTransformer<?> bungee() {
		return ComponentAPI.transformer("BUNGEECORD");
	}

	/** Returns the Adventure transformer when it is registered. */
	public static ComponentTransformer<?> adventure() {
		return ComponentAPI.transformer("ADVENTURE");
	}

	/** Converts a component to its readable legacy-text representation. */
	public static String toString(Component input) {
		return input == null ? null : input.toString();
	}

	/** Parses legacy/hex text using the platform's default hex support. */
	public static Component fromString(String input) {
		if (input == null)
			return null;
		return ComponentAPI.fromString(input,
				hexModeEnabled == null ? (hexModeEnabled = !Ref.type().isBukkit() || Ref.isAtLeast(16, 0))
						: hexModeEnabled,
				true);
	}

	/** Parses text with explicit control over hex color support. */
	public static Component fromString(String input, boolean hexMode) {
		if (input == null)
			return null;
		return ComponentAPI.fromString(
				input, hexMode
						? hexModeEnabled == null ? (hexModeEnabled = !Ref.type().isBukkit() || Ref.isAtLeast(16, 0))
								: hexModeEnabled
						: false,
				true);
	}

	/**
	 * Renders text and then parses it into a component.
	 *
	 * <p>
	 * Placeholder values are rendered before the component parser runs, so a
	 * replacement may intentionally contain legacy or hex formatting codes.
	 * </p>
	 */
	public static Component fromString(String input, @Nullable TextRenderer renderer, @Nullable UUID target) {
		if (input == null)
			return null;
		return fromString(renderText(input, renderer, target));
	}

	/** Renders text using the renderer's default target and parses it. */
	public static Component fromString(String input, @Nullable TextRenderer renderer) {
		return fromString(input, renderer, null);
	}

	/**
	 * Reads a config value and converts plain text, component JSON or mixed lists
	 * through one consistent rendering pipeline.
	 */
	public static Component fromConfig(@Nonnull Config config, @Nonnull String path, @Nullable TextRenderer renderer,
			@Nullable UUID target) {
		Checkers.nonNull(config, "Config");
		Checkers.nonNull(path, "Path");

		if (!config.existsKey(path))
			return Component.EMPTY_COMPONENT;

		final Object value = config.get(path);

		if (config.isJson(path) && (value instanceof Collection || value instanceof Map)) {
			if (value instanceof Collection && ((Collection<?>) value).isEmpty()
					|| value instanceof Map && ((Map<?, ?>) value).isEmpty())
				return Component.EMPTY_COMPONENT;

			return fromJson(renderJson(value, renderer, target));
		}

		if (value instanceof Collection)
			return fromList((Collection<?>) value, renderer, target);

		final String text = config.getString(path);
		if (text == null || text.isEmpty())
			return Component.EMPTY_COMPONENT;

		return fromString(text, renderer, target);
	}

	/** Reads a config value using the renderer's default target. */
	public static Component fromConfig(@Nonnull Config config, @Nonnull String path, @Nullable TextRenderer renderer) {
		return fromConfig(config, path, renderer, null);
	}

	/** Reads a config value without additional text rendering. */
	public static Component fromConfig(@Nonnull Config config, @Nonnull String path) {
		return fromConfig(config, path, (TextRenderer) null, null);
	}

	/**
	 * Converts a mixed config list containing strings and component JSON values.
	 * Each logical list entry is separated by a newline component.
	 */
	public static <V> Component fromList(@Nonnull Collection<? extends V> values, @Nullable TextRenderer renderer,
			@Nullable UUID target) {
		Checkers.nonNull(values, "Collection");
		if (values.isEmpty())
			return Component.EMPTY_COMPONENT;

		Component result = new Component();

		for (Object value : values) {
			final Component part;

			if (value instanceof Collection || value instanceof Map)
				part = fromJson(renderJson(value, renderer, target));
			else
				part = fromString(String.valueOf(value), renderer, target);

			if (part == null || part.isEmpty())
				continue;

			if (result.isEmpty())
				result = part;
			else {
				result.append(Component.NEW_LINE);
				result.append(part);
			}
		}

		return result.isEmpty() ? Component.EMPTY_COMPONENT : result;
	}

	/** Converts a mixed config list using the renderer's default target. */
	public static <V> Component fromList(@Nonnull Collection<? extends V> values, @Nullable TextRenderer renderer) {
		return fromList(values, renderer, null);
	}

	/**
	 * Renders all textual values inside a component JSON graph without mutating the
	 * source object.
	 *
	 * <p>
	 * The {@code color} property is intentionally preserved as structured component
	 * metadata. Hover text payloads are converted back into component JSON after
	 * rendering so formatting inside placeholder values remains meaningful.
	 * </p>
	 */
	public static Object renderJson(Object object, @Nullable TextRenderer renderer, @Nullable UUID target) {
		if (renderer == null)
			return object;
		return renderJson0(object, renderer, resolveTarget(renderer, target), false);
	}

	/** Renders component JSON using the renderer's default target. */
	public static Object renderJson(Object object, @Nullable TextRenderer renderer) {
		return renderJson(object, renderer, null);
	}

	/**
	 * Renders a raw JSON value and parses the result into a component.
	 */
	public static Component fromJson(Object object, @Nullable TextRenderer renderer, @Nullable UUID target) {
		return fromJson(renderJson(object, renderer, target));
	}

	/** Renders and parses a raw JSON value using the renderer's default target. */
	public static Component fromJson(Object object, @Nullable TextRenderer renderer) {
		return fromJson(object, renderer, null);
	}

	/**
	 * Parses an already decoded JSON value into a component.
	 */
	@SuppressWarnings("unchecked")
	public static Component fromJson(Object object) {
		if (object == null)
			return Component.EMPTY_COMPONENT;
		if (object instanceof Map)
			return fromJson((Map<String, Object>) object);
		if (object instanceof Collection)
			return fromJson((Collection<?>) object);
		return fromString(String.valueOf(object));
	}

	private static Object renderJson0(Object object, TextRenderer renderer, UUID target, boolean hoverPayload) {
		if (object instanceof Map) {
			final Map<?, ?> source = (Map<?, ?>) object;
			final Map<String, Object> rendered = new HashMap<>(mapCapacity(source.size()));

			for (Map.Entry<?, ?> entry : source.entrySet()) {
				final String key = String.valueOf(entry.getKey());

				if ("color".equals(key)) {
					rendered.put(key, entry.getValue());
					continue;
				}

				final boolean componentPayload = hoverPayload
						&& ("value".equals(key) || "content".equals(key) || "contents".equals(key));

				rendered.put(key,
						renderJson0(entry.getValue(), renderer, target, componentPayload || "hoverEvent".equals(key)));
			}
			return rendered;
		}

		if (object instanceof Collection) {
			final Collection<?> source = (Collection<?>) object;
			final List<Object> rendered = new ArrayList<>(source.size());
			for (Object value : source)
				rendered.add(renderJson0(value, renderer, target, hoverPayload));
			return rendered;
		}

		if (object instanceof String) {
			final String value = renderer.render((String) object, target);
			return hoverPayload ? toJsonList(fromString(value)) : value;
		}

		return object;
	}

	private static String renderText(String input, TextRenderer renderer, UUID target) {
		return renderer == null ? normalizeEscapedNewLines(input)
				: renderer.render(input, resolveTarget(renderer, target));
	}

	private static UUID resolveTarget(TextRenderer renderer, UUID target) {
		return target == null ? renderer.target() : target;
	}

	private static String normalizeEscapedNewLines(String input) {
		return input.indexOf("\\n") == -1 ? input : input.replace("\\n", "\n");
	}

	private static int mapCapacity(int size) {
		if (size < 3)
			return size + 1;
		if (size >= 1 << 30)
			return Integer.MAX_VALUE;
		return (int) (size / 0.75F) + 1;
	}

	public static Component fromString(String input, boolean hexMode, boolean urlMode) {
		if (input == null || input.isEmpty())
			return Component.EMPTY_COMPONENT;

		Component main = new Component("");
		Component component = new Component();
		main.append(component);

		// UrlFinder
		int componentPos = 0;
		int componentStartAt = 0;
		int spaceFinder = 0;

		int privatePos = 0;
		byte lookingMode = 0;
		int prevCount = 0;
		int count = 0;

		int initAt = 0;
		StringContainer container = new StringContainer(input.length());
		charLoop: for (int i = 0; i < input.length(); ++i) {
			char c = input.charAt(i);
			if (c == '§' && i + 1 < input.length()) {
				char afterSymbol = input.charAt(++i);
				if (hexMode && afterSymbol == 'x' && i + 12 < input.length())
					for (int ic = 1; ic < 13; ++ic) {
						afterSymbol = input.charAt(i + ic);
						if (ic % 2 == 1) {
							if (afterSymbol == '§')
								continue;
							container.append('§').append('x');
							continue charLoop;
						}
						if (!isColorChar(afterSymbol)) {
							container.append('§').append('x');
							continue charLoop;
						}
						if (ic == 12) {
							if (!container.isEmpty()) {
								privatePos = 0;
								spaceFinder = 0;
								component.setText(container.toString());
								container.clear();
								component = new Component();
								main.append(component);
								++componentPos;
							}
							component.setColor(new String(
									new char[] { '#', input.charAt(i + 2), input.charAt(i + 4), input.charAt(i + 6),
											input.charAt(i + 8), input.charAt(i + 10), input.charAt(i + 12) }));
							i += 12;
							continue charLoop;
						}
					}
				else if (isColorChar(afterSymbol)) {
					if (!container.isEmpty()) {
						privatePos = 0;
						spaceFinder = 0;
						component.setText(container.toString());
						container.clear();
						component = new Component();
						main.append(component);
						++componentPos;
					}
					component.setColorFromChar(afterSymbol);
					continue;
				} else if (isFormat(afterSymbol)) {
					if (hasFormat(component, afterSymbol))
						continue;
					if (!container.isEmpty()) {
						privatePos = 0;
						spaceFinder = 0;
						component.setText(container.toString());
						container.clear();
						component = new Component().copyOf(component);
						main.append(component);
						++componentPos;
					}
					component.setFormatFromChar(afterSymbol, true);
					continue;
				}
				container.append('§').append(afterSymbol);
				continue;
			}
			container.append(c);
			if (urlMode)
				switchCase: switch (lookingMode) {
				case 0:
					initAt = i;
					componentStartAt = componentPos;
					spaceFinder = privatePos;
					if (c == 'h' && i + 11 < input.length()) {
						if (input.charAt(i + 1) == 't' && input.charAt(i + 2) == 't' && input.charAt(i + 3) == 'p') {
							container.append('t').append('t').append('p');
							if (input.charAt(i + 4) == 's' && input.charAt(i + 5) == ':' && input.charAt(i + 6) == '/'
									&& input.charAt(i + 7) == '/') {
								container.append('s').append(':').append('/').append('/');
								i += 7;
								c = input.charAt(i + 1);
								if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_') {
									container.append(c);
									++i;
									lookingMode = 1;
									count = 1;
									spaceFinder = privatePos == 0 || container.charAt(privatePos - 1) == ' '
											? privatePos
											: privatePos - 1;
								}
							} else if (input.charAt(i + 4) == ':' && input.charAt(i + 5) == '/'
									&& input.charAt(i + 6) == '/') {
								container.append(':').append('/').append('/');
								i += 6;
								c = input.charAt(i + 1);
								if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_') {
									container.append(c);
									++i;
									lookingMode = 1;
									count = 1;
									spaceFinder = privatePos == 0 || container.charAt(privatePos - 1) == ' '
											? privatePos
											: privatePos - 1;
								}
							}
						}
						break;
					}
					if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_') {
						lookingMode = 1;
						count = 1;
						break;
					}
					break;
				case 1:
					if (c == '.') { // xxx.
						if (count >= 2) {
							prevCount = count;
							count = 0;
							lookingMode = 2;
						} else {
							lookingMode = 0;
							spaceFinder = 0;
						}
						break;
					}
					if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_') {
						++count;
						break;
					}
					if (c == ' ')
						spaceFinder = privatePos + 1;
					else
						spaceFinder = 0;
					lookingMode = 0;
					break;
				case 2: // xxx.(lookingForPossibleEnding/xxx)
					if (c >= '0' && c <= '9') {
						lookingMode = 0; // Start
						spaceFinder = 0;
						count = 0;
						break;
					}
					if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
						if (++count == 1 && prevCount >= 4)
							switch (c) {
							case 'a':
								if (i + 2 < input.length() && (input.charAt(i + 1) == 'p' && input.charAt(i + 2) == 'p'
										|| input.charAt(i + 1) == 'r' && input.charAt(i + 2) == 't')) {
									container.append(input.charAt(i + 1)).append(input.charAt(i + 2));
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'g':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'g') {
									container.append('g');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'm':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'c') {
									container.append('c');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 6 < input.length() && input.charAt(i + 1) == 'o'
										&& input.charAt(i + 2) == 'n' && input.charAt(i + 3) == 's'
										&& input.charAt(i + 4) == 't' && input.charAt(i + 5) == 'e'
										&& input.charAt(i + 6) == 'r') {
									for (int ic = 1; ic < 7; ++ic)
										container.append(input.charAt(i + ic));
									i += 6;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'c':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'z') {
									container.append('z');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 2 < input.length() && input.charAt(i + 1) == 'o'
										&& input.charAt(i + 2) == 'm') {
									container.append('o').append('m');
									i += 2;
									lookingMode = 4; // Read until space
								} else if (i + 4 < input.length() && input.charAt(i + 1) == 'l'
										&& input.charAt(i + 2) == 'o' && input.charAt(i + 3) == 'u'
										&& input.charAt(i + 4) == 'd') {
									for (int ic = 1; ic < 5; ++ic)
										container.append(input.charAt(i + ic));
									i += 4;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'n':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'e'
										&& input.charAt(i + 2) == 't') {
									container.append('e').append('t');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'o':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'r'
										&& input.charAt(i + 2) == 'g') {
									container.append('r').append('g');
									i += 2;
									lookingMode = 4; // Read until space
								} else if (i + 5 < input.length() && input.charAt(i + 1) == 'n'
										&& input.charAt(i + 2) == 'l' && input.charAt(i + 3) == 'i'
										&& input.charAt(i + 4) == 'n' && input.charAt(i + 5) == 'e') {
									for (int ic = 1; ic < 6; ++ic)
										container.append(input.charAt(i + ic));
									i += 5;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'i':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'o') {
									container.append('o');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 3 < input.length() && input.charAt(i + 1) == 'n'
										&& input.charAt(i + 2) == 'f' && input.charAt(i + 3) == 'o') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'u':
								if (i + 1 < input.length()
										&& (input.charAt(i + 1) == 's' || input.charAt(i + 1) == 'k')) {
									container.append(input.charAt(i + 1));
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'd':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'e') {
									container.append('e');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'b':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'l' && input.charAt(i + 2) == 'o'
										&& input.charAt(i + 3) == 'g') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 't':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'e' && input.charAt(i + 2) == 'c'
										&& input.charAt(i + 3) == 'h') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 's':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'k') {
									container.append('k');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 3 < input.length() && input.charAt(i + 1) == 'i'
										&& input.charAt(i + 2) == 't' && input.charAt(i + 3) == 'e') {
									container.append('i').append('t').append('e');
									i += 3;
									lookingMode = 4; // Read until space
								} else if (i + 4 < input.length()
										&& (input.charAt(i + 1) == 'p' && input.charAt(i + 2) == 'a'
												&& input.charAt(i + 3) == 'c' && input.charAt(i + 4) == 'e'
												|| input.charAt(i + 1) == 't' && input.charAt(i + 2) == 'o'
														&& input.charAt(i + 3) == 'r' && input.charAt(i + 4) == 'e')) {
									for (int ic = 1; ic < 5; ++ic)
										container.append(input.charAt(i + ic));
									i += 4;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'p':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'l') {
									container.append('l');
									i += 1;
									lookingMode = 4; // Read until space
								}
								if (i + 2 < input.length() && input.charAt(i + 1) == 'r'
										&& input.charAt(i + 2) == 'o') {
									container.append('r').append('o');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'e':
							case 'r':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'u') {
									container.append('u');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'f':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'u'
										&& input.charAt(i + 2) == 'n') {
									container.append('u').append('n');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'x':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'y'
										&& input.charAt(i + 2) == 'z') {
									container.append('y').append('z');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'w':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'i' && input.charAt(i + 2) == 'k'
										&& input.charAt(i + 3) == 'i') {
									container.append('i').append('k').append('i');
									i += 3;
									lookingMode = 4; // Read until space
								} else if (i + 6 < input.length() && input.charAt(i + 1) == 'e'
										&& input.charAt(i + 2) == 'b' && input.charAt(i + 3) == 's'
										&& input.charAt(i + 4) == 'i' && input.charAt(i + 5) == 't'
										&& input.charAt(i + 6) == 'e') {
									for (int ic = 1; ic < 7; ++ic)
										container.append(input.charAt(i + ic));
									i += 6;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							}
						break;
					}
					if (c == '.') { // xxx.
						if (count >= 4) {
							count = 0;
							lookingMode = 3;
						} else {
							lookingMode = 0;
							spaceFinder = 0;
						}
						break;
					}
					lookingMode = 0;
					spaceFinder = 0;
					break;
				case 3:
					if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
						if (++count == 1)
							switch (c) {
							case 'a':
								if (i + 2 < input.length() && (input.charAt(i + 1) == 'p' && input.charAt(i + 2) == 'p'
										|| input.charAt(i + 1) == 'r' && input.charAt(i + 2) == 't')) {
									container.append(input.charAt(i + 1)).append(input.charAt(i + 2));
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'g':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'g') {
									container.append('g');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'm':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'c') {
									container.append('c');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 6 < input.length() && input.charAt(i + 1) == 'o'
										&& input.charAt(i + 2) == 'n' && input.charAt(i + 3) == 's'
										&& input.charAt(i + 4) == 't' && input.charAt(i + 5) == 'e'
										&& input.charAt(i + 6) == 'r') {
									for (int ic = 1; ic < 7; ++ic)
										container.append(input.charAt(i + ic));
									i += 6;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'c':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'z') {
									container.append('z');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 2 < input.length() && input.charAt(i + 1) == 'o'
										&& input.charAt(i + 2) == 'm') {
									container.append('o').append('m');
									i += 2;
									lookingMode = 4; // Read until space
								} else if (i + 4 < input.length() && input.charAt(i + 1) == 'l'
										&& input.charAt(i + 2) == 'o' && input.charAt(i + 3) == 'u'
										&& input.charAt(i + 4) == 'd') {
									for (int ic = 1; ic < 5; ++ic)
										container.append(input.charAt(i + ic));
									i += 4;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'n':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'e'
										&& input.charAt(i + 2) == 't') {
									container.append('e').append('t');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'o':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'r'
										&& input.charAt(i + 2) == 'g') {
									container.append('r').append('g');
									i += 2;
									lookingMode = 4; // Read until space
								} else if (i + 5 < input.length() && input.charAt(i + 1) == 'n'
										&& input.charAt(i + 2) == 'l' && input.charAt(i + 3) == 'i'
										&& input.charAt(i + 4) == 'n' && input.charAt(i + 5) == 'e') {
									for (int ic = 1; ic < 6; ++ic)
										container.append(input.charAt(i + ic));
									i += 5;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'i':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'o') {
									container.append('o');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 3 < input.length() && input.charAt(i + 1) == 'n'
										&& input.charAt(i + 2) == 'f' && input.charAt(i + 3) == 'o') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'u':
								if (i + 1 < input.length()
										&& (input.charAt(i + 1) == 's' || input.charAt(i + 1) == 'k')) {
									container.append(input.charAt(i + 1));
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'd':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'e') {
									container.append('e');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'b':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'l' && input.charAt(i + 2) == 'o'
										&& input.charAt(i + 3) == 'g') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 't':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'e' && input.charAt(i + 2) == 'c'
										&& input.charAt(i + 3) == 'h') {
									for (int ic = 1; ic < 4; ++ic)
										container.append(input.charAt(i + ic));
									i += 3;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 's':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'k') {
									container.append('k');
									i += 1;
									lookingMode = 4; // Read until space
								} else if (i + 3 < input.length() && input.charAt(i + 1) == 'i'
										&& input.charAt(i + 2) == 't' && input.charAt(i + 3) == 'e') {
									container.append('i').append('t').append('e');
									i += 3;
									lookingMode = 4; // Read until space
								} else if (i + 4 < input.length()
										&& (input.charAt(i + 1) == 'p' && input.charAt(i + 2) == 'a'
												&& input.charAt(i + 3) == 'c' && input.charAt(i + 4) == 'e'
												|| input.charAt(i + 1) == 't' && input.charAt(i + 2) == 'o'
														&& input.charAt(i + 3) == 'r' && input.charAt(i + 4) == 'e')) {
									for (int ic = 1; ic < 5; ++ic)
										container.append(input.charAt(i + ic));
									i += 4;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'p':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'l') {
									container.append('l');
									i += 1;
									lookingMode = 4; // Read until space
								}
								if (i + 2 < input.length() && input.charAt(i + 1) == 'r'
										&& input.charAt(i + 2) == 'o') {
									container.append('r').append('o');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'e':
							case 'r':
								if (i + 1 < input.length() && input.charAt(i + 1) == 'u') {
									container.append('u');
									i += 1;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'f':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'u'
										&& input.charAt(i + 2) == 'n') {
									container.append('u').append('n');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'x':
								if (i + 2 < input.length() && input.charAt(i + 1) == 'y'
										&& input.charAt(i + 2) == 'z') {
									container.append('y').append('z');
									i += 2;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							case 'w':
								if (i + 3 < input.length() && input.charAt(i + 1) == 'i' && input.charAt(i + 2) == 'k'
										&& input.charAt(i + 3) == 'i') {
									container.append('i').append('k').append('i');
									i += 3;
									lookingMode = 4; // Read until space
								} else if (i + 6 < input.length() && input.charAt(i + 1) == 'e'
										&& input.charAt(i + 2) == 'b' && input.charAt(i + 3) == 's'
										&& input.charAt(i + 4) == 'i' && input.charAt(i + 5) == 't'
										&& input.charAt(i + 6) == 'e') {
									for (int ic = 1; ic < 7; ++ic)
										container.append(input.charAt(i + ic));
									i += 6;
									lookingMode = 4; // Read until space
								}
								break switchCase;
							}
						break;
					}
					if (c == '/') {
						lookingMode = 4; // Read until space
						break;
					}
					lookingMode = 0;
					spaceFinder = 0;
					break;
				case 4:
					if (c == ' ') {
						ClickEvent event = new ClickEvent(ClickEvent.Action.OPEN_URL,
								input.substring(initAt, i).replace('§', '&'));
						if (event.getValue().startsWith("http://"))
							event.setValue("https" + event.getValue().substring(4));
						else if (!event.getValue().startsWith("https://"))
							event.setValue("https://" + event.getValue());
						Component start = componentStartAt == componentPos ? component
								: main.getExtra().get(componentStartAt);
						if (componentStartAt == componentPos) { // Start & end are same
							if (spaceFinder == 0) {
								start.setText(container.toString());
								start.setClickEvent(event);
							} else {
								Component withUrl = new Component(
										container.substring(spaceFinder, container.length() - 1)).copyOf(start);
								withUrl.setClickEvent(event);
								main.append(withUrl);
								++componentPos;
								start.setText(container.substring(0, spaceFinder));
							}
							container.clear();
							container.append(' ');
							component = new Component().copyOf(start);
						} else {
							for (int middle = componentStartAt + 1; middle < componentPos; ++middle)
								main.getExtra().get(middle).setClickEvent(event);

							// start
							Component withUrl = new Component(start.getText().substring(spaceFinder)).copyOf(start);
							withUrl.setClickEvent(event);
							main.getExtra().add(componentStartAt + 1, withUrl);
							start.setText(start.getText().substring(0, spaceFinder));
							++componentPos;

							container.deleteCharAt(container.length() - 1);

							// end
							Component end = main.getExtra().get(componentPos);
							end.setText(container.toString());
							end.setClickEvent(event);
							++componentPos;
							container.clear();
							container.append(' ');
							component = new Component().copyOf(end);
						}
						main.append(component);
						privatePos = 0;
						++componentPos;
						spaceFinder = 0;
						lookingMode = 0;
						count = 0;
					}
					break;
				}
			++privatePos;
		}
		if (lookingMode == 4) {
			ClickEvent event = new ClickEvent(ClickEvent.Action.OPEN_URL, input.substring(initAt).replace('§', '&'));
			if (event.getValue().startsWith("http://"))
				event.setValue("https" + event.getValue().substring(4));
			else if (!event.getValue().startsWith("https://"))
				event.setValue("https://" + event.getValue());
			Component start = componentStartAt == componentPos ? component : main.getExtra().get(componentStartAt);
			if (componentStartAt == componentPos) { // Start & end are same
				if (spaceFinder == 0) {
					start.setText(container.toString());
					start.setClickEvent(event);
				} else {
					Component withUrl = new Component(container.substring(spaceFinder)).copyOf(start);
					withUrl.setClickEvent(event);
					main.append(withUrl);
					start.setText(container.substring(0, spaceFinder));
				}
			} else {
				for (int middle = componentStartAt + 1; middle < componentPos; ++middle)
					main.getExtra().get(middle).setClickEvent(event);

				// start
				Component withUrl = new Component(start.getText().substring(spaceFinder)).copyOf(start);
				withUrl.setClickEvent(event);
				main.getExtra().add(componentStartAt + 1, withUrl);
				start.setText(start.getText().substring(0, spaceFinder));
				++componentPos;

				// end
				Component end = main.getExtra().get(componentPos);
				end.setText(container.toString());
				end.setClickEvent(event);
			}
		} else if (!container.isEmpty())
			component.setText(container.toString());
		else if (container.isEmpty() && main.getExtra().get(main.getExtra().size() - 1).isStyleSame(
				main.getExtra().size() == 1 ? main : main.getExtra().get(Math.max(0, main.getExtra().size() - 2))))
			main.getExtra().remove(main.getExtra().size() - 1);
		return main;
	}

	public static List<Map<String, Object>> toJsonList(String text) {
		return ComponentAPI.toJsonList(ComponentAPI.fromString(text));
	}

	public static List<Map<String, Object>> toJsonList(Component component) {
		List<Map<String, Object>> list = new LinkedList<>();
		list.add(component.toJsonMapWithExtras());
		return list;
	}

	@SuppressWarnings("unchecked")
	public static String listToString(List<?> list) {
		StringContainer builder = new StringContainer(list.size() * 20);
		for (Object text : list)
			if (text instanceof Map)
				builder.append(ComponentAPI.getColor(((Map<String, Object>) text).get("color")))
						.append(String.valueOf(((Map<String, Object>) text).get("text")));
			else
				builder.append(ColorUtils.colorize(text + ""));
		return builder.toString();
	}

	@SuppressWarnings("unchecked")
	public static Component fromJson(String json) {
		Object obj = Json.reader().simpleRead(json);
		if (obj instanceof Map)
			return fromJson((Map<String, Object>) obj);
		if (!(obj instanceof Collection))
			return fromString(json);
		return fromJson((Collection<?>) obj);
	}

	/** Parses an already decoded JSON array into one component tree. */
	public static Component fromJson(Collection<?> collection) {
		return fromJson(collection, true);
	}

	private static Component fromJson(Collection<?> collection, boolean newLines) {
		if (collection == null || collection.isEmpty())
			return Component.EMPTY_COMPONENT;

		Component component = new Component("");
		boolean first = true;

		for (Object value : collection) {
			Component child = fromJsonValue(value);

			if (child == null || child.isEmpty())
				continue;

			if (newLines && !first)
				component.append(Component.NEW_LINE);

			component.append(child);
			first = false;
		}

		return component.isEmpty() ? Component.EMPTY_COMPONENT : component;
	}

	/** Parses an already decoded JSON object into a component tree. */
	@SuppressWarnings("unchecked")
	public static Component fromJson(Map<String, Object> map) {
		if (map == null || map.isEmpty())
			return Component.EMPTY_COMPONENT;
		if (map.containsKey("id") && map.containsKey("type"))
			return ComponentEntity.fromJson(map);
		if (map.containsKey("id"))
			return ComponentItem.fromJson(map);

		Object rawText = map.get("text");
		Component component = Component.fromString(rawText == null ? "" : String.valueOf(rawText));
		if (component == null)
			component = new Component("");

		if (map.containsKey("color")) {
			String color = String.valueOf(map.get("color"));
			component.setColor(color);
			if (component.getExtra() != null)
				for (Component child : component.getExtra())
					if (child.getColor() == null)
						child.setColor(color);
		}
		if (map.containsKey("font")) {
			String font = String.valueOf(map.get("font"));
			component.setFont(font);
			if (component.getExtra() != null)
				for (Component child : component.getExtra())
					if (child.getFont() == null)
						child.setFont(font);
		}
		if (map.containsKey("bold")) {
			boolean bold = Boolean.TRUE.equals(map.get("bold"));
			component.setBold(bold);
			if (component.getExtra() != null)
				for (Component child : component.getExtra())
					if (child.getColor() == null)
						child.setBold(bold);
		}
		if (map.containsKey("italic")) {
			boolean italic = Boolean.TRUE.equals(map.get("italic"));
			component.setItalic(italic);
			if (component.getExtra() != null)
				for (Component child : component.getExtra())
					if (child.getColor() == null)
						child.setItalic(italic);
		}
		if (map.containsKey("obfuscated")) {
			boolean obfuscated = Boolean.TRUE.equals(map.get("obfuscated"));
			component.setObfuscated(obfuscated);
			if (component.getExtra() != null)
				for (Component child : component.getExtra())
					if (child.getColor() == null)
						child.setObfuscated(obfuscated);
		}
		if (map.containsKey("underlined")) {
			boolean underlined = Boolean.TRUE.equals(map.get("underlined"));
			component.setUnderlined(underlined);
			if (component.getExtra() != null)
				for (Component child : component.getExtra())
					if (child.getColor() == null)
						child.setUnderlined(underlined);
		}
		if (map.containsKey("strikethrough")) {
			boolean strikethrough = Boolean.TRUE.equals(map.get("strikethrough"));
			component.setStrikethrough(strikethrough);
			if (component.getExtra() != null)
				for (Component child : component.getExtra())
					if (child.getColor() == null)
						child.setStrikethrough(strikethrough);
		}

		Object hoverObject = map.get("hoverEvent");
		if (hoverObject instanceof Map) {
			Map<String, Object> hoverMap = (Map<String, Object>) hoverObject;
			Object actionObject = hoverMap.get("action");
			Object payload = hoverMap.containsKey("value") ? hoverMap.get("value")
					: hoverMap.containsKey("content") ? hoverMap.get("content") : hoverMap.get("contents");

			if (actionObject != null) {
				HoverEvent hover = new HoverEvent(HoverEvent.Action.valueOf(String.valueOf(actionObject).toUpperCase()),
						fromJsonValue(payload));

				if (component.getExtra() != null)
					for (Component child : component.getExtra())
						if (child.getHoverEvent() == null)
							child.setHoverEvent(hover);
				if (component.getHoverEvent() == null)
					component.setHoverEvent(hover);
			}
		}

		Object clickObject = map.get("clickEvent");
		if (clickObject instanceof Map) {
			Map<String, Object> clickMap = (Map<String, Object>) clickObject;
			Object actionObject = clickMap.get("action");
			Object valueObject = clickMap.get("value");
			if (actionObject != null && valueObject != null) {
				ClickEvent click = new ClickEvent(ClickEvent.Action.valueOf(String.valueOf(actionObject).toUpperCase()),
						String.valueOf(valueObject));
				component.setClickEvent(click);
				if (component.getExtra() != null)
					for (Component child : component.getExtra())
						// Explicit JSON click events override URL events inferred from text.
						child.setClickEvent(click);
			}
		}

		if (map.containsKey("insertion") && map.get("insertion") != null)
			component.setInsertion(String.valueOf(map.get("insertion")));

		Object extra = map.get("extra");
		if (extra instanceof Collection)
			for (Object value : (Collection<?>) extra) {
				Component child = fromJsonValue(value);
				if (child != null && !child.isEmpty())
					component.append(child);
			}
		else if (extra != null) {
			Component child = fromJsonValue(extra);
			if (child != null && !child.isEmpty())
				component.append(child);
		}

		return component;
	}

	@SuppressWarnings("unchecked")
	private static Component fromJsonValue(Object value) {
		if (value == null)
			return Component.EMPTY_COMPONENT;
		if (value instanceof Map)
			return fromJson((Map<String, Object>) value);
		if (value instanceof Collection)
			return fromJson((Collection<?>) value, false);
		if (value instanceof Component)
			return (Component) value;
		return fromString(String.valueOf(value));
	}

	private static boolean hasFormat(Component component, char afterSymbol) {
		switch (afterSymbol) {
		case 'k':
		case 'K':
			return component.isObfuscated();
		case 'l':
		case 'L':
			return component.isBold();
		case 'm':
		case 'M':
			return component.isStrikethrough();
		case 'n':
		case 'N':
			return component.isUnderlined();
		case 'o':
		case 'O':
			return component.isItalic();
		case 'r':
		case 'R':
			return !component.isObfuscated() && !component.isBold() && !component.isStrikethrough()
					&& !component.isUnderlined() && !component.isItalic();
		}
		return false;
	}

	private static boolean isColorChar(char afterSymbol) {
		return afterSymbol >= '0' && afterSymbol <= '9' || afterSymbol >= 'a' && afterSymbol <= 'f'
				|| afterSymbol >= 'A' && afterSymbol <= 'F';
	}

	private static boolean isFormat(char afterSymbol) {
		return afterSymbol >= 'k' && afterSymbol <= 'o' || afterSymbol >= 'K' && afterSymbol <= 'O';
	}

	private static String getColor(Object color) {
		if (color == null)
			return "";
		if (color.toString().startsWith("#"))
			return ColorUtils.color.replaceHex(color.toString());
		return "§" + Component.colorToChar(color.toString());
	}
}
