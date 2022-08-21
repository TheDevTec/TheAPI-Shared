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
		value = new char[capacity];
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

	// long utils
	void getChars(long i, int index, char[] buf) {
		long q;
		int r;
		int charPos = index;
		char sign = 0;

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

}
