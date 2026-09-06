package me.devtec.shared.dataholder.loaders;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public final class LoaderWriteUtil {
	private LoaderWriteUtil() {
	}

	public static void writeIterator(File file, Iterator<CharSequence> it) throws IOException {
		try (Writer w = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
			while (it.hasNext())
				w.append(it.next());
		}
	}
}