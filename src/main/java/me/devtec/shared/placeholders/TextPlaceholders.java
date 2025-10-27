package me.devtec.shared.placeholders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.annotations.Nullable;
import me.devtec.shared.components.ComponentAPI;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.utility.ColorUtils;
import me.devtec.shared.utility.StringUtils;
import me.devtec.shared.utility.StringUtils.FormatType;

public class TextPlaceholders {
	public interface Replacement {
		String retrive(@Nullable UUID player);
	}

	private static final TextPlaceholders EMPTY = new TextPlaceholders() {
		@Override
		public TextPlaceholders add(String placeholder, Replacement replacement) {
			return this;
		}

		@Override
		public TextPlaceholders add(String placeholder, String replacement) {
			return this;
		}

		@Override
		public TextPlaceholders add(String placeholder, Number replacement) {
			return this;
		}

		@Override
		public boolean remove(String placeholder) {
			return false;
		}

		@Override
		public String replace(String text, UUID player) {
			return text;
		}

		@Override
		public StringContainer replace(StringContainer text, UUID player) {
			return text;
		}

		@Override
		public void setColorizeText(boolean colorizeText) {

		}

		@Override
		public boolean shouldColorizeText() {
			return false;
		}

		@Override
		public Object replaceAsJson(Object object, UUID player) {
			return object;
		}
	};

	private static final TextPlaceholders EMPTY_COLORIZED = new TextPlaceholders() {
		@Override
		public TextPlaceholders add(String placeholder, Replacement replacement) {
			return this;
		}

		@Override
		public TextPlaceholders add(String placeholder, String replacement) {
			return this;
		}

		@Override
		public TextPlaceholders add(String placeholder, Number replacement) {
			return this;
		}

		@Override
		public boolean remove(String placeholder) {
			return false;
		}

		@Override
		public String replace(String text, UUID player) {
			return ColorUtils.colorize(text, null);
		}

		@Override
		public StringContainer replace(StringContainer text, UUID player) {
			return ColorUtils.colorize(text, null);
		}

		@Override
		public void setColorizeText(boolean colorizeText) {

		}

		@Override
		public boolean shouldColorizeText() {
			return true;
		}
	};

	private Map<String, Replacement> placeholders = new HashMap<>();
	private Map<String, String> staticPlaceholders = new HashMap<>();
	private boolean colorizeText;

	public static TextPlaceholders create() {
		return new TextPlaceholders();
	}

	public static TextPlaceholders empty() {
		return EMPTY;
	}

	public static TextPlaceholders emptyColorized() {
		return EMPTY_COLORIZED;
	}

	public boolean shouldColorizeText() {
		return colorizeText;
	}

	public void setColorizeText(boolean colorizeText) {
		this.colorizeText = colorizeText;
	}

	public TextPlaceholders add(@Nonnull String placeholder, @Nonnull Replacement replacement) {
		Checkers.nonNull(placeholder, "placeholder");
		Checkers.nonNull(replacement, "replacement");
		staticPlaceholders.remove('{' + placeholder + '}');
		placeholders.put('{' + placeholder + '}', replacement);
		return this;
	}

	public TextPlaceholders add(@Nonnull String placeholder, @Nonnull String replacement) {
		Checkers.nonNull(placeholder, "placeholder");
		Checkers.nonNull(replacement, "replacement");
		placeholders.remove('{' + placeholder + '}');
		staticPlaceholders.put('{' + placeholder + '}', replacement);
		return this;
	}

	public TextPlaceholders add(@Nonnull String placeholder, @Nonnull Number replacement) {
		Checkers.nonNull(placeholder, "placeholder");
		Checkers.nonNull(replacement, "replacement");
		placeholders.remove('{' + placeholder + '}');
		staticPlaceholders.put('{' + placeholder + '}',
				StringUtils.formatDouble(FormatType.NORMAL, replacement.doubleValue()));
		return this;
	}

