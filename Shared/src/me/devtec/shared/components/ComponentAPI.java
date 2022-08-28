package me.devtec.shared.components;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import me.devtec.shared.Ref;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.utility.StringUtils;

public class ComponentAPI {
	static Pattern url = Pattern.compile("(w{3}\\\\.|[a-zA-Z0-9+&@#/%?=~_|!:,.;-]+:\\/\\/)?[a-zA-Z0-9+&@#/%?=~_|!:,.;-]+\\w\\.[a-zA-Z0-9+&@#/%?=~_|!:,.;-]{1,}\\w");
	static Map<String, ComponentTransformer<?>> transformers = new HashMap<>();

	public static ComponentTransformer<?> transformer(String name) {
		return ComponentAPI.transformers.get(name.toUpperCase());
	}

	public static ComponentTransformer<?> registerTransformer(String name, ComponentTransformer<?> transformer) {
		if (ComponentAPI.transformers.put(name.toUpperCase(), transformer) != null)
			System.out.println("[TheAPI] Overriding " + name.toUpperCase() + " transformer.");
		return transformer;
	}

	public static ComponentTransformer<?> unregisterTransformer(String name) {
		return ComponentAPI.transformers.remove(name.toUpperCase());
	}

	public static ComponentTransformer<?> bungee() {
		return ComponentAPI.transformer("BUNGEECORD");
	}

	public static ComponentTransformer<?> adventure() {
		return ComponentAPI.transformer("ADVENTURE");
	}

	public static String toString(Component input) {
		if (input == null)
			return null;
		return input.toString(); // Are you lazy or stupid?
	}

	public static Component fromString(String input) {
		if (input == null)
			return null;
		return ComponentAPI.fromString(input, /* Depends on version & software */ Ref.serverType().isBukkit() && Ref.isNewerThan(15), input.contains("http"));
	}

	public static Component fromString(String input, boolean hexMode) {
		if (input == null)
			return null;
		return ComponentAPI.fromString(input, hexMode ? Ref.serverType().isBukkit() && Ref.isNewerThan(15) : false, input.contains("http"));
	}

