package me.devtec.shared.utility;

public class ParseUtils {
	private static final char DOT = '.';
	private static final char COMMA = ',';
	private static final char SPACE = ' ';
	private static final char SMALL_E = 'e';
	private static final char BIG_E = 'E';
	private static final char MINUS = '-';
	private static final char PLUS = '+';

	/**
	 * @apiNote Parse boolean from the String
	 * @return boolean
	 */
	public static boolean getBoolean(CharSequence text) {
		return text != null && text.length() == 4 && toLowerCase(text.charAt(0)) == 't'
				&& toLowerCase(text.charAt(1)) == 'r' && toLowerCase(text.charAt(2)) == 'u'
				&& toLowerCase(text.charAt(3)) == SMALL_E;
	}

	/**
	 * @apiNote Checks if the String is Boolean
	 * @return boolean
	 */
	public static boolean isBoolean(CharSequence text) {
		if (text == null || text.length() > 5 || text.length() < 4)
			return false;
		if (text.length() == 5)
			return toLowerCase(text.charAt(0)) == 'f' && toLowerCase(text.charAt(1)) == 'a'
			&& toLowerCase(text.charAt(2)) == 'l' && toLowerCase(text.charAt(3)) == 's'
			&& toLowerCase(text.charAt(4)) == SMALL_E;
		// true
		return toLowerCase(text.charAt(0)) == 't' && toLowerCase(text.charAt(1)) == 'r'
				&& toLowerCase(text.charAt(2)) == 'u' && toLowerCase(text.charAt(3)) == SMALL_E;
	}

	private static char toLowerCase(int character) {
		return (char) (character <= 90 ? character + 32 : character);
	}

	/**
	 * @apiNote Parse double from the String
	 * @return double
	 */
	public static double getDouble(CharSequence text) {
		if (text == null)
			return 0;
		return getDouble(text, 0, text.length());
	}

	/**
	 * @apiNote Parse double from the String between certain positions in the String
	 * @param start Beginning in String from where the parse will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where parse will be applied (Defaultly length of
	 *              String)
	 * @return double
	 */
	public static double getDouble(CharSequence text, int start, int end) {
		if (text == null)
			return 0;
		return parseDecimalNumber(false, (short) 308, text, start, end).doubleValue();
	}

	private static double calculateResult(double result, int decimal, int exponent, boolean minusExponent,
			boolean minus, byte exponentSymbol) {
		int range = (minusExponent ? -exponent : exponent) - decimal;
		if (range != 0)
			if (range > 0)
				result *= Math.pow(10, range);
			else
				result /= Math.pow(10, range * -1);
		return exponentSymbol == 0 ? minus ? -result : result : 0;
	}

	/**
	 * @apiNote Checks if the String is double
	 * @return boolean
	 */
	public static boolean isDouble(CharSequence text) {
		if (text == null)
			return false;
		return isDouble(text, 0, text.length());
	}

