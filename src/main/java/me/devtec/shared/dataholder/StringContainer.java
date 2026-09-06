package me.devtec.shared.dataholder;

import java.util.Arrays;

public class StringContainer implements CharSequence {

	private static final int DEFAULT_CAPACITY = 16;
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
	private static final byte[] EMPTY_BYTES = {};

	final static char[] DigitTens = {
			'0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
			'1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
			'2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
			'3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
			'4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
			'5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
			'6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
			'7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
			'8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
			'9', '9', '9', '9', '9', '9', '9', '9', '9', '9'
	};

	final static char[] DigitOnes = {
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
	};

	final static char[] digits = {
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
			'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
			'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
			'u', 'v', 'w', 'x', 'y', 'z'
	};

	private transient char[] value;
	private int count;

	public StringContainer() {
		value = new char[DEFAULT_CAPACITY];
	}

	public StringContainer(int capacity) {
		value = new char[capacity <= 0 ? DEFAULT_CAPACITY : capacity];
	}

	public StringContainer(String text) {
		this(text, 0);
	}

	public StringContainer(String text, int offset) {
		this(text, offset, 16);
	}

	public StringContainer(String text, int offset, int additionalCapacity) {
		count = text.length() - offset;
		value = new char[count + Math.max(0, additionalCapacity)];
		text.getChars(offset, text.length(), value, 0);
	}

	public StringContainer(char[] array, int size) {
		value = new char[size + 1];
		System.arraycopy(array, 0, value, 0, Math.min(array.length, size));
		count = size;
	}

	@Override
	public int length() {
		return count;
	}

	public void ensureCapacity(int minimumCapacity) {
		if (minimumCapacity > value.length)
			ensureCapacityInternal(minimumCapacity);
	}

	private void ensureCapacityInternal(int minimumCapacity) {
		if (minimumCapacity - value.length > 0)
			value = Arrays.copyOf(value, newCapacity(minimumCapacity));
	}

	public boolean isEmpty() {
		return count == 0;
	}

	private int newCapacity(int minCapacity) {
		int newCapacity = (value.length << 1) + 2;

		if (newCapacity - minCapacity < 0)
			newCapacity = minCapacity;

		return newCapacity <= 0 || MAX_ARRAY_SIZE - newCapacity < 0
				? hugeCapacity(minCapacity)
						: newCapacity;
	}

	private int hugeCapacity(int minCapacity) {
		if (Integer.MAX_VALUE - minCapacity < 0)
			throw new OutOfMemoryError();

		return Math.max(minCapacity, MAX_ARRAY_SIZE);
	}

	@Override
	public char charAt(int index) {
		return value[index];
	}

	public StringContainer setCharAt(int index, char newChar) {
		value[index] = newChar;
		return this;
	}

	public StringContainer append(StringContainer asb) {
		if (asb == null)
			return appendNull();

		int len = asb.count;

		if (len == 0)
			return this;

		int pos = count;
		ensureCapacityInternal(pos + len);
		System.arraycopy(asb.value, 0, value, pos, len);
		count = pos + len;
		return this;
	}

	public StringContainer append(CharSequence s, int start, int end) {
		if (s == null)
			s = "null";

		int len = end - start;

		if (len <= 0)
			return this;

		int pos = count;
		ensureCapacityInternal(pos + len);
		copyChars(s, start, end, pos);
		count = pos + len;
		return this;
	}

	public StringContainer append(CharSequence asb) {
		if (asb == null)
			return appendNull();

		if (asb instanceof StringContainer)
			return append((StringContainer) asb);

		if (asb instanceof String)
			return append((String) asb);

		int len = asb.length();

		if (len == 0)
			return this;

		int pos = count;
		ensureCapacityInternal(pos + len);
		copyChars(asb, 0, len, pos);
		count = pos + len;
		return this;
	}

	public StringContainer append(long l) {
		if (l == Long.MIN_VALUE)
			return append("-9223372036854775808");

		int appendedLength = l < 0 ? stringSize(-l) + 1 : stringSize(l);
		int end = count + appendedLength;

		ensureCapacityInternal(end);
		getChars(l, end, value);
		count = end;
		return this;
	}

