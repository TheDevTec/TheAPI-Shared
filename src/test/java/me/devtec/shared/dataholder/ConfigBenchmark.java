package me.devtec.shared.dataholder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Repeatable process benchmark, also executable against the captured original
 * JAR.
 */
public final class ConfigBenchmark {
	private static volatile long blackhole;

	public static void main(String[] args) throws Exception {
		if (args.length == 0 || "small".equals(args[0])) {
			small(1024, 3000);
			small(100 * 1024, 100);
			return;
		}
		File source = new File(args[1]);
		long target = Long.parseLong(args[2]);
		String shape = args.length > 3 ? args[3] : "nested";
		if ("generate".equals(args[0])) {
			generate(source, target, shape);
			return;
		}
		long start = System.nanoTime();
		long gc = gcMillis();
		try (Config c = Config.loadFromFile(source)) {
			long loaded = System.nanoTime();
			if (c.getKeys().isEmpty())
				throw new AssertionError("Load failed");
			File output = new File(source.getParentFile(), source.getName() + ".json");
			c.setFile(output);
			c.save(DataType.JSON);
			if (c.isModified() || !output.exists())
				throw new AssertionError("Save failed");
			long saved = System.nanoTime();
			System.out.printf(Locale.ROOT,
					"sourceBytes=%d outputBytes=%d loadMs=%.1f saveMs=%.1f gcMs=%d peakHeap=%d%n", source.length(),
					output.length(), (loaded - start) / 1e6, (saved - loaded) / 1e6, gcMillis() - gc, peakHeap());
		}
	}

	private static void small(int size, int count) {
		StringContainer b = new StringContainer();
		for (int i = 0; b.length() < size; i++)
			b.append("entry").append(i).append(":\n  a: 123\n  b: text value\n");
		String input = b.toString();
		for (int i = 0; i < 100; i++)
			try (Config c = Config.loadFromString(input)) {
				blackhole += c.toString(DataType.YAML).length();
			}
		long start = System.nanoTime();
		for (int i = 0; i < count; i++)
			try (Config c = Config.loadFromString(input)) {
				blackhole += c.toString(DataType.YAML).length();
			}
		System.out.printf(Locale.ROOT, "small bytes=%d nsPerLoadSave=%.0f iterations=%d%n", input.length(),
				(System.nanoTime() - start) / (double) count, count);
	}

	private static long peakHeap() {
		long n = 0;
		for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans())
			if (p.getType() == MemoryType.HEAP)
				n += p.getPeakUsage().getUsed();
		return n;
	}

	private static long gcMillis() {
		long n = 0;
		for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans())
			n += gc.getCollectionTime();
		return n;
	}

	private static void generate(File file, long target, String shape) throws IOException {
		char[] payload = new char[16384];
		Arrays.fill(payload, 'x');
		String text = new String(payload);
		long bytes = 0;
		int i = 0;
		try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8),
				1024 * 1024)) {
			if (shape != null) {
				switch (shape) {
				case "text":
					w.write("giant: \"");
					while (bytes < target) {
						w.write(text);
						bytes += text.length();
					}
					w.write("\"\n");
					break;
				case "list":
					w.write("giant:\n");
					while (bytes < target) {
						w.write("- \"");
						w.write(text);
						w.write("\"\n");
						bytes += text.length() + 5;
					}
					break;
				case "multiline":
					w.write("giant: |\n");
					while (bytes < target) {
						w.write("  ");
						w.write(text);
						w.write('\n');
						bytes += text.length() + 3;
					}
					break;
				default:
					while (bytes < target) {
						String header = "entry" + i++ + ":\n  level1:\n    level2:\n      value: ";
						w.write(header);
						w.write(text);
						w.write('\n');
						bytes += header.length() + text.length() + 1;
					}
					break;
				}
			} else
				while (bytes < target) {
					String header = "entry" + i++ + ":\n  level1:\n    level2:\n      value: ";
					w.write(header);
					w.write(text);
					w.write('\n');
					bytes += header.length() + text.length() + 1;
				}
		}
		System.out.println("generated " + file.length() + " bytes " + shape);
	}
}
