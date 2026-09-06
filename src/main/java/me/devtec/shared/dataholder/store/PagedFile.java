package me.devtec.shared.dataholder.store;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Bounded write-back cache; disk grows only to the last byte actually written.
 *
 * Array-backed cache: - no LinkedHashMap - no Long boxing - no Map.Entry
 * allocations - bounded fixed number of Page objects
 */
final class PagedFile implements Closeable {

	static final int PAGE = 16384;

	private static final long EMPTY_KEY = Long.MIN_VALUE;

	private final FileChannel channel;
	private final int maxPages;

	private long length;

	/*
	 * Resident slots.
	 */
	private final long[] pageIndexes;
	private final Page[] pages;

	/*
	 * Intrusive LRU list.
	 *
	 * head = least recently used tail = most recently used
	 */
	private final int[] prev;
	private final int[] next;

	private int head = -1;
	private int tail = -1;
	private int size;

	/*
	 * Open-addressed hash table:
	 *
	 * hash slot value: 0 = empty n + 1 = resident slot n
	 */
	private final int[] hashTable;
	private final int hashMask;

	private static final class Page {
		final byte[] bytes = new byte[PAGE];
		boolean dirty;
	}

	PagedFile(Path file, int pages) throws IOException {
		channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ,
				StandardOpenOption.WRITE);

		maxPages = Math.max(1, pages);

		pageIndexes = new long[maxPages];
		Arrays.fill(pageIndexes, EMPTY_KEY);

		this.pages = new Page[maxPages];

		prev = new int[maxPages];
		next = new int[maxPages];

		Arrays.fill(prev, -1);
		Arrays.fill(next, -1);

		int hashSize = 1;

		/*
		 * <= 50% load factor.
		 */
		while (hashSize < maxPages * 2)
			hashSize <<= 1;

