package me.devtec.shared.utility;

import me.devtec.shared.dataholder.StringContainer;

public class ParseUtils {
	private static final char DOT = '.';
	private static final char COMMA = ',';
	private static final char SPACE = ' ';
	private static final char SMALL_E = 'e';
	private static final char BIG_E = 'E';
	private static final char MINUS = '-';

	/**
	 * @apiNote Parse boolean from the String
	 * @return boolean
	 */
	public static boolean getBoolean(String text) {
		return text == null || text.length() != 4 ? false
				: toLowerCase(text.charAt(0)) == 't' && toLowerCase(text.charAt(1)) == 'r' && toLowerCase(text.charAt(2)) == 'u' && toLowerCase(text.charAt(3)) == SMALL_E;
	}

	/**
	 * @apiNote Checks if the String is Boolean
	 * @return boolean
	 */
	public static boolean isBoolean(String text) {
		if (text == null || text.length() > 5 || text.length() < 4)
			return false;
		if (text.length() == 5)
			return toLowerCase(text.charAt(0)) == 'f' && toLowerCase(text.charAt(1)) == 'a' && toLowerCase(text.charAt(2)) == 'l' && toLowerCase(text.charAt(3)) == 's'
					&& toLowerCase(text.charAt(4)) == SMALL_E;
		// true
		return toLowerCase(text.charAt(0)) == 't' && toLowerCase(text.charAt(1)) == 'r' && toLowerCase(text.charAt(2)) == 'u' && toLowerCase(text.charAt(3)) == SMALL_E;
	}

	private static char toLowerCase(int character) {
		return (char) (character <= 90 ? character + 32 : character);
	}

	/**
	 * @apiNote Parse double from the String
	 * @return double
	 */
	public static double getDouble(StringContainer text) {
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
	public static double getDouble(StringContainer text, int start, int end) {
		if (text == null)
			return 0;

		double result = 0.0;
		int decimal = 0;
		int exponent = 0;
		boolean minusExponent = false;

		boolean minus = false;
		boolean hasDecimal = false;
		boolean hasExponent = false;
		byte exponentSymbol = 0;

		short totalWidth = 0;

		int size = end;
		charsLoop: for (int i = start; i < size; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue charsLoop;
			case MINUS:
				if (minus) {
					if (hasExponent && exponent == 0) {
						minusExponent = true;
						continue charsLoop;
					}
					break charsLoop;
				}
				if (hasExponent && exponent == 0) {
					minusExponent = true;
					continue charsLoop;
				}
				minus = true;
				continue charsLoop;
			case SMALL_E:
			case BIG_E:
				if (hasExponent)
					break charsLoop;
				hasExponent = true;
				exponentSymbol = 1;
				continue charsLoop;
			case DOT:
			case COMMA:
				if (hasDecimal || hasExponent)
					break charsLoop;
				hasDecimal = true;
				continue charsLoop;
			}
			if (c < 48 || c > 57) {
				if (totalWidth == 0) {
					if (c == 'N' && i + 3 <= size)
						if (text.charAt(i + 1) == 'a' && text.charAt(i + 2) == 'N')
							return Double.NaN;
					if (c == 'I' && i + 8 <= size)
						if (text.charAt(i + 1) == 'n' && text.charAt(i + 2) == 'f' && text.charAt(i + 3) == 'i' && text.charAt(i + 4) == 'n' && text.charAt(i + 5) == 'i' && text.charAt(i + 6) == 't'
								&& text.charAt(i + 7) == 'y')
							return minus ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
				}
				continue;
			}
			if (!hasDecimal && totalWidth == 0 && c == 48)
				continue;
			int digit = c - 48;
			if (++totalWidth > 308)
				return minus ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

			if (hasExponent) {
				exponent = exponent * 10 + digit;
				exponentSymbol = 0;
			} else {
				result = result * 10 + digit;
				if (hasDecimal)
					++decimal;
			}
		}
		int range = (minusExponent ? -exponent : exponent) - decimal;
		if (range != 0)
			if (range > 0)
				result *= Math.pow(10, range);
			else
				result /= Math.pow(10, range * -1);
		return exponentSymbol == 0 ? minus ? -result : result : 0;
	}

