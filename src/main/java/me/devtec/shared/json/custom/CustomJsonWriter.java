package me.devtec.shared.json.custom;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.json.JWriter;

public final class CustomJsonWriter implements JWriter {

	private static final String NULL = "null";

	@Override
	public String toGson(Object object) {
		return toJson(object);
	}

	public static String toJson(Object object) {
		StringContainer container = new StringContainer();
		writeValue(object, container);
		return container.toString();
	}

	private static void writeValue(Object object, StringContainer container) {
		if (object == null) {
			container.append(NULL);
			return;
		}

		if (object instanceof CharSequence) {
			writeString((CharSequence) object, container);
			return;
		}

		if (object instanceof Character) {
			writeString(String.valueOf(object), container);
			return;
		}

		if (object instanceof Boolean) {
			container.append(String.valueOf(object));
			return;
		}

		if (object instanceof Number) {
			writeNumber((Number) object, container);
			return;
		}

		if (object instanceof Map) {
			writeMap((Map<?, ?>) object, container);
			return;
		}

		if (object instanceof Collection) {
			writeCollection((Collection<?>) object, container);
			return;
		}

		Class<?> type = object.getClass();

		if (type.isArray()) {
			writeArray(object, container);
			return;
		}

		writeString(String.valueOf(object), container);
	}

	private static void writeNumber(Number number, StringContainer container) {
		if (number instanceof Double) {
			double value = number.doubleValue();

			if (Double.isNaN(value) || Double.isInfinite(value)) {
				container.append(NULL);
				return;
			}
		} else if (number instanceof Float) {
			float value = number.floatValue();

			if (Float.isNaN(value) || Float.isInfinite(value)) {
				container.append(NULL);
				return;
			}
		}

		container.append(String.valueOf(number));
	}

	private static void writeArray(Object array, StringContainer container) {
		container.append(CustomJsonReader.OPEN_BRACKET);

		int length = Array.getLength(array);

		for (int i = 0; i < length; ++i) {
			if (i != 0)
				container.append(CustomJsonReader.COMMA);

			writeValue(Array.get(array, i), container);
		}

		container.append(CustomJsonReader.CLOSED_BRACKET);
	}

	private static void writeCollection(Collection<?> collection, StringContainer container) {
		container.append(CustomJsonReader.OPEN_BRACKET);

		boolean first = true;

		for (Object value : collection) {
			if (!first)
				container.append(CustomJsonReader.COMMA);
			else
				first = false;

			writeValue(value, container);
		}

		container.append(CustomJsonReader.CLOSED_BRACKET);
	}

	private static void writeMap(Map<?, ?> map, StringContainer container) {
		container.append(CustomJsonReader.OPEN_BRACE);

		boolean first = true;

		for (Entry<?, ?> entry : map.entrySet()) {
			if (!first)
				container.append(CustomJsonReader.COMMA);
			else
				first = false;

			/*
			 * JSON object keys are always strings.
			 */
			writeString(String.valueOf(entry.getKey()), container);

			container.append(CustomJsonReader.COLON);

			writeValue(entry.getValue(), container);
		}

		container.append(CustomJsonReader.CLOSED_BRACE);
	}

	private static void writeString(CharSequence value, StringContainer container) {
		if (value == null) {
			container.append(NULL);
			return;
		}

		container.append(CustomJsonReader.QUOTES);

		int length = value.length();

		for (int i = 0; i < length; ++i) {
			char character = value.charAt(i);

			switch (character) {
			case '"':
				container.append('\\').append('"');
				break;

			case '\\':
				container.append('\\').append('\\');
				break;

			case '\b':
				container.append('\\').append('b');
				break;

			case '\f':
				container.append('\\').append('f');
				break;

			case '\n':
				container.append('\\').append('n');
				break;

			case '\r':
				container.append('\\').append('r');
				break;

			case '\t':
				container.append('\\').append('t');
				break;

			default:
				if (character < 0x20)
					writeUnicode(character, container);
				else
					container.append(character);
				break;
			}
		}

		container.append(CustomJsonReader.QUOTES);
	}

	private static void writeUnicode(char character, StringContainer container) {
		container.append('\\').append('u');

		String hex = Integer.toHexString(character);

		for (int i = hex.length(); i < 4; ++i)
			container.append('0');

		container.append(hex);
	}

	public static String parseToString(String string) {
		if (string == null)
			return NULL;

		StringContainer container = new StringContainer(string.length() + 16);
		writeString(string, container);

		return container.toString();
	}

	@Override
	public String toString() {
		return "CustomJsonWriter";
	}
}