		hashTable = new int[hashSize];
		hashMask = hashSize - 1;
	}

	long length() {
		return length;
	}

	long cacheBytes() {
		return (long) size * PAGE;
	}

	private static int mix(long value) {
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdL;
		value ^= value >>> 33;

		return (int) value;
	}

	private int findSlot(long index) {
		int hash = mix(index) & hashMask;

		while (true) {
			int value = hashTable[hash];

			if (value == 0)
				return -1;

			int slot = value - 1;

			if (pageIndexes[slot] == index)
				return slot;

			hash = hash + 1 & hashMask;
		}
	}

	private void hashInsert(long index, int slot) {
		int hash = mix(index) & hashMask;

		while (hashTable[hash] != 0)
			hash = hash + 1 & hashMask;

		hashTable[hash] = slot + 1;
	}

	private void hashRemove(long index) {
		int hash = mix(index) & hashMask;

		while (true) {
			int value = hashTable[hash];

			if (value == 0)
				return;

			int slot = value - 1;

			if (pageIndexes[slot] == index)
				break;

			hash = hash + 1 & hashMask;
		}

		/*
		 * Remove entry, then reinsert the following cluster.
		 */
		hashTable[hash] = 0;

		int scan = hash + 1 & hashMask;

		while (hashTable[scan] != 0) {
			int slot = hashTable[scan] - 1;

			hashTable[scan] = 0;

			hashInsert(pageIndexes[slot], slot);

			scan = scan + 1 & hashMask;
		}
	}

	private void unlinkLru(int slot) {
		int p = prev[slot];
		int n = next[slot];

		if (p == -1)
			head = n;
		else
			next[p] = n;

		if (n == -1)
			tail = p;
		else
			prev[n] = p;

		prev[slot] = -1;
		next[slot] = -1;
	}

	private void appendLru(int slot) {
		prev[slot] = tail;
		next[slot] = -1;

		if (tail == -1)
			head = slot;
		else
			next[tail] = slot;

		tail = slot;
	}

	private void touch(int slot) {
		if (tail == slot)
			return;

		unlinkLru(slot);
		appendLru(slot);
	}

	private int freeSlot() {
		/*
		 * Cache not full: first unused slot.
		 *
		 * Usually simply slot == size because slots remain compact until the cache
		 * fills.
		 */
		if (size < maxPages)
			for (int i = 0; i < maxPages; ++i)
				if (pages[i] == null)
					return i;

		return -1;
	}

	private Page page(long index) throws IOException {
		int slot = findSlot(index);

		if (slot >= 0) {
			touch(slot);
			return pages[slot];
		}

		if (size < maxPages) {

			slot = freeSlot();

			Page page = new Page();

			pages[slot] = page;

			pageIndexes[slot] = index;

			++size;

		} else {

			/*
			 * Reuse the LRU slot itself.
			 *
			 * No new Page allocation after the cache reaches maxPages.
			 */
			slot = head;

			long oldIndex = pageIndexes[slot];

			Page oldPage = pages[slot];

			write(oldIndex, oldPage);

			hashRemove(oldIndex);
			unlinkLru(slot);

			pageIndexes[slot] = index;

			/*
			 * Clear reused page because data beyond file content must remain zero-filled.
			 */
			Arrays.fill(oldPage.bytes, (byte) 0);

			oldPage.dirty = false;
		}

		Page page = pages[slot];

		long pos = index * PAGE;

		int count = (int) Math.min(PAGE, Math.max(0, Math.min(channel.size(), length) - pos));

		if (count > 0) {
			ByteBuffer buffer = ByteBuffer.wrap(page.bytes, 0, count);

			int stalls = 0;

			while (buffer.hasRemaining()) {
				int read = channel.read(buffer, pos + buffer.position());

				if (read < 0)
					throw new EOFException("Truncated Config page");

				if (read == 0) {
					if (++stalls > 100)
						throw new IOException("Config page read stalled");
				} else
					stalls = 0;
			}
		}

		hashInsert(index, slot);

		appendLru(slot);

		return page;
	}

	private void write(long index, Page page) throws IOException {

		if (!page.dirty)
			return;

		long pos = index * PAGE;

		int size = (int) Math.min(PAGE, length - pos);

		if (size <= 0) {
			page.dirty = false;
			return;
		}

		ByteBuffer buffer = ByteBuffer.wrap(page.bytes, 0, size);

		int stalls = 0;

		while (buffer.hasRemaining()) {
			int written = channel.write(buffer, pos + buffer.position());

			if (written == 0) {
				if (++stalls > 100)
					throw new IOException("Config page write stalled");
			} else
				stalls = 0;
		}

		page.dirty = false;
	}

	void read(long pos, byte[] target, int off, int len) throws IOException {

		if (pos < 0 || len < 0 || pos > length - len)
			throw new EOFException("Invalid Config read bounds");

		while (len > 0) {
			int in = (int) (pos % PAGE);

			int amount = Math.min(len, PAGE - in);

			Page page = page(pos / PAGE);

			System.arraycopy(page.bytes, in, target, off, amount);

			pos += amount;
			off += amount;
			len -= amount;
		}
	}

	void write(long pos, byte[] source, int off, int len) throws IOException {

		if (pos < 0 || len < 0 || pos > Long.MAX_VALUE - len || pos > length)
			throw new IOException("Invalid/non-contiguous Config write bounds");

		final long end = pos + len;

		while (len > 0) {
			int in = (int) (pos % PAGE);

			int amount = Math.min(len, PAGE - in);

			Page page = page(pos / PAGE);

			System.arraycopy(source, off, page.bytes, in, amount);

			page.dirty = true;

			pos += amount;
			off += amount;
			len -= amount;

			if (pos > length)
				length = pos;
		}

		if (end > length)
			length = end;
	}

	long getLong(long pos) throws IOException {
		if (pos < 0 || pos > length - 8)
			throw new EOFException();

		int in = (int) (pos % PAGE);

		if (in <= PAGE - 8) {
			byte[] bytes = page(pos / PAGE).bytes;

			return (long) (bytes[in] & 255) << 56 | (long) (bytes[in + 1] & 255) << 48
					| (long) (bytes[in + 2] & 255) << 40 | (long) (bytes[in + 3] & 255) << 32
					| (long) (bytes[in + 4] & 255) << 24 | (long) (bytes[in + 5] & 255) << 16
					| (long) (bytes[in + 6] & 255) << 8 | bytes[in + 7] & 255;
		}

		/*
		 * Cross-page case only.
		 */
		byte[] bytes = new byte[8];

		read(pos, bytes, 0, 8);

		return (long) (bytes[0] & 255) << 56 | (long) (bytes[1] & 255) << 48 | (long) (bytes[2] & 255) << 40
				| (long) (bytes[3] & 255) << 32 | (long) (bytes[4] & 255) << 24 | (long) (bytes[5] & 255) << 16
				| (long) (bytes[6] & 255) << 8 | bytes[7] & 255;
	}

	void putLong(long pos, long value) throws IOException {

		if (pos < 0 || pos > Long.MAX_VALUE - 8 || pos > length)
			throw new IOException("Invalid/non-contiguous Config write bounds");

		int in = (int) (pos % PAGE);

		/*
		 * Important fast path:
		 *
		 * no new byte[8] no generic write()
		 */
		if (in <= PAGE - 8) {
			Page page = page(pos / PAGE);

			byte[] bytes = page.bytes;

			bytes[in] = (byte) (value >>> 56);

			bytes[in + 1] = (byte) (value >>> 48);

			bytes[in + 2] = (byte) (value >>> 40);

			bytes[in + 3] = (byte) (value >>> 32);

			bytes[in + 4] = (byte) (value >>> 24);

			bytes[in + 5] = (byte) (value >>> 16);

			bytes[in + 6] = (byte) (value >>> 8);

			bytes[in + 7] = (byte) value;

			page.dirty = true;

			long end = pos + 8;

			if (end > length)
				length = end;

			return;
		}

		/*
		 * Very rare cross-page write.
		 */
		byte[] bytes = new byte[8];

		bytes[0] = (byte) (value >>> 56);
		bytes[1] = (byte) (value >>> 48);
		bytes[2] = (byte) (value >>> 40);
		bytes[3] = (byte) (value >>> 32);
		bytes[4] = (byte) (value >>> 24);
		bytes[5] = (byte) (value >>> 16);
		bytes[6] = (byte) (value >>> 8);
		bytes[7] = (byte) value;

		write(pos, bytes, 0, 8);
	}

	int getByte(long pos) throws IOException {
		if (pos < 0 || pos >= length)
			throw new EOFException();

		return page(pos / PAGE).bytes[(int) (pos % PAGE)] & 255;
	}

	void putByte(long pos, int value) throws IOException {

		if (pos < 0 || pos > length)
			throw new IOException("Invalid/non-contiguous Config write bounds");

		Page page = page(pos / PAGE);

		page.bytes[(int) (pos % PAGE)] = (byte) value;

		page.dirty = true;

		long end = pos + 1;

		if (end > length)
			length = end;
	}

	void flush() throws IOException {
		for (int i = 0; i < maxPages; ++i) {
			Page page = pages[i];

			if (page != null)
				write(pageIndexes[i], page);
		}
	}

	@Override
	public void close() throws IOException {
		Arrays.fill(pages, null);

		Arrays.fill(pageIndexes, EMPTY_KEY);

		Arrays.fill(hashTable, 0);

		size = 0;
		head = -1;
		tail = -1;

		channel.close();
	}
}