	public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
		System.arraycopy(value, srcBegin, dst, dstBegin, srcEnd - srcBegin);
	}

	public StringContainer append(String str) {
		if (str == null)
			return appendNull();

		int len = str.length();

		if (len == 0)
			return this;

		int pos = count;
		ensureCapacityInternal(pos + len);
		str.getChars(0, len, value, pos);
		count = pos + len;
		return this;
	}

	public StringContainer append(String str, int start, int end) {
		if (str == null)
			return appendNull();

		int len = end - start;

		if (len <= 0)
			return this;

		int pos = count;
		ensureCapacityInternal(pos + len);
		str.getChars(start, end, value, pos);
		count = pos + len;
		return this;
	}

	public StringContainer append(char[] array, int offset, int length) {
		if (length <= 0)
			return this;

		int pos = count;
		ensureCapacityInternal(pos + length);
		System.arraycopy(array, offset, value, pos, length);
		count = pos + length;
		return this;
	}

	public StringContainer append(char[] array) {
		int len = array.length;

		if (len == 0)
			return this;

		int pos = count;
		ensureCapacityInternal(pos + len);
		System.arraycopy(array, 0, value, pos, len);
		count = pos + len;
		return this;
	}

	public StringContainer appendNull() {
		int pos = count;

		ensureCapacityInternal(pos + 4);

		value[pos++] = 'n';
		value[pos++] = 'u';
		value[pos++] = 'l';
		value[pos++] = 'l';

		count = pos;
		return this;
	}

	public StringContainer append(char c) {
		int pos = count;
		ensureCapacityInternal(pos + 1);
		value[pos] = c;
		count = pos + 1;
		return this;
	}

	public StringContainer insert(int offset, char c) {
		int oldCount = count;

		ensureCapacityInternal(oldCount + 1);

		if (offset < oldCount)
			System.arraycopy(value, offset, value, offset + 1, oldCount - offset);

		value[offset] = c;
		count = oldCount + 1;
		return this;
	}

	public StringContainer insertMultipleChars(int offset, char... characters) {
		int len = characters.length;

		if (len == 0)
			return this;

		int oldCount = count;
		ensureCapacityInternal(oldCount + len);

		if (offset < oldCount)
			System.arraycopy(value, offset, value, offset + len, oldCount - offset);

		System.arraycopy(characters, 0, value, offset, len);

		count = oldCount + len;
		return this;
	}

	public StringContainer insert(int offset, String str) {
		if (offset < 0 || offset > count)
			throw new StringIndexOutOfBoundsException(offset);

		if (str == null)
			str = "null";

		int len = str.length();

		if (len == 0)
			return this;

		int oldCount = count;
		ensureCapacityInternal(oldCount + len);

		if (offset < oldCount)
			System.arraycopy(value, offset, value, offset + len, oldCount - offset);

		str.getChars(0, len, value, offset);

		count = oldCount + len;
		return this;
	}

	public StringContainer insert(int pos, long l) {
		if (pos < 0 || pos > count)
			throw new StringIndexOutOfBoundsException(pos);

		if (l == Long.MIN_VALUE)
			return insert(pos, "-9223372036854775808");

		int len = l < 0 ? stringSize(-l) + 1 : stringSize(l);
		int oldCount = count;

		ensureCapacityInternal(oldCount + len);

		if (pos < oldCount)
			System.arraycopy(value, pos, value, pos + len, oldCount - pos);

		getChars(l, pos + len, value);
		count = oldCount + len;
		return this;
	}

	public StringContainer appendInternal(char c) {
		value[count++] = c;
		return this;
	}

	public char[] getValue() {
		if (count < value.length)
			value = Arrays.copyOf(value, count);

		return value;
	}

	public byte[] getBytes() {
		return getBytes(value, count);
	}

	public static byte[] getBytes(CharSequence input) {
		if (input instanceof StringContainer) {
			StringContainer container = (StringContainer) input;
			return getBytes(container.value, container.count);
		}

		int length = input.length();

		if (length == 0)
			return EMPTY_BYTES;

		byte[] result = new byte[length];
		int i = 0;

		for (; i < length; ++i) {
			char c = input.charAt(i);

			if (c > 0x7F)
				break;

			result[i] = (byte) c;
		}

		if (i == length)
			return result;

		long byteLength = i;

		for (int j = i; j < length; ++j) {
			char c = input.charAt(j);

			if (c <= 0x7F)
				++byteLength;
			else if (c <= 0x7FF)
				byteLength += 2;
			else if (Character.isHighSurrogate(c)) {
				if (++j >= length || !Character.isLowSurrogate(input.charAt(j)))
					throw new IllegalArgumentException("Invalid surrogate pair.");

				byteLength += 4;
			} else if (Character.isLowSurrogate(c))
				throw new IllegalArgumentException("Invalid surrogate pair.");
			else
				byteLength += 3;
		}

		if (byteLength > Integer.MAX_VALUE)
			throw new OutOfMemoryError("UTF-8 result too large");

		result = Arrays.copyOf(result, (int) byteLength);
		int pos = i;

		for (; i < length; ++i) {
			int c = input.charAt(i);

			if (c <= 0x7F) {
				result[pos++] = (byte) c;
				continue;
			}

			if (c <= 0x7FF) {
				result[pos++] = (byte) (0xC0 | c >> 6);
				result[pos++] = (byte) (0x80 | c & 0x3F);
				continue;
			}

			char high = (char) c;

			if (Character.isHighSurrogate(high)) {
				char low = input.charAt(++i);
				int codePoint = Character.toCodePoint(high, low);

				result[pos++] = (byte) (0xF0 | codePoint >> 18);
				result[pos++] = (byte) (0x80 | codePoint >> 12 & 0x3F);
				result[pos++] = (byte) (0x80 | codePoint >> 6 & 0x3F);
				result[pos++] = (byte) (0x80 | codePoint & 0x3F);
				continue;
			}

			result[pos++] = (byte) (0xE0 | c >> 12);
			result[pos++] = (byte) (0x80 | c >> 6 & 0x3F);
			result[pos++] = (byte) (0x80 | c & 0x3F);
		}

		return result;
	}

	private static byte[] getBytes(char[] input, int length) {
		if (length == 0)
			return EMPTY_BYTES;

		byte[] result = new byte[length];
		int i = 0;

		for (; i < length; ++i) {
			char c = input[i];

			if (c > 0x7F)
				break;

			result[i] = (byte) c;
		}

		if (i == length)
			return result;

		long byteLength = i;

		for (int j = i; j < length; ++j) {
			char c = input[j];

			if (c <= 0x7F)
				++byteLength;
			else if (c <= 0x7FF)
				byteLength += 2;
			else if (Character.isHighSurrogate(c)) {
				if (++j >= length || !Character.isLowSurrogate(input[j]))
					throw new IllegalArgumentException("Invalid surrogate pair.");

				byteLength += 4;
			} else if (Character.isLowSurrogate(c))
				throw new IllegalArgumentException("Invalid surrogate pair.");
			else
				byteLength += 3;
		}

		if (byteLength > Integer.MAX_VALUE)
			throw new OutOfMemoryError("UTF-8 result too large");

		result = Arrays.copyOf(result, (int) byteLength);
		int pos = i;

		for (; i < length; ++i) {
			int c = input[i];

			if (c <= 0x7F) {
				result[pos++] = (byte) c;
				continue;
			}

			if (c <= 0x7FF) {
				result[pos++] = (byte) (0xC0 | c >> 6);
				result[pos++] = (byte) (0x80 | c & 0x3F);
				continue;
			}

			char high = (char) c;

			if (Character.isHighSurrogate(high)) {
				char low = input[++i];
				int codePoint = Character.toCodePoint(high, low);

				result[pos++] = (byte) (0xF0 | codePoint >> 18);
				result[pos++] = (byte) (0x80 | codePoint >> 12 & 0x3F);
				result[pos++] = (byte) (0x80 | codePoint >> 6 & 0x3F);
				result[pos++] = (byte) (0x80 | codePoint & 0x3F);
				continue;
			}

			result[pos++] = (byte) (0xE0 | c >> 12);
			result[pos++] = (byte) (0x80 | c >> 6 & 0x3F);
			result[pos++] = (byte) (0x80 | c & 0x3F);
		}

		return result;
	}

	public char[] getValueWithoutTrim() {
		return value;
	}

	public void clear() {
		count = 0;
	}

	public void deleteCharAt(int index) {
		int move = count - index - 1;

		if (move > 0)
			System.arraycopy(value, index + 1, value, index, move);

		--count;
	}

	@Override
	public String toString() {
		return new String(value, 0, count);
	}

	public String substring(int start) {
		return substring(start, count);
	}

	public String substring(int start, int end) {
		return new String(value, start, end - start);
	}

	public StringContainer delete(int start, int end) {
		int len = end - start;

		if (len <= 0)
			return this;

		if (end >= count) {
			count = start;
			return this;
		}

		System.arraycopy(value, end, value, start, count - end);
		count -= len;
		return this;
	}

	public StringContainer replace(int start, int end, CharSequence str) {
		if (end > count)
			end = count;

		int len = str.length();
		int removed = end - start;
		int oldCount = count;
		int newCount = oldCount + len - removed;

		if (str == this)
			str = toString();

		ensureCapacityInternal(newCount);

		if (len != removed && end < oldCount)
			System.arraycopy(value, end, value, start + len, oldCount - end);

		copyChars(str, 0, len, start);

		count = newCount;
		return this;
	}

	private void copyChars(CharSequence source, int start, int end, int destination) {
		if (source instanceof String) {
			((String) source).getChars(start, end, value, destination);
			return;
		}

		if (source instanceof StringContainer) {
			((StringContainer) source).getChars(start, end, value, destination);
			return;
		}

		if (source instanceof StringBuilder) {
			((StringBuilder) source).getChars(start, end, value, destination);
			return;
		}

		if (source instanceof StringBuffer) {
			((StringBuffer) source).getChars(start, end, value, destination);
			return;
		}

		for (int i = start; i < end; ++i)
			value[destination++] = source.charAt(i);
	}

	void getChars(long lIndex, int index, char[] buf) {
		long q;
		int r;
		int charPos = index;
		char sign = 0;

		long i = lIndex;

		if (i < 0) {
			sign = '-';
			i = -i;
		}

		while (i > Integer.MAX_VALUE) {
			q = i / 100;
			r = (int) (i - ((q << 6) + (q << 5) + (q << 2)));
			i = q;

			buf[--charPos] = DigitOnes[r];
			buf[--charPos] = DigitTens[r];
		}

		int q2;
		int i2 = (int) i;

		while (i2 >= 65536) {
			q2 = i2 / 100;
			r = i2 - ((q2 << 6) + (q2 << 5) + (q2 << 2));
			i2 = q2;

			buf[--charPos] = DigitOnes[r];
			buf[--charPos] = DigitTens[r];
		}

		do {
			q2 = i2 * 52429 >>> 19;
			r = i2 - ((q2 << 3) + (q2 << 1));

			buf[--charPos] = digits[r];
			i2 = q2;
		} while (i2 != 0);

		if (sign != 0)
			buf[--charPos] = sign;
	}

	int stringSize(long x) {
		long p = 10;

		for (int i = 1; i < 19; ++i) {
			if (x < p)
				return i;

			p *= 10;
		}

		return 19;
	}

	public StringContainer replace(String search, String replacement) {
		if (search == null || replacement == null || search.isEmpty() || search.equals(replacement))
			return this;

		int searchLength = search.length();
		int replacementLength = replacement.length();

		int first = indexOf(search);

		if (first == -1)
			return this;

		int second = indexOf(search, first + searchLength);

		if (second == -1)
			return replace(first, first + searchLength, replacement);

		if (replacementLength == searchLength) {
			int index = first;

			while (index != -1) {
				replacement.getChars(0, replacementLength, value, index);
				index = indexOf(search, index + searchLength);
			}

			return this;
		}

		if (replacementLength < searchLength) {
			int read = 0;
			int write = 0;
			int index = first;

			while (index != -1) {
				int before = index - read;

				if (before > 0) {
					if (write != read)
						System.arraycopy(value, read, value, write, before);

					write += before;
				}

				if (replacementLength > 0) {
					replacement.getChars(0, replacementLength, value, write);
					write += replacementLength;
				}

				read = index + searchLength;
				index = indexOf(search, read);
			}

			int remaining = count - read;

			if (remaining > 0) {
				if (write != read)
					System.arraycopy(value, read, value, write, remaining);

				write += remaining;
			}

			count = write;
			return this;
		}

		int[] positions = new int[8];
		int found = 0;
		int index = first;

		while (index != -1) {
			if (found == positions.length)
				positions = Arrays.copyOf(positions, found << 1);

			positions[found++] = index;
			index = indexOf(search, index + searchLength);
		}

		long required = count + (long) found * (replacementLength - searchLength);

		if (required > Integer.MAX_VALUE)
			throw new OutOfMemoryError();

		int oldCount = count;
		int newCount = (int) required;

		ensureCapacityInternal(newCount);

		int sourceEnd = oldCount;
		int destinationEnd = newCount;

		for (int i = found - 1; i >= 0; --i) {
			int foundAt = positions[i];
			int tailStart = foundAt + searchLength;
			int tailLength = sourceEnd - tailStart;

			if (tailLength > 0) {
				destinationEnd -= tailLength;
				System.arraycopy(value, tailStart, value, destinationEnd, tailLength);
			}

			destinationEnd -= replacementLength;
			replacement.getChars(0, replacementLength, value, destinationEnd);

			sourceEnd = foundAt;
		}

		count = newCount;
		return this;
	}

	public StringContainer replaceFirst(String search, String replacement) {
		if (search == null || replacement == null || search.isEmpty())
			return this;

		int start = indexOf(search);

		if (start != -1)
			replace(start, start + search.length(), replacement);

		return this;
	}

	public StringContainer replaceLast(String search, String replacement) {
		if (search == null || replacement == null || search.isEmpty())
			return this;

		int start = lastIndexOf(search);

		if (start != -1)
			replace(start, start + search.length(), replacement);

		return this;
	}

	public StringContainer removeAllChars(char removing) {
		int write = 0;

		for (int read = 0; read < count; ++read) {
			char c = value[read];

			if (c != removing)
				value[write++] = c;
		}

		count = write;
		return this;
	}

	public StringContainer removeAllChars(char... removing) {
		if (removing == null || removing.length == 0)
			return this;

		switch (removing.length) {
		case 1:
			return removeAllChars(removing[0]);

		case 2:
			return removeAllChars(removing[0], removing[1]);

		case 3:
			return removeAllChars(removing[0], removing[1], removing[2]);

		case 4:
			return removeAllChars(removing[0], removing[1], removing[2], removing[3]);

		default:
			break;
		}

		int write = 0;

		outer:
			for (int read = 0; read < count; ++read) {
				char c = value[read];

				for (char element : removing)
					if (c == element)
						continue outer;

				value[write++] = c;
			}

		count = write;
		return this;
	}

	private StringContainer removeAllChars(char a, char b) {
		int write = 0;

		for (int read = 0; read < count; ++read) {
			char c = value[read];

			if (c != a && c != b)
				value[write++] = c;
		}

		count = write;
		return this;
	}

	private StringContainer removeAllChars(char a, char b, char c) {
		int write = 0;

		for (int read = 0; read < count; ++read) {
			char current = value[read];

			if (current != a && current != b && current != c)
				value[write++] = current;
		}

		count = write;
		return this;
	}

	private StringContainer removeAllChars(char a, char b, char c, char d) {
		int write = 0;

		for (int read = 0; read < count; ++read) {
			char current = value[read];

			if (current != a && current != b && current != c && current != d)
				value[write++] = current;
		}

		count = write;
		return this;
	}

	public StringContainer replace(char search, char replacement) {
		if (search == replacement)
			return this;

		for (int i = 0; i < count; ++i)
			if (value[i] == search)
				value[i] = replacement;

		return this;
	}

	public StringContainer replaceFirst(char search, char replacement) {
		for (int i = 0; i < count; ++i)
			if (value[i] == search) {
				value[i] = replacement;
				break;
			}

		return this;
	}

	public StringContainer replaceLast(char search, char replacement) {
		for (int i = count - 1; i >= 0; --i)
			if (value[i] == search) {
				value[i] = replacement;
				break;
			}

		return this;
	}

	public boolean contains(char search) {
		return indexOf(search) != -1;
	}

	public boolean contains(String search) {
		return indexOf(search) != -1;
	}

	public boolean containsIgnoreCase(String search) {
		return indexOfIgnoreCase(search) != -1;
	}

	public int indexOf(char search) {
		return indexOf(search, 0);
	}

	public int indexOf(char search, int start) {
		if (start < 0)
			start = 0;

		for (int i = start; i < count; ++i)
			if (value[i] == search)
				return i;

		return -1;
	}

	public int lastIndexOf(char search) {
		return lastIndexOf(search, count - 1);
	}

	public int lastIndexOf(char search, int start) {
		for (int i = Math.min(start, count - 1); i >= 0; --i)
			if (value[i] == search)
				return i;

		return -1;
	}

	public int lastIndexOf(char search, int start, int limit) {
		for (int i = Math.min(start, count - 1); i >= 0; --i)
			if (value[i] == search && --limit <= 0)
				return i;

		return -1;
	}

	public int indexOf(String search) {
		return indexOf(search, 0);
	}

	public int indexOf(String search, int start) {
		return indexOf(start, search);
	}

	protected int indexOf(int start, String search) {
		int length = search.length();

		if (start < 0)
			start = 0;

		if (length == 0)
			return Math.min(start, count);

		if (length == 1)
			return indexOf(search.charAt(0), start);

		int max = count - length;

		if (start > max)
			return -1;

		char first = search.charAt(0);
		char last = search.charAt(length - 1);
		int lastOffset = length - 1;

		for (int i = start; i <= max; ++i) {
			if (value[i] != first || value[i + lastOffset] != last)
				continue;

			int j = 1;

			while (j < lastOffset && value[i + j] == search.charAt(j))
				++j;

			if (j == lastOffset)
				return i;
		}

		return -1;
	}

	public int indexOfIgnoreCase(char search) {
		return indexOfIgnoreCase(search, 0);
	}

	public int indexOfIgnoreCase(char search, int start) {
		if (start < 0)
			start = 0;

		for (int i = start; i < count; ++i)
			if (equalsIgnoreCase(value[i], search))
				return i;

		return -1;
	}

	public int indexOfIgnoreCase(char search, int start, int limit) {
		if (start < 0)
			start = 0;

		for (int i = start; i < count; ++i)
			if (equalsIgnoreCase(value[i], search) && --limit <= 0)
				return i;

		return -1;
	}

	public int indexOfIgnoreCase(String search) {
		return indexOfIgnoreCase(search, 0);
	}

	public int indexOfIgnoreCase(String search, int start) {
		return indexOfIgnoreCase(start, search);
	}

	protected int indexOfIgnoreCase(int start, String search) {
		int searchLength = search.length();

		if (start < 0)
			start = 0;

		if (searchLength == 0)
			return Math.min(start, count);

		if (searchLength == 1)
			return indexOfIgnoreCase(search.charAt(0), start);

		int max = count - searchLength;

		if (start > max)
			return -1;

		char first = search.charAt(0);

		for (int i = start; i <= max; ++i) {
			if (!equalsIgnoreCase(value[i], first))
				continue;

			int j = 1;

			while (j < searchLength && equalsIgnoreCase(value[i + j], search.charAt(j)))
				++j;

			if (j == searchLength)
				return i;
		}

		return -1;
	}

	public int lastIndexOf(String search) {
		return lastIndexOf(search, count);
	}

	public int lastIndexOf(String search, int start) {
		return lastIndexOf(start, search);
	}

	protected int lastIndexOf(int start, String search) {
		int length = search.length();

		if (length == 0)
			return start < 0 ? -1 : Math.min(start, count);

		if (length == 1)
			return lastIndexOf(search.charAt(0), start);

		int from = Math.min(start, count - length);

		if (from < 0)
			return -1;

		char first = search.charAt(0);
		char last = search.charAt(length - 1);
		int lastOffset = length - 1;

		for (int i = from; i >= 0; --i) {
			if (value[i] != first || value[i + lastOffset] != last)
				continue;

			int j = 1;

			while (j < lastOffset && value[i + j] == search.charAt(j))
				++j;

			if (j == lastOffset)
				return i;
		}

		return -1;
	}

	public int lastIndexOfIgnoreCase(String search) {
		return lastIndexOfIgnoreCase(search, count);
	}

	public int lastIndexOfIgnoreCase(String search, int start) {
		return lastIndexOfIgnoreCase(start, search);
	}

	protected int lastIndexOfIgnoreCase(int start, String search) {
		int searchLength = search.length();

		if (searchLength == 0)
			return start < 0 ? -1 : Math.min(start, count);

		if (searchLength == 1) {
			char c = search.charAt(0);

			for (int i = Math.min(start, count - 1); i >= 0; --i)
				if (equalsIgnoreCase(value[i], c))
					return i;

			return -1;
		}

		int from = Math.min(start, count - searchLength);

		if (from < 0)
			return -1;

		char first = search.charAt(0);

		for (int i = from; i >= 0; --i) {
			if (!equalsIgnoreCase(value[i], first))
				continue;

			int j = 1;

			while (j < searchLength && equalsIgnoreCase(value[i + j], search.charAt(j)))
				++j;

			if (j == searchLength)
				return i;
		}

		return -1;
	}

	private static boolean equalsIgnoreCase(char first, char second) {
		if (first == second)
			return true;

		if (first < 128 && second < 128) {
			if (first >= 'A' && first <= 'Z')
				first = (char) (first + ('a' - 'A'));

			if (second >= 'A' && second <= 'Z')
				second = (char) (second + ('a' - 'A'));

			return first == second;
		}

		return Character.toUpperCase(first) == Character.toUpperCase(second);
	}

	public void increaseCount(int newCount) {
		count += newCount;
	}

	public StringContainer trim() {
		int start = 0;
		int end = count;

		while (start < end) {
			char c = value[start];

			if (c != ' ' && c != '\t')
				break;

			++start;
		}

		while (end > start) {
			char c = value[end - 1];

			if (c != ' ' && c != '\t')
				break;

			--end;
		}

		int length = end - start;

		if (start > 0 && length > 0)
			System.arraycopy(value, start, value, 0, length);

		count = length;
		return this;
	}

	public boolean startsWith(String prefix) {
		return startsWith((CharSequence) prefix, 0);
	}

	public boolean startsWith(String prefix, int toffset) {
		return startsWith((CharSequence) prefix, toffset);
	}

	public boolean startsWith(CharSequence prefix, int toffset) {
		int length = prefix.length();

		if (toffset < 0 || toffset > count - length)
			return false;

		for (int i = 0; i < length; ++i)
			if (value[toffset + i] != prefix.charAt(i))
				return false;

		return true;
	}

	public boolean endsWith(String suffix) {
		return endsWith((CharSequence) suffix);
	}

	public boolean endsWith(CharSequence suffix) {
		return startsWith(suffix, count - suffix.length());
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		int len = end - start;
		StringContainer sub = new StringContainer(len);

		System.arraycopy(value, start, sub.value, 0, len);
		sub.count = len;

		return sub;
	}
}