	/**
	 * @apiNote Parse double from the String
	 * @return double
	 */
	public static double getDouble(String text) {
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
	public static double getDouble(String text, int start, int end) {
		if (text == null)
			return 0;

		double result = 0.0;
		int decimal = 0;
		int exponent = 0;
		boolean minusExponent = false;

		boolean minus = false;
		boolean hasDecimal = false;
		boolean hasExponent = false;
		byte exponentSymbol = 0;

		short totalWidth = 0;

		int size = end;
		charsLoop: for (int i = start; i < size; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue charsLoop;
			case MINUS:
				if (minus) {
					if (hasExponent && exponent == 0) {
						minusExponent = true;
						continue charsLoop;
					}
					break charsLoop;
				}
				if (hasExponent && exponent == 0) {
					minusExponent = true;
					continue charsLoop;
				}
				minus = true;
				continue charsLoop;
			case SMALL_E:
			case BIG_E:
				if (hasExponent)
					break charsLoop;
				hasExponent = true;
				exponentSymbol = 1;
				continue charsLoop;
			case DOT:
			case COMMA:
				if (hasDecimal || hasExponent)
					break charsLoop;
				hasDecimal = true;
				continue charsLoop;
			}
			if (c < 48 || c > 57) {
				if (totalWidth == 0) {
					if (c == 'N' && i + 3 <= size)
						if (text.charAt(i + 1) == 'a' && text.charAt(i + 2) == 'N')
							return Double.NaN;
					if (c == 'I' && i + 8 <= size)
						if (text.charAt(i + 1) == 'n' && text.charAt(i + 2) == 'f' && text.charAt(i + 3) == 'i' && text.charAt(i + 4) == 'n' && text.charAt(i + 5) == 'i' && text.charAt(i + 6) == 't'
								&& text.charAt(i + 7) == 'y')
							return minus ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
				}
				continue;
			}
			if (!hasDecimal && totalWidth == 0 && c == 48)
				continue;
			int digit = c - 48;
			if (++totalWidth > 308)
				return minus ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

			if (hasExponent) {
				exponent = exponent * 10 + digit;
				exponentSymbol = 0;
			} else {
				result = result * 10 + digit;
				if (hasDecimal)
					++decimal;
			}
		}
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
	public static boolean isDouble(StringContainer text) {
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
	public static boolean isDouble(StringContainer text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		short totalWidth = 0;

		boolean hasDecimal = false;
		boolean hasExponent = false;
		byte exponentSymbol = 0;

		int size = end;
		for (int i = start; i < size; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case MINUS:
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
				if (size - 1 == i && (c == 'd' || c == 'f' || c == 'D' || c == 'F'))
					continue;
				if (!foundZero && totalWidth == 0 && c == 'N' && i + 3 <= size)
					return text.charAt(++i) == 'a' && text.charAt(++i) == 'N';
				if (!foundZero && totalWidth == 0 && c == 'I' && i + 8 <= size)
					return text.charAt(++i) == 'n' && text.charAt(++i) == 'f' && text.charAt(++i) == 'i' && text.charAt(++i) == 'n' && text.charAt(++i) == 'i' && text.charAt(++i) == 't'
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
	 * @apiNote Checks if the String is double
	 * @return boolean
	 */
	public static boolean isDouble(String text) {
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
	public static boolean isDouble(String text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		short totalWidth = 0;

		boolean hasDecimal = false;
		boolean hasExponent = false;
		byte exponentSymbol = 0;

		int size = end;
		for (int i = start; i < size; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case MINUS:
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
				if (size - 1 == i && (c == 'd' || c == 'f' || c == 'D' || c == 'F'))
					continue;
				if (!foundZero && totalWidth == 0 && c == 'N' && i + 3 <= size)
					return text.charAt(++i) == 'a' && text.charAt(++i) == 'N';
				if (!foundZero && totalWidth == 0 && c == 'I' && i + 8 <= size)
					return text.charAt(++i) == 'n' && text.charAt(++i) == 'f' && text.charAt(++i) == 'i' && text.charAt(++i) == 'n' && text.charAt(++i) == 'i' && text.charAt(++i) == 't'
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
	public static long getLong(StringContainer text) {
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
	public static long getLong(StringContainer text, int start, int end) {
		if (text == null)
			return 0;
		long result = 0;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case MINUS:
				if (minus)
					break;
				minus = true;
				result = -result;
				continue;
			}
			if (c < 48 || c > 57)
				continue;
			if (totalWidth == 0) {
				if (c == 48)
					continue;
				onLimit = c == 57;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overLongLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 19 || totalWidth == 19 && overLimit == 1)
				return 0;

			result = result * 10 + (minus ? -digit : digit);
		}
		return result;
	}

	/**
	 * @apiNote Parse long from the String
	 * @return long
	 */
	public static long getLong(String text) {
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
	public static long getLong(String text, int start, int end) {
		if (text == null)
			return 0;
		long result = 0;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case MINUS:
				if (minus)
					break;
				minus = true;
				result = -result;
				continue;
			}
			if (c < 48 || c > 57)
				continue;
			if (totalWidth == 0) {
				if (c == 48)
					continue;
				onLimit = c == 57;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overLongLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 19 || totalWidth == 19 && overLimit == 1)
				return 0;

			result = result * 10 + (minus ? -digit : digit);
		}
		return result;
	}

	/**
	 * @apiNote Checks if the String is long
	 * @return boolean
	 */
	public static boolean isLong(StringContainer text) {
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
	public static boolean isLong(StringContainer text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

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
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 19 || totalWidth == 19 && overLimit == 1)
				return false;
		}
		return totalWidth > 0 || foundZero;
	}

	/**
	 * @apiNote Checks if the String is long
	 * @return boolean
	 */
	public static boolean isLong(String text) {
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
	public static boolean isLong(String text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

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
					else if (digit < limit)
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
	public static int getInt(StringContainer text) {
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
	public static int getInt(StringContainer text, int start, int end) {
		if (text == null)
			return 0;
		int result = 0;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case MINUS:
				if (minus)
					break;
				minus = true;
				result = -result;
				continue;
			}
			if (c < 48 || c > 57)
				continue;
			if (totalWidth == 0) {
				if (c == 48)
					continue;
				onLimit = c == 50;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overIntLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 10 || totalWidth == 10 && overLimit == 1)
				return 0;

			result = result * 10 + (minus ? -digit : digit);
		}
		return result;
	}

	/**
	 * @apiNote Parse integer from the String
	 * @return int
	 */
	public static int getInt(String text) {
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
	public static int getInt(String text, int start, int end) {
		if (text == null)
			return 0;
		int result = 0;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case MINUS:
				if (minus)
					break;
				minus = true;
				result = -result;
				continue;
			}
			if (c < 48 || c > 57)
				continue;
			if (totalWidth == 0) {
				if (c == 48)
					continue;
				onLimit = c == 50;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overIntLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 10 || totalWidth == 10 && overLimit == 1)
				return 0;

			result = result * 10 + (minus ? -digit : digit);
		}
		return result;
	}

	/**
	 * @apiNote Checks if the String is integer
	 * @return boolean
	 */
	public static boolean isInt(StringContainer text) {
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
	public static boolean isInt(StringContainer text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

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
				onLimit = c == 50;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overIntLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 10 || totalWidth == 10 && overLimit == 1)
				return false;
		}
		return totalWidth > 0 || foundZero;
	}

	/**
	 * @apiNote Checks if the String is integer
	 * @return boolean
	 */
	public static boolean isInt(String text) {
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
	public static boolean isInt(String text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

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
				onLimit = c == 50;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overIntLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
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
	public static boolean isFloat(String text) {
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
	public static boolean isFloat(String text, int start, int end) {
		return isDouble(text, start, end);
	}

	/**
	 * @apiNote Parse float from the String
	 * @return float
	 */
	public static float getFloat(String text) {
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
	public static float getFloat(String text, int start, int end) {
		if (text == null)
			return 0;

		float result = 0;
		int decimal = 0;
		int exponent = 0;
		boolean minusExponent = false;

		boolean minus = false;
		boolean hasDecimal = false;
		boolean hasExponent = false;
		byte exponentSymbol = 0;

		short totalWidth = 0;

		int size = end;
		charsLoop: for (int i = start; i < size; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue charsLoop;
			case MINUS:
				if (minus) {
					if (hasExponent && exponent == 0) {
						minusExponent = true;
						continue charsLoop;
					}
					break charsLoop;
				}
				if (hasExponent && exponent == 0) {
					minusExponent = true;
					continue charsLoop;
				}
				minus = true;
				continue charsLoop;
			case SMALL_E:
			case BIG_E:
				if (hasExponent)
					break charsLoop;
				hasExponent = true;
				exponentSymbol = 1;
				continue charsLoop;
			case DOT:
			case COMMA:
				if (hasDecimal || hasExponent)
					break charsLoop;
				hasDecimal = true;
				continue charsLoop;
			}
			if (c < 48 || c > 57) {
				if (totalWidth == 0) {
					if (c == 'N' && i + 3 <= size)
						if (text.charAt(i + 1) == 'a' && text.charAt(i + 2) == 'N')
							return Float.NaN;
					if (c == 'I' && i + 8 <= size)
						if (text.charAt(i + 1) == 'n' && text.charAt(i + 2) == 'f' && text.charAt(i + 3) == 'i' && text.charAt(i + 4) == 'n' && text.charAt(i + 5) == 'i' && text.charAt(i + 6) == 't'
								&& text.charAt(i + 7) == 'y')
							return minus ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
				}
				continue;
			}
			if (!hasDecimal && totalWidth == 0 && c == 48)
				continue;
			int digit = c - 48;
			if (++totalWidth > 39)
				return minus ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;

			if (hasExponent) {
				exponent = exponent * 10 + digit;
				exponentSymbol = 0;
			} else {
				result = result * 10 + digit;
				if (hasDecimal)
					++decimal;
			}
		}
		int range = (minusExponent ? -exponent : exponent) - decimal;
		if (range != 0)
			if (range > 0)
				result *= Math.pow(10, range);
			else
				result /= Math.pow(10, range * -1);
		return exponentSymbol == 0 ? minus ? -result : result : 0;
	}

	/**
	 * @apiNote Checks if the String is byte
	 * @return boolean
	 */
	public static boolean isByte(String text) {
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
	public static boolean isByte(String text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

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
				onLimit = c == 49;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overByteLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
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
	public static byte getByte(String text) {
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
	public static byte getByte(String text, int start, int end) {
		if (text == null)
			return 0;
		byte result = 0;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case MINUS:
				if (minus)
					break;
				minus = true;
				result = (byte) -result;
				continue;
			}
			if (c < 48 || c > 57)
				continue;
			if (totalWidth == 0) {
				if (c == 48)
					continue;
				onLimit = c == 51;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overByteLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 5 || totalWidth == 5 && overLimit == 1)
				return 0;

			result = (byte) (result * 10 + (minus ? -digit : digit));
		}
		return result;
	}

	/**
	 * @apiNote Checks if the String is short
	 * @return boolean
	 */
	public static boolean isShort(String text) {
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
	public static boolean isShort(String text, int start, int end) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

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
				onLimit = c == 51;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overShortLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
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
	public static short getShort(String text) {
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
	public static short getShort(String text, int start, int end) {
		if (text == null)
			return 0;
		short result = 0;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = start; i < end; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case SPACE:
				continue;
			case MINUS:
				if (minus)
					break;
				minus = true;
				result = (short) -result;
				continue;
			}
			if (c < 48 || c > 57)
				continue;
			if (totalWidth == 0) {
				if (c == 48)
					continue;
				onLimit = c == 51;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overShortLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 5 || totalWidth == 5 && overLimit == 1)
				return 0;

			result = (short) (result * 10 + (minus ? -digit : digit));
		}
		return result;
	}

	/**
	 * @apiNote Checks if the String is Number
	 * @return boolean
	 */
	public static boolean isNumber(String text) {
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
	public static boolean isNumber(String text, int start, int end) {
		return isInt(text, start, end) || isLong(text, start, end) || isDouble(text, start, end);
	}

	/**
	 * @apiNote Parse Number from the String
	 * @return Number
	 */
	public static Number getNumber(String text) {
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
	public static Number getNumber(String text, int start, int end) {
		if (text == null || text.isEmpty())
			return null;
		int dotAt = text.indexOf(DOT, start);
		if (dotAt == -1 || dotAt > end) {
			if (isInt(text, start, end))
				return getInt(text, start, end);
			if (isLong(text, start, end))
				return getLong(text, start, end);
		}
		if (isDouble(text, start, end))
			return getDouble(text, start, end);
		return null;
	}

	/**
	 * @apiNote Parse Number from the String
	 * @return Number
	 */
	public static Number getNumber(StringContainer text) {
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
	public static Number getNumber(StringContainer text, int start, int end) {
		if (text == null || text.isEmpty())
			return null;
		int dotAt = text.indexOf(DOT, start);
		if (dotAt == -1 || dotAt > end) {
			if (isInt(text, start, end))
				return getInt(text, start, end);
			if (isLong(text, start, end))
				return getLong(text, start, end);
		}
		if (isDouble(text, start, end))
			return getDouble(text, start, end);
		return null;
	}
}
