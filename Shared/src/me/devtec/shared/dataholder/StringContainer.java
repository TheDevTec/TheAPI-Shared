package me.devtec.shared.dataholder;

import java.util.Arrays;

public class StringContainer {
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	// long utils
	final static char[] DigitTens = { '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '1', '1', '1', '1', '1', '1', '1', '1', '1', '2', '2', '2', '2', '2', '2', '2', '2', '2', '2', '3', '3',
			'3', '3', '3', '3', '3', '3', '3', '3', '4', '4', '4', '4', '4', '4', '4', '4', '4', '4', '5', '5', '5', '5', '5', '5', '5', '5', '5', '5', '6', '6', '6', '6', '6', '6', '6', '6', '6',
			'6', '7', '7', '7', '7', '7', '7', '7', '7', '7', '7', '8', '8', '8', '8', '8', '8', '8', '8', '8', '8', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', };

	final static char[] DigitOnes = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1',
			'2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8',
			'9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', };
	final static char[] digits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w',
			'x', 'y', 'z' };

	private char[] value;

	private int count;

	public StringContainer(int capacity) {
		value = new char[capacity <= 0 ? 16 : capacity];
	}

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

	private StringContainer appendNull() {
		int c = count;
		ensureCapacityInternal(c + 4);
		final char[] value = this.value;
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

	public char[] getValue() {
		if (count < value.length)
			value = Arrays.copyOf(value, count);
		return value;
	}

	public char[] getValueWithoutTrim() {
		return value;
	}

	public void clear() {
		count = 0;
		value = new char[32];
	}

	public void deleteCharAt(int index) {
		System.arraycopy(value, index + 1, value, index, count - index - 1);
		count--;
	}

	@Override
	public String toString() {
		return new String(value, 0, count);
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
		char[] lookingFor = value.toCharArray();

		int start = indexOf(0, lookingFor);
		int index = start;
		while (start != -1) {
			replace(start, start + value.length(), replacement);
			start = indexOf(index, lookingFor);
			index += start;
		}
		return this;
	}

	public StringContainer replaceFirst(String value, String replacement) {
		int start = indexOf(value);
		if (start != -1)
			replace(start, start + value.length(), replacement);
		return this;
	}

	public StringContainer replaceLast(String value, String replacement) {
		int start = lastIndexOf(value);
		if (start != -1)
			replace(start, start + value.length(), replacement);
		return this;
	}

	public StringContainer replace(char value, char replacement) {
		int start = indexOf(value, 0);
		int index = start;
		while (start != -1) {
			setCharAt(start, replacement);
			start = indexOf(value, index);
			index += start;
		}
		return this;
	}

	public StringContainer replaceFirst(char value, char replacement) {
		int start = indexOf(value);
		if (start != -1)
			setCharAt(start, replacement);
		return this;
	}

	public StringContainer replaceLast(char value, char replacement) {
		int start = lastIndexOf(value);
		if (start != -1)
			setCharAt(start, replacement);
		return this;
	}

	public boolean contains(char value) {
		return indexOf(value) != -1;
	}

	public boolean contains(String value) {
		return indexOf(value) != -1;
	}

	public int indexOf(char c) {
		return indexOf(c, 0);
	}

	public int indexOf(char c, int start) {
		for (int i = start; i < count; ++i)
			if (value[i] == c)
				return i;
		return -1;
	}

	public int lastIndexOf(char val) {
		for (int i = count; i > -1; --i)
			if (value[i] == val)
				return i;
		return -1;
	}

	public int indexOf(String value) {
		return indexOf(value, 0);
	}

	public int indexOf(String value, int start) {
		return indexOf(start, value.toCharArray());
	}

	protected int indexOf(int start, char[] lookingFor) {
		int foundPos = 0;

		if (start + lookingFor.length > count)
			return -1;

		for (int i = start; i < count; ++i)
			if (value[i] == lookingFor[foundPos]) {
				if (++foundPos == lookingFor.length)
					return i - (lookingFor.length - 1);
			} else
				foundPos = 0;
		return -1;
	}

	public int lastIndexOf(String value) {
		return lastIndexOf(value, count);
	}

	public int lastIndexOf(String value, int start) {
		return lastIndexOf(start, value.toCharArray());
	}

	protected int lastIndexOf(int start, char[] lookingFor) {
		int foundPos = lookingFor.length - 1;

		if (start - lookingFor.length < 0)
			return -1;

		for (int i = start; i > -1; --i)
			if (value[i] == lookingFor[foundPos]) {
				if (--foundPos == -1)
					return i;
			} else
				foundPos = lookingFor.length - 1;
		return -1;
	}

	public void increaseCount(int newCount) {
		count += newCount;
	}

}
