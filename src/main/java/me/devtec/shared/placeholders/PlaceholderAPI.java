package me.devtec.shared.placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import me.devtec.shared.dataholder.StringContainer;

public class PlaceholderAPI {

	public static final Pattern placeholderLookup =
			Pattern.compile("\\%(.*?)\\%");

	private static final List<PlaceholderExpansion> extensions = new ArrayList<>();

	/*
	 * Hot-path snapshot.
	 *
	 * Registrations happen mainly during onEnable(), while apply()
	 * may be called very frequently and also asynchronously.
	 */
	private static volatile PlaceholderExpansion[] extensionsArray = {};

	public static Consumer<PlaceholderExpansion> registerConsumer;
	public static Consumer<PlaceholderExpansion> unregisterConsumer;

	public static volatile PlaceholderExpansion PAPI_BRIDGE;

	public static List<PlaceholderExpansion> getPlaceholders() {
		return Collections.unmodifiableList(extensions);
	}

	public static void register(PlaceholderExpansion ext) {
		if (ext == null)
			return;

		unregister(ext);

		Consumer<PlaceholderExpansion> consumer =
				registerConsumer;

		if (consumer != null)
			consumer.accept(ext);

		extensions.add(ext);

		updateSnapshot();
	}

	public static void unregister(PlaceholderExpansion ext) {
		if (ext == null)
			return;

		boolean changed = extensions.remove(ext);

		String name = ext.getName();

		for (int i = extensions.size() - 1; i >= 0; --i) {
			PlaceholderExpansion registered =
					extensions.get(i);

			if (!equalsIgnoreCase(
					registered.getName(),
					name))
				continue;

			Consumer<PlaceholderExpansion> consumer =
					unregisterConsumer;

			/*
			 * Zachování původní semantiky.
			 */
			if (consumer != null)
				consumer.accept(ext);

			extensions.remove(i);
			changed = true;
		}

		if (changed)
			updateSnapshot();
	}

	private static void updateSnapshot() {
		extensionsArray =
				extensions.toArray(
						new PlaceholderExpansion[
						                         extensions.size()]);
	}

	public static String apply(
			String original,
			UUID player) {

		if (original == null
				|| original.length() < 3)
			return original;

		int length = original.length();

		int open = original.indexOf('%');

		if (open == -1
				|| open + 1 >= length)
			return original;

		/*
		 * Lazy output allocation.
		 *
		 * Dokud nenajdeme opravdu nahraditelný placeholder,
		 * nevytváříme StringContainer.
		 */
		StringContainer result = null;

		int copyFrom = 0;
		int searchFrom = open;

		while (searchFrom < length) {
			open = original.indexOf(
					'%',
					searchFrom);

			if (open == -1
					|| open + 1 >= length)
				break;

			int close = original.indexOf(
					'%',
					open + 1);

			if (close == -1)
				break;

			/*
			 * Empty %% is not useful as a placeholder.
			 */
			if (close == open + 1) {
				searchFrom = close + 1;
				continue;
			}

			String placeholder =
					original.substring(
							open + 1,
							close);

			String replacement =
					resolve(
							placeholder,
							player);

			if (replacement != null
					&& !replacement.equals(placeholder)) {

				if (result == null)
					result =
					new StringContainer(
							length + 16);

				if (open > copyFrom)
					result.append(
							original,
							copyFrom,
							open);

				result.append(replacement);

				copyFrom = close + 1;
			}

			searchFrom = close + 1;
		}

		if (result == null)
			return original;

		if (copyFrom < length)
			result.append(
					original,
					copyFrom,
					length);

		return result.toString();
	}

	private static String resolve(
			String placeholder,
			UUID player) {

		/*
		 * One volatile read.
		 *
		 * Potom celý lookup pracuje se stejným immutable
		 * snapshotem expansions.
		 */
		PlaceholderExpansion[] expansions =
				extensionsArray;

		for (PlaceholderExpansion ext : expansions) {
			if (!matchesExpansion(
					placeholder,
					ext.getName()))
				continue;

			String value =
					ext.apply(
							placeholder,
							player);

			if (value != null
					&& !value.equals(placeholder))
				return value;
		}

		PlaceholderExpansion bridge =
				PAPI_BRIDGE;

		if (bridge != null) {
			String value =
					bridge.apply(
							placeholder,
							player);

			if (value != null
					&& !value.equals(placeholder))
				return value;
		}

		return null;
	}

	private static boolean matchesExpansion(
			String placeholder,
			String expansionName) {

		if (expansionName == null)
			return false;

		int nameLength =
				expansionName.length();

		if (placeholder.length() <= nameLength
				|| placeholder.charAt(nameLength) != '_')
			return false;

		for (int i = 0; i < nameLength; ++i) {
			char expected =
					expansionName.charAt(i);

			char actual =
					placeholder.charAt(i);

			if (expected == actual)
				continue;

			if (expected >= 'A'
					&& expected <= 'Z')
				expected =
				(char) (expected + 32);

			/*
			 * Zachováváme původní:
			 *
			 * placeholder.startsWith(
			 *     ext.getName().toLowerCase() + "_")
			 *
			 * Tzn. lowercase se aplikovalo pouze na jméno
			 * expansionu, nikoli na placeholder.
			 */
			if (expected != actual)
				return false;
		}

		return true;
	}

	public static List<String> apply(
			List<String> text,
			UUID player) {

		if (text == null || text.isEmpty())
			return text;

		text.replaceAll(
				line -> apply(line, player));

		return text;
	}

	public static PlaceholderExpansion getExpansion(
			String extensionName) {

		if (extensionName == null)
			return null;

		PlaceholderExpansion[] expansions =
				extensionsArray;

		for (PlaceholderExpansion registered : expansions)
			if (equalsIgnoreCase(
					registered.getName(),
					extensionName))
				return registered;

		return null;
	}

	public static boolean isRegistered(
			String extensionName) {

		return getExpansion(extensionName) != null;
	}

	public static void unregister(
			String extensionName) {

		if (extensionName == null)
			return;

		boolean changed = false;

		for (int i = extensions.size() - 1;
				i >= 0;
				--i) {

			PlaceholderExpansion registered =
					extensions.get(i);

			if (!equalsIgnoreCase(
					registered.getName(),
					extensionName))
				continue;

			extensions.remove(i);
			changed = true;
		}

		if (changed)
			updateSnapshot();
	}

	public static void unregisterAll() {
		if (extensions.isEmpty())
			return;

		/*
		 * exp.unregister() může modifikovat extensions,
		 * takže cold-path snapshot zde dává smysl.
		 */
		PlaceholderExpansion[] snapshot =
				extensionsArray;

		for (PlaceholderExpansion expansion : snapshot)
			expansion.unregister();
	}

	private static boolean equalsIgnoreCase(
			String first,
			String second) {

		if (first == second)
			return true;

		if (first == null
				|| second == null
				|| first.length() != second.length())
			return false;

		for (int i = 0,
				length = first.length();
				i < length;
				++i) {

			char a = first.charAt(i);
			char b = second.charAt(i);

			if (a == b)
				continue;

			if (a >= 'A' && a <= 'Z')
				a = (char) (a + 32);

			if (b >= 'A' && b <= 'Z')
				b = (char) (b + 32);

			if (a != b)
				return false;
		}

		return true;
	}
}