	/**
	 * @apiNote Checks if the String between certain positions is double
	 * @param start Beginning in String from where the checking will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where checking will be applied (Defaultly length
	 *              of String)
	 * @return boolean
	 */
	public static boolean isDouble(CharSequence text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		short totalWidth = 0;

		boolean exponentMinus = false;
		boolean hasDecimal = false;
		boolean hasExponent = false;
		byte exponentSymbol = 0;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case PLUS:
				if (!minus && totalWidth == 0 && !foundZero)
					continue;
			case MINUS:
				if(hasExponent) {
					if(exponentMinus)break;
					exponentMinus=true;
					continue;
				}
				if (minus)
					break;
				minus = true;
				continue;
			case SMALL_E:
			case BIG_E:
				if (hasExponent || totalWidth == 0 && !foundZero)
					return false;
				hasExponent = true;
				exponentSymbol = 1;
				continue;
			case DOT:
			case COMMA:
				if (hasDecimal || hasExponent || totalWidth == 0 && !foundZero)
					return false;
				hasDecimal = true;
				continue;
			}
			if (c < 48 || c > 57) {
				if (end - 1 == i && (c == 'd' || c == 'f' || c == 'D' || c == 'F'))
					continue;
				if (!foundZero && totalWidth == 0 && c == 'N' && i + 3 <= end)
					return text.charAt(++i) == 'a' && text.charAt(++i) == 'N';
				if (!foundZero && totalWidth == 0 && c == 'I' && i + 8 <= end)
					return text.charAt(++i) == 'n' && text.charAt(++i) == 'f' && text.charAt(++i) == 'i'
					&& text.charAt(++i) == 'n' && text.charAt(++i) == 'i' && text.charAt(++i) == 't'
					&& text.charAt(++i) == 'y';
				return false;
			}
			if (!hasDecimal && totalWidth == 0 && c == 48) {
				foundZero = true;
				continue;
			}
			++totalWidth;
			exponentSymbol = 0;
		}
		return (totalWidth > 0 || foundZero) && exponentSymbol == 0;
	}

	/**
	 * @apiNote Parse long from the String
	 * @return long
	 */
	public static long getLong(CharSequence text) {
		if (text == null)
			return 0;
		return getLong(text, 0, text.length());
	}

	/**
	 * @apiNote Parse long from the String between certain positions in the String
	 * @param start Beginning in String from where the parse will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where parse will be applied (Defaultly length of
	 *              String)
	 * @return long
	 */
	public static long getLong(CharSequence text, int start, int end) {
		if (text == null)
			return 0;
		return parseNonDecimalNumber((byte) 3, 19, text, start, end).longValue();
	}

	/**
	 * @apiNote Checks if the String is long
	 * @return boolean
	 */
	public static boolean isLong(CharSequence text) {
		if (text == null)
			return false;
		return isLong(text, 0, text.length());
	}

	/**
	 * @apiNote Checks if the String between certain positions is long
	 * @param start Beginning in String from where the checking will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where checking will be applied (Defaultly length
	 *              of String)
	 * @return boolean
	 */
	public static boolean isLong(CharSequence text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case MINUS:
				if (minus)
					break;
				minus = true;
				continue;
			}
			if (c < 48 || c > 57)
				return false;
			if (totalWidth == 0) {
				if (c == 48) {
					foundZero = true;
					continue;
				}
				onLimit = c == 57;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overLongLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else
						onLimit = false;
			}
			if (++totalWidth > 19 || totalWidth == 19 && overLimit == 1)
				return false;
		}
		return totalWidth > 0 || foundZero;
	}

	// 9,223,372,036,854,775,807-8
	private static int overLongLimit(boolean minus, int pos) {
		switch (pos) {
		case 1:
		case 2:
		case 6:
			return 2;
		case 3:
		case 4:
		case 8:
			return 3;
		case 5:
		case 13:
		case 14:
			return 7;
		case 7:
		case 17:
			return 0;
		case 9:
			return 6;
		case 10:
		case 16:
			return 8;
		case 11:
		case 15:
			return 5;
		case 12:
			return 4;
		case 18:
			return minus ? 8 : 7;
		default:
			break;
		}
		return 9;
	}

	/**
	 * @apiNote Parse integer from the String
	 * @return int
	 */
	public static int getInt(CharSequence text) {
		if (text == null)
			return 0;
		return getInt(text, 0, text.length());
	}

	/**
	 * @apiNote Parse integer from the String between certain positions in the
	 *          String
	 * @param start Beginning in String from where the parse will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where parse will be applied (Defaultly length of
	 *              String)
	 * @return int
	 */
	public static int getInt(CharSequence text, int start, int end) {
		if (text == null)
			return 0;
		return parseNonDecimalNumber((byte) 2, 10, text, start, end).intValue();
	}

	/**
	 * @apiNote Checks if the String is integer
	 * @return boolean
	 */
	public static boolean isInt(CharSequence text) {
		if (text == null)
			return false;
		return isInt(text, 0, text.length());
	}

	/**
	 * @apiNote Checks if the String between certain positions is integer
	 * @param start Beginning in String from where the checking will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where checking will be applied (Defaultly length
	 *              of String)
	 * @return boolean
	 */
	public static boolean isInt(CharSequence text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case PLUS:
				if (totalWidth == 0 && !foundZero)
					continue;
			case MINUS:
				minus = !minus;
				continue;
			}
			if (c < 48 || c > 57)
				return false;
			if (totalWidth == 0) {
				if (c == 48) {
					foundZero = true;
					continue;
				}
				onLimit = c == 50;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overIntLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else
						onLimit = false;
			}
			if (++totalWidth > 10 || totalWidth == 10 && overLimit == 1)
				return false;
		}
		return totalWidth > 0 || foundZero;
	}

	// -2,147,483,647-8
	private static int overIntLimit(boolean minus, int pos) {
		switch (pos) {
		case 1:
			return 1;
		case 2:
		case 4:
		case 8:
			return 4;
		case 3:
			return 7;
		case 5:
			return 8;
		case 6:
			return 3;
		case 7:
			return 6;
		case 9:
			return minus ? 8 : 7;
		default:
			break;
		}
		return 2;
	}

	/**
	 * @apiNote Checks if the String is float
	 * @return boolean
	 */
	public static boolean isFloat(CharSequence text) {
		return isDouble(text);
	}

	/**
	 * @apiNote Checks if the String between certain positions is float
	 * @param start Beginning in String from where the checking will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where checking will be applied (Defaultly length
	 *              of String)
	 * @return boolean
	 */
	public static boolean isFloat(CharSequence text, int start, int end) {
		return isDouble(text, start, end);
	}

	/**
	 * @apiNote Parse float from the String
	 * @return float
	 */
	public static float getFloat(CharSequence text) {
		if (text == null)
			return 0;
		return getFloat(text, 0, text.length());
	}

	/**
	 * @apiNote Parse float from the String between certain positions in the String
	 * @param start Beginning in String from where the parse will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where parse will be applied (Defaultly length of
	 *              String)
	 * @return float
	 */
	public static float getFloat(CharSequence text, int start, int end) {
		if (text == null)
			return 0;
		return parseDecimalNumber(true, (short) 39, text, start, end).floatValue();
	}

	/**
	 * @apiNote Checks if the String is byte
	 * @return boolean
	 */
	public static boolean isByte(CharSequence text) {
		if (text == null)
			return false;
		return isByte(text, 0, text.length());
	}

	/**
	 * @apiNote Checks if the String between certain positions is byte
	 * @param start Beginning in String from where the checking will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where checking will be applied (Defaultly length
	 *              of String)
	 * @return boolean
	 */
	public static boolean isByte(CharSequence text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case PLUS:
				if (totalWidth == 0 && !foundZero)
					continue;
			case MINUS:
				if (minus)
					minus = false;
				else
					minus = true;
				continue;
			}
			if (c < 48 || c > 57)
				return false;
			if (totalWidth == 0) {
				if (c == 48) {
					foundZero = true;
					continue;
				}
				onLimit = c == 49;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overByteLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else
						onLimit = false;
			}
			if (++totalWidth > 3 || totalWidth == 3 && overLimit == 1)
				return false;
		}
		return totalWidth > 0 || foundZero;
	}

	// 127-8
	private static int overByteLimit(boolean minus, int pos) {
		switch (pos) {
		case 1:
			return 2;
		case 2:
			return minus ? 8 : 7;
		}
		return 1;
	}

	/**
	 * @apiNote Parse byte from the String
	 * @return byte
	 */
	public static byte getByte(CharSequence text) {
		if (text == null)
			return 0;
		return getByte(text, 0, text.length());
	}

	/**
	 * @apiNote Parse byte from the String between certain positions in the String
	 * @param start Beginning in String from where the parse will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where parse will be applied (Defaultly length of
	 *              String)
	 * @return byte
	 */
	public static byte getByte(CharSequence text, int start, int end) {
		if (text == null)
			return 0;
		return parseNonDecimalNumber((byte) 0, 3, text, start, end).byteValue();
	}

	/**
	 * @apiNote Checks if the String is short
	 * @return boolean
	 */
	public static boolean isShort(CharSequence text) {
		if (text == null)
			return false;
		return isShort(text, 0, text.length());
	}

	/**
	 * @apiNote Checks if the String between certain positions is short
	 * @param start Beginning in String from where the checking will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where checking will be applied (Defaultly length
	 *              of String)
	 * @return boolean
	 */
	public static boolean isShort(CharSequence text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case PLUS:
				if (totalWidth == 0 && !foundZero)
					continue;
			case MINUS:
				if (minus)
					minus = false;
				else
					minus = true;
				continue;
			}
			if (c < 48 || c > 57)
				return false;
			if (totalWidth == 0) {
				if (c == 48) {
					foundZero = true;
					continue;
				}
				onLimit = c == 51;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overShortLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else
						onLimit = false;
			}
			if (++totalWidth > 5 || totalWidth == 5 && overLimit == 1)
				return false;
		}
		return totalWidth > 0 || foundZero;
	}

	// 32,767-8
	private static int overShortLimit(boolean minus, int pos) {
		switch (pos) {
		case 1:
			return 2;
		case 2:
			return 7;
		case 3:
			return 6;
		case 4:
			return minus ? 8 : 7;
		}
		return 3;
	}

	/**
	 * @apiNote Parse short from the String
	 * @return short
	 */
	public static short getShort(CharSequence text) {
		if (text == null)
			return 0;
		return getShort(text, 0, text.length());
	}

	/**
	 * @apiNote Parse short from the String between certain positions in the String
	 * @param start Beginning in String from where the parse will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where parse will be applied (Defaultly length of
	 *              String)
	 * @return short
	 */
	public static short getShort(CharSequence text, int start, int end) {
		if (text == null)
			return 0;
		return parseNonDecimalNumber((byte) 1, 5, text, start, end).shortValue();
	}

	/**
	 * @apiNote Checks if the String is Number
	 * @return boolean
	 */
	public static boolean isNumber(CharSequence text) {
		return isInt(text) || isLong(text) || isDouble(text);
	}

	/**
	 * @apiNote Checks if the String between certain positions is Number
	 * @param start Beginning in String from where the checking will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where checking will be applied (Defaultly length
	 *              of String)
	 * @return boolean
	 */
	public static boolean isNumber(CharSequence text, int start, int end) {
		return isInt(text, start, end) || isLong(text, start, end) || isDouble(text, start, end);
	}

	/**
	 * @apiNote Parse Number from the String
	 * @return Number
	 */
	public static Number getNumber(CharSequence text) {
		if (text == null)
			return 0;
		return getNumber(text, 0, text.length());
	}

	/**
	 * @apiNote Parse Number from the String between certain positions in the String
	 * @param start Beginning in String from where the parse will be applied
	 *              (Defaulty 0)
	 * @param end   End in String where parse will be applied (Defaultly length of
	 *              String)
	 * @return Number
	 */
	public static Number getNumber(CharSequence text, int start, int end) {
		if (text == null || text.length() == 0)
			return null;
		int dotAt = -1;
		boolean numberOnPrefix = false;
		for (int i = start; i < text.length() && i < end; ++i) {
			char c = text.charAt(i);
			if (c >= '0' && c <= '9')
				numberOnPrefix = true;
			if (c == DOT) {
				dotAt = i;
				break;
			}
			if (c == MINUS || c == PLUS)
				if (numberOnPrefix)
					return null; // This is math
		}
		if (dotAt == -1) {
			if (isInt(text, start, end))
				return getInt(text, start, end);
			if (isLong(text, start, end))
				return getLong(text, start, end);
		}
		if (isDouble(text, start, end))
			return getDouble(text, start, end);
		return null;
	}

	private static Number parseDecimalNumber(boolean isFloat, short maxTotalWidth, CharSequence text, int start,
			int end) {
		double result = 0;
		int decimal = 0;
		int exponent = 0;
		boolean minusExponent = false;

		boolean minus = false;
		boolean hasDecimal = false;
		boolean hasExponent = false;
		byte exponentSymbol = 0;

		short totalWidth = 0;

		charsLoop: for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case PLUS:
				if (totalWidth == 0)
					continue;
			case MINUS:
				if (minus) {
					if (text.charAt(i - 1) == MINUS || text.charAt(i - 1) == PLUS)
						minus = false;
					if (hasExponent && exponent == 0) {
						minusExponent = true;
						continue;
					}
					break charsLoop;
				}
				if (hasExponent && exponent == 0) {
					minusExponent = true;
					continue;
				}
				minus = true;
				continue;
			case SMALL_E:
			case BIG_E:
				if (hasExponent)
					break charsLoop;
				hasExponent = true;
				exponentSymbol = 1;
				continue;
			case DOT:
			case COMMA:
				if (hasDecimal || hasExponent)
					break charsLoop;
				hasDecimal = true;
				continue;
			}
			if (c < 48 || c > 57) {
				if (totalWidth == 0) {
					if (c == 'N' && i + 3 <= end)
						if (text.charAt(i + 1) == 'a' && text.charAt(i + 2) == 'N')
							return isFloat ? Float.NaN : Double.NaN;
					if (c == 'I' && i + 8 <= end)
						if (text.charAt(i + 1) == 'n' && text.charAt(i + 2) == 'f' && text.charAt(i + 3) == 'i'
						&& text.charAt(i + 4) == 'n' && text.charAt(i + 5) == 'i' && text.charAt(i + 6) == 't'
						&& text.charAt(i + 7) == 'y')
							return isFloat ? minus ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY
									: minus ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
				}
				continue;
			}
			if (!hasDecimal && totalWidth == 0 && c == 48)
				continue;
			int digit = c - 48;
			if (++totalWidth > maxTotalWidth)
				return isFloat ? minus ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY
						: minus ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

			if (hasExponent) {
				exponent = exponent * 10 + digit;
				exponentSymbol = 0;
			} else {
				result = result * 10 + digit;
				if (hasDecimal)
					++decimal;
			}
		}
		return isFloat ? (float) calculateResult(result, decimal, exponent, minusExponent, minus, exponentSymbol)
				: calculateResult(result, decimal, exponent, minusExponent, minus, exponentSymbol);
	}

	private static Number parseNonDecimalNumber(byte type, int totalDigits, CharSequence text, int start, int end) {
		if (text == null)
			return 0;
		Number result = 0;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case PLUS:
				if (totalWidth == 0)
					continue;
				else
					break;
			case MINUS:
				if (minus) {
					if (text.charAt(i - 1) == MINUS || text.charAt(i - 1) == PLUS) {
						minus = false;
						switch (type) {
						case 0: // Byte
							result = -result.byteValue();
							break;
						case 1: // Short
							result = -result.shortValue();
							break;
						case 2: // Integer
							result = -result.intValue();
							break;
						case 3: // Long
							result = -result.longValue();
							break;
						}
						continue;
					}
					break;
				}
				minus = true;
				switch (type) {
				case 0: // Byte
					result = -result.byteValue();
					break;
				case 1: // Short
					result = -result.shortValue();
					break;
				case 2: // Integer
					result = -result.intValue();
					break;
				case 3: // Long
					result = -result.longValue();
					break;
				}
				continue;
			}
			if (c < 48 || c > 57)
				continue;
			if (totalWidth == 0) {
				if (c == 48)
					continue;
				onLimit = isOnLimit(type, c);
			}
			int digit = c - 48;

			if (onLimit) {
				limit = checkOverLimit(type, minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else
						onLimit = false;
			}
			if (++totalWidth > totalDigits || totalWidth == totalDigits && overLimit == 1)
				return getInfinityOf(type, minus);

			result = multiplyTen(type, result, minus ? -digit : digit);
		}
		return result;
	}

	private static boolean isOnLimit(byte type, char c) {
		switch (type) {
		case 0: // Byte
			return c == 49;
		case 1: // Short
			return c == 51;
		case 2: // Integer
			return c == 50;
		case 3: // Long
			return c == 57;
		}
		return false;
	}

	private static Number getInfinityOf(byte type, boolean minus) {
		switch (type) {
		case 0: // Byte
			return minus ? Byte.MIN_VALUE : Byte.MAX_VALUE;
		case 1: // Short
			return minus ? Short.MIN_VALUE : Short.MAX_VALUE;
		case 2: // Integer
			return minus ? Integer.MIN_VALUE : Integer.MAX_VALUE;
		case 3: // Long
			return minus ? Long.MIN_VALUE : Long.MAX_VALUE;
		}
		return 0;
	}

	private static Number multiplyTen(byte type, Number result, int digit) {
		switch (type) {
		case 0: // Byte
			return result.byteValue() * 10 + digit;
		case 1: // Short
			return result.shortValue() * 10 + digit;
		case 2: // Integer
			return result.intValue() * 10 + digit;
		case 3: // Long
			return result.longValue() * 10 + digit;
		}
		return 0;
	}

	private static int checkOverLimit(byte type, boolean minus, int totalWidth) {
		switch (type) {
		case 0: // Byte
			return overByteLimit(minus, totalWidth);
		case 1: // Short
			return overShortLimit(minus, totalWidth);
		case 2: // Integer
			return overIntLimit(minus, totalWidth);
		case 3: // Long
			return overLongLimit(minus, totalWidth);
		}
		return 0;
	}
}
