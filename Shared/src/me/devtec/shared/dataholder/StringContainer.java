package me.devtec.shared.dataholder;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StringContainer implements CharSequence {
	private static final int DEFAULT_CAPACITY = 16;

	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	private static final Charset charset = StandardCharsets.UTF_8;

	// long utils
	final static char[] DigitTens = { '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '1', '1', '1', '1', '1', '1', '1', '1', '1', '2', '2', '2', '2', '2', '2', '2', '2', '2', '2', '3', '3',
			'3', '3', '3', '3', '3', '3', '3', '3', '4', '4', '4', '4', '4', '4', '4', '4', '4', '4', '5', '5', '5', '5', '5', '5', '5', '5', '5', '5', '6', '6', '6', '6', '6', '6', '6', '6', '6',
			'6', '7', '7', '7', '7', '7', '7', '7', '7', '7', '7', '8', '8', '8', '8', '8', '8', '8', '8', '8', '8', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', };

	final static char[] DigitOnes = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1',
			'2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8',
			'9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', };
	final static char[] digits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w',
			'x', 'y', 'z' };

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
		value = new char[(count = text.length() - offset) + Math.max(0, additionalCapacity)];
		text.getChars(offset, text.length(), value, 0);
	}

	@Override
	public int length() {
		return count;
	}

	public void ensureCapacity(int minimumCapacity) {
		if (minimumCapacity > 0)
			ensureCapacityInternal(minimumCapacity);
	}

	private void ensureCapacityInternal(int minimumCapacity) {
		// overflow-conscious code
		if (minimumCapacity - value.length > 0)
			value = Arrays.copyOf(value, newCapacity(minimumCapacity));
	}

	private int newCapacity(int minCapacity) {
		// overflow-conscious code
		int newCapacity = (value.length << 1) + 2;
		if (newCapacity - minCapacity < 0)
			newCapacity = minCapacity;
		return newCapacity <= 0 || MAX_ARRAY_SIZE - newCapacity < 0 ? hugeCapacity(minCapacity) : newCapacity;
	}

	private int hugeCapacity(int minCapacity) {
		if (Integer.MAX_VALUE - minCapacity < 0)
			throw new OutOfMemoryError();
		return minCapacity > MAX_ARRAY_SIZE ? minCapacity : MAX_ARRAY_SIZE;
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
		int len = asb.length();
		ensureCapacityInternal(count + len);
		asb.getChars(0, len, value, count);
		count += len;
		return this;
	}

	public StringContainer append(long l) {
		if (l == Long.MIN_VALUE) {
			append("-9223372036854775808");
			return this;
		}
		int appendedLength = l < 0 ? stringSize(-l) + 1 : stringSize(l);
		int spaceNeeded = count + appendedLength;
		ensureCapacityInternal(spaceNeeded);
		getChars(l, spaceNeeded, value);
		count = spaceNeeded;
		return this;
	}

	public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
		System.arraycopy(value, srcBegin, dst, dstBegin, srcEnd - srcBegin);
	}

	public StringContainer append(String str) {
		if (str == null)
			return appendNull();
		int len = str.length();
		ensureCapacityInternal(count + len);
		str.getChars(0, len, value, count);
		count += len;
		return this;
	}

	public StringContainer append(char[] array) {
		int len = array.length;
		ensureCapacityInternal(count + len);
		System.arraycopy(array, 0, value, count, array.length);
		count += len;
		return this;
	}

	public StringContainer appendNull() {
		int c = count;
		ensureCapacityInternal(c + 4);
		value[c++] = 'n';
		value[c++] = 'u';
		value[c++] = 'l';
		value[c++] = 'l';
		count = c;
		return this;
	}

	public StringContainer append(char c) {
		ensureCapacityInternal(count + 1);
		value[count++] = c;
		return this;
	}

	public StringContainer insert(int offset, char c) {
		ensureCapacityInternal(count + 1);
		System.arraycopy(value, offset, value, offset + 1, ++count - offset - 1);
		value[offset] = c;
		return this;
	}

	public StringContainer insertMultipleChars(int offset, char... characters) {
		int len = characters.length;
		ensureCapacityInternal(count + len);
		System.arraycopy(value, offset, value, offset + len, count - offset);
		for (char c : characters)
			value[offset++] = c;
		count += len;
		return this;
	}

	public StringContainer insert(int offset, String str) {
		if (offset < 0 || offset > length())
			throw new StringIndexOutOfBoundsException(offset);
		if (str == null)
			str = "null";
		int len = str.length();
		ensureCapacityInternal(count + len);
		System.arraycopy(value, offset, value, offset + len, count - offset);
		str.getChars(0, len, value, offset);
		count += len;
		return this;
	}

	public StringContainer insert(int pos, long l) {
		return insert(pos, String.valueOf(l));
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
		return getBytes(charset);
	}

	public byte[] getBytes(Charset charset) {
		if (count == 0)
			return new byte[0];

		CharsetEncoder encoder = charset.newEncoder();
		ByteBuffer bb = ByteBuffer.allocate((int) (count * (double) encoder.maxBytesPerChar()));
		encoder.onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
		CharBuffer cb = CharBuffer.wrap(value, 0, count);
		encoder.encode(cb, bb, true);
		encoder.flush(bb);
		return Arrays.copyOf(bb.array(), bb.position());
	}

	public char[] getValueWithoutTrim() {
		return value;
	}

	public void clear() {
		if (count == 0)
			return;
		delete(0, count);
	}

	public void deleteCharAt(int index) {
		System.arraycopy(value, index + 1, value, index, count - index - 1);
		count--;
	}

	@Override
	public String toString() {
		return new String(value, 0, count);
	}

	public String substring(int start) {
		return substring(start, length());
	}

	public String substring(int start, int end) {
		return new String(value, start, end - start);
	}

	public StringContainer delete(int start, int end) {
		int len = end - start;
		if (len > 0) {
			System.arraycopy(value, start + len, value, start, count - end);
			count -= len;
		}
		return this;
	}

	public StringContainer replace(int start, int end, String str) {
		if (end > count)
			end = count;

		int len = str.length();
		int newCount = count + len - (end - start);
		ensureCapacityInternal(newCount);

		System.arraycopy(value, end, value, start + len, count - end);
		str.getChars(0, len, value, start);
		count = newCount;
		return this;
	}

	public StringContainer replace(int start, int end, StringContainer str) {
		if (end > count)
			end = count;
		int len = str.length();
		int newCount = count + len - (end - start);
		ensureCapacityInternal(newCount);

		System.arraycopy(value, end, value, start + len, count - end);
		str.getChars(0, len, value, count);
		count = newCount;
		return this;
	}

	// long utils
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

		// Get 2 digits/iteration using longs until quotient fits into an int
		while (i > Integer.MAX_VALUE) {
			q = i / 100;
			// really: r = i - (q * 100);
			r = (int) (i - ((q << 6) + (q << 5) + (q << 2)));
			i = q;
			buf[--charPos] = DigitOnes[r];
			buf[--charPos] = DigitTens[r];
		}

		// Get 2 digits/iteration using ints
		int q2;
		int i2 = (int) i;
		while (i2 >= 65536) {
			q2 = i2 / 100;
			// really: r = i2 - (q * 100);
			r = i2 - ((q2 << 6) + (q2 << 5) + (q2 << 2));
			i2 = q2;
			buf[--charPos] = DigitOnes[r];
			buf[--charPos] = DigitTens[r];
		}

		// Fall thru to fast mode for smaller numbers
		// assert(i2 <= 65536, i2);
		for (;;) {
			q2 = i2 * 52429 >>> 16 + 3;
			r = i2 - ((q2 << 3) + (q2 << 1)); // r = i2-(q2*10) ...
			buf[--charPos] = digits[r];
			i2 = q2;
			if (i2 == 0)
				break;
		}
		if (sign != 0)
			buf[--charPos] = sign;
	}

	int stringSize(long x) {
		long p = 10;
		for (int i = 1; i < 19; i++) {
			if (x < p)
				return i;
			p = 10 * p;
		}
		return 19;
	}

	public StringContainer replace(String value, String replacement) {
		if (value == null || replacement == null)
			return this;
		int index;
		int start = 0;
		while (start < count && (index = indexOf(start, value)) != -1) {
			start = index + replacement.length();
			replace(index, index + value.length(), replacement);
		}
		return this;
	}

	public StringContainer replaceFirst(String value, String replacement) {
		if (value == null || replacement == null)
			return this;
		int start = indexOf(value);
		if (start != -1)
			replace(start, start + value.length(), replacement);
		return this;
	}

	public StringContainer replaceLast(String value, String replacement) {
		if (value == null || replacement == null)
			return this;
		int start = lastIndexOf(value);
		if (start != -1)
			replace(start, start + value.length(), replacement);
		return this;
	}

	public StringContainer removeAllChars(char... value) {
		for (int i = 0; i < count; ++i) {
			char c = charAt(i);
			for (char replacing : value)
				if (c == replacing) {
					deleteCharAt(i);
					--i;
					break;
				}
		}
		return this;
	}

	public StringContainer replace(char value, char replacement) {
		for (int i = 0; i < count; ++i)
			if (charAt(i) == value)
				setCharAt(i, replacement);
		return this;
	}

	public StringContainer replaceFirst(char value, char replacement) {
		for (int i = 0; i < count; ++i)
			if (charAt(i) == value)
				return setCharAt(i, replacement);
		return this;
	}

	public StringContainer replaceLast(char value, char replacement) {
		for (int i = count; i >= 0; --i)
			if (charAt(i) == value)
				return setCharAt(i, replacement);
		return this;
	}

	public boolean contains(char value) {
		return indexOf(value) != -1;
	}

	public boolean contains(String value) {
		return indexOf(value) != -1;
	}

	public boolean containsIgnoreCase(String value) {
		return indexOfIgnoreCase(value) != -1;
	}

	public int indexOf(char c) {
		return indexOf(c, 0);
	}

	public int indexOf(char c, int start) {
		for (int i = Math.min(start, count); i < count; ++i)
			if (value[i] == c)
				return i;
		return -1;
	}

	public int lastIndexOf(char val) {
		return lastIndexOf(val, count);
	}

	public int lastIndexOf(char val, int start) {
		for (int i = Math.min(start, count - 1); i >= 0; i--)
			if (value[i] == val)
				return i;
		return -1;
	}

	public int lastIndexOf(char val, int start, int limit) {
		for (int i = Math.min(start, count - 1); i >= 0; i--)
			if (value[i] == val && --limit <= 0)
				return i;
		return -1;
	}

	public int indexOf(String value) {
		return indexOf(value, 0);
	}

	public int indexOf(String value, int start) {
		return indexOf(start, value);
	}

	protected int indexOf(int start, String lookingFor) {
		if (lookingFor.length() == 1)
			return indexOf(lookingFor.charAt(0), start);
		int min = Math.min(start, count);
		int size = lookingFor.length();

		if (min + size > count)
			return -1;

		char firstChar = lookingFor.charAt(0);
		for (int i = min; i < count; ++i)
			if (value[i] == firstChar) {
				++i;
				int foundPos = 1;
				for (; i < count; ++i)
					if (value[i] == lookingFor.charAt(foundPos)) {
						if (++foundPos == size)
							return i - (size - 1);
					} else
						break;
			}
		return -1;
	}

	public int indexOfIgnoreCase(char val) {
		return indexOfIgnoreCase(val, count);
	}

	public int indexOfIgnoreCase(char val, int start) {
		for (int i = Math.min(start, count - 1); i >= 0; i--)
			if (Character.toUpperCase(value[i]) == Character.toUpperCase(val))
				return i;
		return -1;
	}

	public int indexOfIgnoreCase(char val, int start, int limit) {
		for (int i = Math.min(start, count - 1); i >= 0; i--)
			if (Character.toUpperCase(value[i]) == Character.toUpperCase(val) && --limit <= 0)
				return i;
		return -1;
	}

	public int indexOfIgnoreCase(String value) {
		return indexOfIgnoreCase(value, 0);
	}

	public int indexOfIgnoreCase(String value, int start) {
		return indexOfIgnoreCase(start, value);
	}

	protected int indexOfIgnoreCase(int start, String lookingFor) {
		if (lookingFor.length() == 1)
			return indexOfIgnoreCase(lookingFor.charAt(0), start);
		int min = Math.min(start, count);
		int size = lookingFor.length();

		if (min + size > count)
			return -1;

		char firstChar = lookingFor.charAt(0);
		for (int i = min; i < count; ++i)
			if (Character.toUpperCase(value[i]) == Character.toUpperCase(firstChar)) {
				++i;
				int foundPos = 1;
				for (; i < count; ++i)
					if (Character.toUpperCase(value[i]) == Character.toUpperCase(lookingFor.charAt(foundPos))) {
						if (++foundPos == size)
							return i - (size - 1);
					} else
						break;
			}
		return -1;
	}

	public int lastIndexOf(String value) {
		return lastIndexOf(value, count);
	}

	public int lastIndexOf(String value, int start) {
		return lastIndexOf(start, value);
	}

	protected int lastIndexOf(int start, String lookingFor) {
		if (lookingFor.length() == 1)
			return lastIndexOf(lookingFor.charAt(0), start);
		int min = Math.min(start, count - 1);
		int size = lookingFor.length();

		if (min - size < 0)
			return -1;

		char firstChar = lookingFor.charAt(0);
		for (int i = min; i >= 0; i--)
			if (value[i] == firstChar) {
				++i;
				int foundPos = 1;
				for (; i >= 0; i--)
					if (value[i] == lookingFor.charAt(foundPos)) {
						if (++foundPos == size)
							return i - (size - 1);
					} else
						break;
			}
		return -1;
	}

	public int lastIndexOfIgnoreCase(String value) {
		return lastIndexOfIgnoreCase(value, count);
	}

	public int lastIndexOfIgnoreCase(String value, int start) {
		return lastIndexOfIgnoreCase(start, value);
	}

	protected int lastIndexOfIgnoreCase(int start, String lookingFor) {
		int min = Math.min(start, count - 1);
		int size = lookingFor.length();

		if (min - size < 0)
			return -1;

		char firstChar = lookingFor.charAt(0);
		for (int i = min; i >= 0; i--)
			if (Character.toUpperCase(value[i]) == Character.toUpperCase(firstChar)) {
				++i;
				int foundPos = 1;
				for (; i >= 0; i--)
					if (Character.toUpperCase(value[i]) == Character.toUpperCase(lookingFor.charAt(foundPos))) {
						if (++foundPos == size)
							return i - (size - 1);
					} else
						break;
			}
		return -1;
	}

	public void increaseCount(int newCount) {
		count += newCount;
	}

	public boolean isEmpty() {
		return length() == 0;
	}

	public StringContainer trim() {
		int i = 0;
		char c;
		while (i < count && ((c = charAt(i)) == ' ' || c == '\t'))
			i++;
		if (i > 0)
			delete(0, i);

		i = count - 1;
		while (i >= 0 && ((c = charAt(i)) == ' ' || c == '\t'))
			i--;
		if (i < count - 1)
			delete(i + 1, count);

		return this;
	}

	public boolean startsWith(String prefix, int toffset) {
		char ta[] = value;
		int to = toffset;
		int po = 0;
		int pc = prefix.length();
		// Note: toffset might be near -1>>>1.
		if (toffset < 0 || toffset > length() - pc)
			return false;
		while (--pc >= 0)
			if (ta[to++] != prefix.charAt(po++))
				return false;
		return true;
	}

	public boolean endsWith(String suffix) {
		return startsWith(suffix, length() - suffix.length());
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		StringContainer sub = new StringContainer(end - start);
		System.arraycopy(value, start, sub.value, 0, end - start);
		sub.count += end - start;
		return sub;
	}
}
