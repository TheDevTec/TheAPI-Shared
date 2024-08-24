package me.devtec.shared.placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderAPI {
	public static final Pattern placeholderLookup = Pattern.compile("\\%(.*?)\\%"); // %PLACEHOLDER_NAME%

	private static final List<PlaceholderExpansion> extensions = new ArrayList<>();
	public static Consumer<PlaceholderExpansion> registerConsumer;
	public static Consumer<PlaceholderExpansion> unregisterConsumer;
	public static PlaceholderExpansion PAPI_BRIDGE;

	public static List<PlaceholderExpansion> getPlaceholders() {
		return Collections.unmodifiableList(PlaceholderAPI.extensions);
	}

	public static void register(PlaceholderExpansion ext) {
		PlaceholderAPI.unregister(ext); // Unregister placeholders with same name
		if (registerConsumer != null)
			registerConsumer.accept(ext);
		PlaceholderAPI.extensions.add(ext);
	}

	public static void unregister(PlaceholderExpansion ext) {
		PlaceholderAPI.extensions.remove(ext);
		// Lookup for same names
		Iterator<PlaceholderExpansion> iterator = PlaceholderAPI.extensions.iterator();
		while (iterator.hasNext()) {
			PlaceholderExpansion reg = iterator.next();
			if (reg.getName().equalsIgnoreCase(ext.getName())) {
				if (unregisterConsumer != null)
					unregisterConsumer.accept(ext);
				iterator.remove();
			}
		}
	}

	public static String apply(String original, UUID player) {
		String text = original;
		Matcher match = PlaceholderAPI.placeholderLookup.matcher(text);
		while (match.find()) {
			String placeholder = match.group(1);
            for (PlaceholderExpansion ext : PlaceholderAPI.extensions) {
                if (placeholder.startsWith(ext.getName().toLowerCase() + "_")) {
                    String value = ext.apply(placeholder, player);
                    if (value != null && !value.equals(placeholder))
                        text = text.replace(match.group(), value);
                }
            }
			if (PlaceholderAPI.PAPI_BRIDGE != null) {
				String value = PlaceholderAPI.PAPI_BRIDGE.apply(placeholder, player);
				if (value != null && !value.equals(placeholder))
					text = text.replace(match.group(), value);
			}
		}
		return text;
	}

	public static List<String> apply(List<String> text, UUID player) {
		ListIterator<String> list = text.listIterator();
		while (list.hasNext()) {
			String val = list.next();
			list.set(PlaceholderAPI.apply(val, player));
		}
		return text;
	}

	public static PlaceholderExpansion getExpansion(String extensionName) {
        for (PlaceholderExpansion reg : PlaceholderAPI.extensions) {
            if (reg.getName().equalsIgnoreCase(extensionName))
                return reg;
        }
		return null;
	}

	public static boolean isRegistered(String extensionName) {
        for (PlaceholderExpansion reg : PlaceholderAPI.extensions) {
            if (reg.getName().equalsIgnoreCase(extensionName))
                return true;
        }
		return false;
	}

	public static void unregister(String extensionName) {
        PlaceholderAPI.extensions.removeIf(reg -> reg.getName().equalsIgnoreCase(extensionName));
	}

	public static void unregisterAll() {
		if (!extensions.isEmpty())
			for (PlaceholderExpansion exp : new ArrayList<>(getPlaceholders()))
				exp.unregister();
	}
}