	public boolean remove(@Nonnull String placeholder) {
		Checkers.nonNull(placeholder, "placeholder");
		boolean removed = staticPlaceholders.remove('{' + placeholder + '}') != null;
		return placeholders.remove('{' + placeholder + '}') != null || removed;
	}

	public Set<String> getPlaceholders() {
		Set<String> set = new HashSet<>(staticPlaceholders.keySet());
		set.addAll(placeholders.keySet());
		return set;
	}

	@SuppressWarnings("unchecked")
	public Object replaceAsJson(Object object, @Nullable UUID player) {
		if (object instanceof Map) {
			Map<String, Object> map = new HashMap<>( ((Map<String, Object>) object).size());
			for (Entry<String, Object> entry : ((Map<String, Object>) object).entrySet())
				if ("color".equals(entry.getKey()))
					map.put(entry.getKey(), entry.getValue());
				else
					map.put(entry.getKey(),
							replaceAsJson(entry.getValue(), player, "hoverEvent".equals(entry.getKey())));
			return map;
		}
		if (object instanceof Collection) {
			List<Object> rewritten = new ArrayList<>(((Collection<?>) object).size());
			for (Object obj : (Collection<?>) object)
				rewritten.add(replaceAsJson(obj, player));
			return rewritten;
		}
		return object instanceof String ? replace(object.toString(), player) : object;
	}

	@SuppressWarnings("unchecked")
	private Object replaceAsJson(Object object, UUID player, boolean shouldConvertToComponent) {
		if (object instanceof Map) {
			Map<String, Object> map = new HashMap<>(((Map<String, Object>) object).size());
			for (Entry<String, Object> entry : ((Map<String, Object>) object).entrySet())
				if ("color".equals(entry.getKey()))
					map.put(entry.getKey(), entry.getValue());
				else
					map.put(entry.getKey(),
							replaceAsJson(entry.getValue(), player,
									shouldConvertToComponent
									? "value".equals(entry.getKey()) || "content".equals(entry.getKey())
											|| "contents".equals(entry.getKey())
											: false));
			return map;
		}
		if (object instanceof Collection) {
			List<Object> rewritten = new ArrayList<>(((Collection<?>) object).size());
			for (Object obj : (Collection<?>) object)
				rewritten.add(replaceAsJson(obj, player, shouldConvertToComponent));
			return rewritten;
		}
		if (object instanceof String)
			return shouldConvertToComponent
					? ComponentAPI.toJsonList(ComponentAPI.fromString(replace(object.toString(), player)))
							: replace(object.toString(), player);
		return object;
	}

	public List<String> replace(List<String> text, @Nullable UUID player) {
		text.replaceAll(line -> replace(line, player));
		return text;
	}

	public String replace(String text, @Nullable UUID player) {
		return replace(new StringContainer(text).replace("\\n", "\n"), player).toString();
	}

	public StringContainer replace(StringContainer text, @Nullable UUID player) {
		for (Entry<String, Replacement> entry : placeholders.entrySet()) {
			int index;
			int start = 0;
			String replacement = null;
			while (start < text.length() && (index = text.indexOf(entry.getKey(), start)) != -1) {
				if (replacement == null)
					try {
						replacement = entry.getValue().retrive(player);
					} catch (Exception e) {
						e.printStackTrace();
						break;
					}
				start = index + replacement.length();
				text.replace(index, index + entry.getKey().length(), replacement);
			}
		}
		for (Entry<String, String> entry : staticPlaceholders.entrySet()) {
			int index;
			int start = 0;
			String replacement = entry.getValue();
			while (start < text.length() && (index = text.indexOf(entry.getKey(), start)) != -1) {
				start = index + replacement.length();
				text.replace(index, index + entry.getKey().length(), replacement);
			}
		}
		return shouldColorizeText() ? ColorUtils.colorize(text, null) : text;
	}
}
