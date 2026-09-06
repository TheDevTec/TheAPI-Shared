package me.devtec.shared.dataholder.store;

import java.io.IOException;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Java 8 phantom cleanup; lazy references keep their immutable generation
 * alive.
 */
final class DiskGeneration {
	private static final ReferenceQueue<Object> QUEUE = new ReferenceQueue<>();
	private static final Set<Cleanup> REFERENCES = Collections.synchronizedSet(new HashSet<Cleanup>());
	static {
		Thread t = new Thread((Runnable) () -> {
			for (;;)
				try {
					Cleanup c = (Cleanup) QUEUE.remove();
					c.clean();
					REFERENCES.remove(c);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
		}, "Config-temp-cleanup");
		t.setDaemon(true);
		t.start();
	}

	private static final class Cleanup extends PhantomReference<Object> {
		final Path directory;
		final List<PagedFile> files;

		Cleanup(Object owner, Path dir, List<PagedFile> f) {
			super(owner, QUEUE);
			directory = dir;
			files = f;
		}

		void clean() {
			for (PagedFile f : files)
				try {
					f.close();
				} catch (IOException e) {
					System.err.println("Config close: " + e.getMessage());
				}
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
				for (Path p : stream)
					Files.deleteIfExists(p);
			} catch (IOException e) {
				System.err.println("Config cleanup: " + e.getMessage());
			}
			try {
				Files.deleteIfExists(directory);
			} catch (IOException e) {
				System.err.println("Config cleanup: " + e.getMessage());
			}
			clear();
		}
	}

	final Path directory;
	final List<PagedFile> files = new ArrayList<>();
	private final Cleanup cleanup;
	private boolean external;

	DiskGeneration() throws IOException {
		directory = TempStoragePolicy.createDirectory();
		cleanup = new Cleanup(this, directory, files);
		REFERENCES.add(cleanup);
	}

	PagedFile file(String name, int pages) throws IOException {
		PagedFile f = new PagedFile(directory.resolve(name), pages);
		files.add(f);
		return f;
	}

	void external() {
		external = true;
	}

	void release() {
		if (!external) {
			cleanup.clean();
			REFERENCES.remove(cleanup);
		}
	}

	void abort() {
		cleanup.clean();
		REFERENCES.remove(cleanup);
	}
}