	public static Component fromString(String input, boolean hexMode, boolean urlMode) {
		if (input == null)
			return null;
		final Component start = new Component("");
		Component current = start;

		final List<Component> extra = new ArrayList<>();
		StringContainer builder = new StringContainer(input.length());
		char prev = 0;

		// REQUIRES hexMode ENABLED
		String hex = null;

		for (int i = 0; i < input.length(); ++i) {
			char c = input.charAt(i);

			// COLOR or FORMAT
			if (prev == '§') {
				prev = c;
				if (hexMode && c == 'x') {
					builder.deleteCharAt(builder.length() - 1); // Remove §
					hex = "#";
					continue;
				}
				// COLOR
				if (c >= 97 && c <= 102 || c >= 48 && c <= 57) { // a-f or 0-9
					if (hex != null) {
						hex += c;
						builder.deleteCharAt(builder.length() - 1); // Remove §
						if (hex.length() == 7) {
							current.setText(builder.toString()); // Current builder into text
							builder.clear(); // Clear builder
							current = new Component(); // Create new component
							extra.add(current);
							current.setColor(hex); // Set current format component to bold
							hex = null; // reset hex
						}
						continue;
					}
					builder.deleteCharAt(builder.length() - 1); // Remove §
					current.setText(builder.toString()); // Current builder into text
					if (current.getText().trim().isEmpty()) { // Just space or empty Component.. fast fix
						current.setColor(null);
						current.setFormatFromChar('r', false);
					}
					builder.clear(); // Clear builder
					current = new Component(); // Create new component
					extra.add(current);
					current.setColorFromChar(c);
					continue;
				}
				// FORMAT
				if (c >= 107 && c <= 111 || c == 114) {
					hex = null;
					builder.deleteCharAt(builder.length() - 1); // Remove §
					current.setText(builder.toString()); // Current builder into text
					builder.clear(); // Clear builder
					Component before = current;
					current = new Component().copyOf(before); // Create new component
					extra.add(current);
					current.setFormatFromChar(c, c != 114); // Set current format to 'true' or reset all
					continue;
				}
				// Is this bug?
			}
			prev = c;

			if (urlMode && c == ' ') {
				// URL

				String[] split = builder.toString().split(" ");

				if (ComponentAPI.checkHttp(split[split.length - 1])) {
					hex = null;
					current.setText(builder.toString().substring(0, builder.toString().length() - split[split.length - 1].length())); // Current builder into
					// text
					builder.clear(); // Clear builder
					Component before = current;
					if (!current.getText().trim().isEmpty()) {
						current = new Component().copyOf(before); // Create new component
						extra.add(current);
						current.setColor(before.getColor());
					}
					current.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, split[split.length - 1]));
					current.setText(split[split.length - 1]);

					current = new Component().copyOf(before); // Create new component
					extra.add(current);
					current.setColor(before.getColor());
				}
			}
			builder.append(c);
		}
		current.setText(builder.toString());
		if (urlMode) {

			String[] split = builder.toString().split(" ");

			if (ComponentAPI.checkHttp(split[split.length - 1])) {
				current.setText(builder.toString().substring(0, builder.toString().length() - split[split.length - 1].length())); // Current builder into text
				builder.clear(); // Clear builder
				Component before = current;
				current = new Component().copyOf(before); // Create new component
				extra.add(current);
				current.setColor(before.getColor());
				current.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, split[split.length - 1]));
				current.setText(split[split.length - 1]);
			}
		}
		start.setExtra(extra);
		return start;
	}

	private static boolean checkHttp(String text) {
		return ComponentAPI.url.matcher(text).find();
	}

	public static List<Map<String, Object>> toJsonList(Component component) {
		List<Map<String, Object>> list = new LinkedList<>();
		list.add(component.toJsonMap());
		if (component.getExtra() != null)
			ComponentAPI.toJsonListAll(list, component.getExtra());
		return list;
	}

	private static void toJsonListAll(List<Map<String, Object>> list, List<Component> extra) {
		for (Component c : extra) {
			list.add(c.toJsonMap());
			if (c.getExtra() != null)
				ComponentAPI.toJsonListAll(list, c.getExtra());
		}
	}

	public static List<Map<String, Object>> toJsonList(String text) {
		return ComponentAPI.toJsonList(ComponentAPI.fromString(text));
	}

	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> fixJsonList(List<Map<String, Object>> lists) { // usable for ex. chat format
		if (lists == null || lists.isEmpty())
			return lists;
		List<Map<String, Object>> list = new ArrayList<>();

		Iterator<Map<String, Object>> it = lists.iterator();
		while (it.hasNext()) {
			Map<String, Object> text = it.next();

			if (text.get("text") == null || text.get("text").toString().isEmpty())
				continue; // fast skip

			Map<String, Object> hover = convertMapValues("hoverEvent", (Map<String, Object>) text.get("hoverEvent"));
			Map<String, Object> click = convertMapValues("clickEvent", (Map<String, Object>) text.get("clickEvent"));
			String insertion = (String) text.get("insertion");

			String stext = text.get("text").toString();
			if (stext.contains("§")) {
				Component c = ComponentAPI.fromString(stext);
				while (c != null) {
					Map<String, Object> json = c.toJsonMap();
					if (hover != null)
						json.put("hoverEvent", hover);
					if (click != null && c.getClickEvent() == null) // Propably URL
						json.put("clickEvent", click);
					if (insertion != null)
						json.put("insertion", insertion);
					list.add(json);
				}
			} else {
				Component c = ComponentAPI.fromString(stext, false);
				while (c != null) {
					Map<String, Object> json = c.toJsonMap();
					if (hover != null)
						json.put("hoverEvent", hover);
					if (click != null && c.getClickEvent() == null) // Propably URL
						json.put("clickEvent", click);
					if (insertion != null)
						json.put("insertion", insertion);
					list.add(json);
				}
			}
			Object extra = text.get("extra");
			if (extra != null) {
				List<Map<String, Object>> extras = new ArrayList<>();
				if (extra instanceof Map)
					extras.addAll(fixJsonList(Arrays.asList((Map<String, Object>) extra)));
				else if (extra instanceof List)
					extras.addAll(fixJsonList((List<Map<String, Object>>) extra));
				list.get(list.size() - 1).put("extra", extras);
			}
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	public static String listToString(List<?> list) {
		StringContainer builder = new StringContainer(list.size() * 20);
		for (Object text : list)
			if (text instanceof Map)
				builder.append(ComponentAPI.getColor(((Map<String, Object>) text).get("color"))).append(String.valueOf(((Map<String, Object>) text).get("text")));
			else
				builder.append(StringUtils.colorize(text + ""));
		return builder.toString();
	}

	private static String getColor(Object color) {
		if (color == null)
			return "";
		if (color.toString().startsWith("#"))
			return StringUtils.color.replaceHex(color.toString());
		return "§" + Component.colorToChar(color.toString());
	}

	private static Map<String, Object> convertMapValues(String key, Map<String, Object> hover) {
		if (hover == null || hover.isEmpty())
			return null;
		Object val = hover.getOrDefault("value", hover.getOrDefault("content", hover.getOrDefault("contents", null)));
		if (val == null)
			hover.put("value", "");
		else if (key.equalsIgnoreCase("hoverEvent")) {
			if (val instanceof Collection || val instanceof Map) {
				Object ac = hover.get("action");
				hover.clear();
				hover.put("action", ac);
				hover.put("value", val);
			} else {
				Object ac = hover.get("action");
				hover.clear();
				hover.put("action", ac);
				hover.put("value", ComponentAPI.toJsonList(val + ""));
			}
		} else if (val instanceof Collection || val instanceof Map) {
			Object ac = hover.get("action");
			hover.clear();
			hover.put("action", ac);
			hover.put("value", val);
		} else {
			Object ac = hover.get("action");
			hover.clear();
			hover.put("action", ac);
			hover.put("value", val + "");
		}
		return hover;
	}
}
