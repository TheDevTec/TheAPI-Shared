package me.devtec.shared.dataholder;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

import me.devtec.shared.dataholder.loaders.DataLoader;

/** Standalone diagnostic, not run by the unit test suite. Arguments: input.json [rounds]. */
public final class ConfigFilePathDiagnostic {
	private static volatile long consumed;

	public static void main(String[] args) throws Exception {
		if (args.length == 0) throw new IllegalArgumentException("input.json [rounds]");
		Path source = Paths.get(args[0]).toAbsolutePath();
		if (Files.size(source) > 16 * 1024 * 1024) throw new IllegalArgumentException("Diagnostic input limit: 16 MiB");
		byte[] bytes = Files.readAllBytes(source);
		int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 30;
		if (rounds < 1) throw new IllegalArgumentException("rounds must be positive");
		long[][] samples = new long[6][rounds];
		// Both paths read identical bytes immediately after writing the SAME file.
		Path file = Files.createTempFile(source.getParent(), "config-io-diagnostic-", ".json");
		try {
			for (int run = -10; run < rounds; run++) {
				for (int order = 0; order < 2; order++) {
					boolean fullLoad = ((run + order) & 1) == 0;
					Files.write(file, bytes);
					if (fullLoad) {
						long start = System.nanoTime();
						Config config = Config.loadFromFile(file.toFile());
						long elapsed = System.nanoTime() - start;
						try {
							if (!config.getDataLoader().isLoaded()) throw new IllegalStateException("File load failed", DataLoader.lastLoadError);
							consumed += config.getDataLoader().document().storage.store().size();
							if (run >= 0) samples[0][run] = elapsed;
						} finally { config.close(); }
					} else {
						long start = System.nanoTime();
						long size = file.toFile().length();
						long metadataEnd = System.nanoTime();
						byte[] input = Files.readAllBytes(file);
						long readEnd = System.nanoTime();
						String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
								.decode(ByteBuffer.wrap(input)).toString();
						long decodeEnd = System.nanoTime();
						DataLoader loader = DataLoader.findLoaderFor(text);
						long parseEnd = System.nanoTime();
						try {
							if (!loader.isLoaded()) throw new IllegalStateException("String load failed", DataLoader.lastLoadError);
							consumed += size + loader.document().storage.store().size();
							if (run >= 0) {
								samples[1][run] = metadataEnd - start;
								samples[2][run] = readEnd - metadataEnd;
								samples[3][run] = decodeEnd - readEnd;
								samples[4][run] = parseEnd - decodeEnd;
								samples[5][run] = parseEnd - start;
							}
						} finally { loader.document().close(); }
					}
				}
			}
		} finally { Files.deleteIfExists(file); }
		String[] labels = {"FULL LOAD AFTER WRITE", "FILE LENGTH", "RAW READ AFTER WRITE", "UTF8 DECODE TO STRING", "PARSE STRING", "SPLIT PATH TOTAL"};
		for (int i = 0; i < labels.length; i++) {
			Arrays.sort(samples[i]);
			double median = rounds % 2 == 0 ? (samples[i][rounds / 2 - 1] / 2.0 + samples[i][rounds / 2] / 2.0) : samples[i][rounds / 2];
			System.out.printf(Locale.ROOT, "%s median=%.2f us%n", labels[i], median / 1000.0);
		}
	}
}
