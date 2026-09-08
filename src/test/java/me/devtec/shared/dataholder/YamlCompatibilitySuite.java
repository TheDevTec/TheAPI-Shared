package me.devtec.shared.dataholder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Executable against the original JAR as well as the replacement. */
public final class YamlCompatibilitySuite {
	public static final String[] YAML = { "a: hello\nb: 'true'\nc: \"00123\"\nd: \"a\\nb\\t\\\"c\"\n",
			"empty:\nnil: null\nyes: true\nno: false\ni: 1\nl: 2147483648\nf: 1.0\nneg: -2\nz: -0\ne: 1e3\nbig: 999999999999999999999999999999\n",
			"root:\n  child:\n    a: 1\n    b: two\nempty:\n",
			"a:\n- 1\n- 'true'\n- false\nb: [1, 2, \"three\"]\nc: {\"x\": 1}\n",
			"a:\n  - name: one\n    count: 2\n  - name: two\n    items:\n    - 3\n    - 4\n",
			"a: |\n  first\n  second\nb: >\n  third\n  fourth\nc: |-\n  fifth\n",
			"# header\n\n# before\na: 1 # inline\n\n# section\ns:\n  # child\n  b: 2\n\n# footer\n",
			"žluťoučký: příliš\nemoji: 😀\n'key: colon': value\n'key#hash': x\n'foo.bar': 3\n",
			"a: 1\nb: 2\n# replacement\na: 3 # last\n", "a: &x [1, 2]\nb: *x\nc: !!str 123\n",
			"a: 'it''s good'\nb: text #comment\nc: \"a#b:c\"\n", "a: []\nb: {}\nc: [true, null, {\"x\": [1, 2]}]\n" };

	public static String value(Object o) {
		if (o == null)
			return "null";
		if (o instanceof Map) {
			List<String> s = new ArrayList<>();
			for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet())
				s.add(value(e.getKey()) + "=" + value(e.getValue()));
			Collections.sort(s);
			return "map" + s;
		}
		if (o instanceof Collection) {
			List<String> s = new ArrayList<>();
			for (Object v : (Collection<?>) o)
				s.add(value(v));
			return "list" + s;
		}
		return o.getClass().getSimpleName() + ":"
				+ o.toString().replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
	}

	public static String snapshot() {
		StringContainer b = new StringContainer();
		for (int i = 0; i < YAML.length; i++)
			try (Config c = Config.loadFromString(YAML[i])) {
				b.append("CASE ").append(i).append('\n');
				b.append("header ").append(value(c.getHeader())).append('\n');
				for (String k : c.getKeys(true))
					b.append(k).append(" | ").append(value(c.get(k))).append(" | ").append(value(c.getString(k)))
							.append(" | ").append(value(c.getComments(k))).append(" | ")
							.append(value(c.getCommentAfterValue(k))).append('\n');
				b.append("footer ").append(value(c.getFooter())).append('\n');
			}
		return b.toString();
	}

	public static String api() {
		List<String> lines = new ArrayList<>();
		for (Constructor<?> c : Config.class.getDeclaredConstructors())
			if (visible(c.getModifiers()))
				lines.add(c.toGenericString());
		for (Method m : Config.class.getDeclaredMethods())
			if (visible(m.getModifiers()) && !"getDataLoader".equals(m.getName()))
				lines.add(m.toGenericString());
		for (Field f : Config.class.getDeclaredFields())
			if (visible(f.getModifiers()))
				lines.add(f.toGenericString());
		Collections.sort(lines);
		return String.join("\n", lines) + "\n";
	}

	private static boolean visible(int m) {
		return Modifier.isPublic(m) || Modifier.isProtected(m);
	}

	public static void main(String[] args) throws Exception {
		Path p = Paths.get(args.length == 0 ? "src/test/resources/config" : args[0]);
		if (args.length > 1 && "record".equals(args[1])) {
			Files.createDirectories(p);
			Files.write(p.resolve("yaml-baseline.txt"), snapshot().getBytes(StandardCharsets.UTF_8));
			Files.write(p.resolve("api-baseline.txt"), api().getBytes(StandardCharsets.UTF_8));
		} else {
			String expected = new String(Files.readAllBytes(p.resolve("yaml-baseline.txt")), StandardCharsets.UTF_8);
			if (!expected.equals(snapshot()))
				throw new AssertionError("YAML compatibility difference\n" + snapshot());
			if (!new String(Files.readAllBytes(p.resolve("api-baseline.txt")), StandardCharsets.UTF_8).equals(api()))
				throw new AssertionError("Config API changed");
			System.out.println("Config API and YAML compatibility: PASS");
		}
	}
}
