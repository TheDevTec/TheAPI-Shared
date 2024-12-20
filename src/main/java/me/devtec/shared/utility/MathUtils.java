package me.devtec.shared.utility;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import me.devtec.shared.Pair;

public class MathUtils {

	public static final Random random = new Random();

	/**
	 * @apiNote The method first casts the double value to an int using a simple
	 *          cast operation. If the cast value is equal to the original double
	 *          value, it is returned directly as the int value. If not, the method
	 *          calculates the difference between the cast value and the bitwise
	 *          representation of the original double value. The result of this
	 *          calculation is either 0 or 1, which is then subtracted from the cast
	 *          value and returned as the final result.
	 * @return int
	 */
	public static int floor(double value) {
		int floor = (int) value;
		return floor == value ? floor : floor - (int) (Double.doubleToRawLongBits(value) >>> 63);
	}

	/**
	 * @apiNote The method takes a double value as input and returns a long value
	 *          that represents the floor or ceiling of the input value. If the
	 *          input value is less than the nearest integer value, the floor value
	 *          is returned, otherwise, the ceiling value is returned. The method
	 *          first casts the input value to a long and assigns it to the variable
	 *          l. If the input value is less than the casted long value, then it
	 *          subtracts 1 from the casted long value to return the floor value,
	 *          otherwise, it returns the casted long value which represents the
	 *          ceiling value.
	 * @return long
	 */
	public static long floorOrCeilLong(double value) {
		long l = (long) value;
		return value < l ? l - 1L : l;
	}

	/**
	 * @apiNote The method always returns a non-negative integer that is the
	 *          smallest integer greater than or equal to the absolute value of the
	 *          input value.
	 * @return int
	 */
	public static int absRoundUp(double value) {
		return (int) (value >= 0.0D ? value : -value + 1.0D);
	}

	/**
	 * @apiNote The method checks if number has decimals (number.decimals)
	 * @return boolean
	 */
	public static boolean hasDecimal(double num) {
		return num - floor(num) > 0;
	}

	/**
	 * @apiNote Generate random int within limits
	 * @param max Maximum int
	 */
	public static int randomInt(int max) {
		return randomInt(0, max);
	}

	/**
	 * @apiNote Generate random double within limits
	 * @param max Maximum double
	 */
	public static double randomDouble(double max) {
		return randomDouble(0, max);
	}

	/**
	 * @apiNote Generate random double within limits
	 * @param min Minimum double
	 * @param max Maximum double
	 * @return double
	 */
	public static double randomDouble(double min, double max) {
		double range = max - min;
		if (range <= 0) {
			return min;
		}
		double randomValue = random.nextDouble() * range + min;
		if (randomValue >= max) {
			return Math.nextDown(max);
		}
		return randomValue;
	}

	/**
	 * @apiNote Generate random int within limits
	 * @param min Minimum int
	 * @param max Maximum int
	 * @return int
	 */
	public static int randomInt(int min, int max) {
		if (min == max) {
			return min;
		}

		boolean isNegative = max < 0;
		if (isNegative) {
			max *= -1;
			min *= -1;
		}

		int range = max - min;
		if (range <= 0) {
			throw new IllegalArgumentException("Invalid range: min > max");
		}
		int randomValue = (int) (random.nextDouble() * range) + min;
		return isNegative ? randomValue * -1 : randomValue;
	}

	/**
	 * @apiNote Generates a random chance and compares it to the specified chance
	 * @param percent Inserted chance
	 */
	public static boolean checkProbability(double percent) {
		return checkProbability(percent, 100);
	}

	/**
	 * @apiNote Generates a random chance and compares it to the specified chance
	 * @param percent       Inserted chance
	 * @param basedChance Based chance
	 */
	public static boolean checkProbability(double percent, double basedChance) {
		return randomDouble(basedChance) <= percent;
	}

	/**
	 * @apiNote This method takes a String argument that represents a mathematical
	 *          expression and returns a double value that represents the result of
	 *          evaluating the expression. The expression can contain the usual
	 *          arithmetic operators (+, -, *, /) as well as parentheses to control
	 *          the order of operations. The method uses a stack to keep track of
	 *          intermediate results and operators, and follows the standard rules
	 *          of operator precedence and associativity.
	 * @return double
	 */
	public static double calculate(String expression) {
		return calculate(expression, 0, expression.length());
	}

	/**
	 * @apiNote This method takes a String argument that represents a mathematical
	 *          expression and returns a double value that represents the result of
	 *          evaluating the expression. The expression can contain the usual
	 *          arithmetic operators (+, -, *, /) as well as parentheses to control
	 *          the order of operations. The method uses a stack to keep track of
	 *          intermediate results and operators, and follows the standard rules
	 *          of operator precedence and associativity.
	 * @param startPos Beginning in String from where the math will be applied
	 *                 (Defaulty 0)
	 * @param endPos   End in String where math will be applied (Defaultly length of
	 *                 String)
	 * @return double
	 */
	public static double calculate(String expression, int startPos, int endPos) {
		List<Pair> operation = new ArrayList<>();
		int start = -1;
		int brackets = 0;

		char prevOperation = 0;
		boolean minus = false;
		for (int i = startPos; i < endPos; ++i) {
			char c = expression.charAt(i);
			if (brackets == 0 && c >= '0' && c <= '9' || c == '.' || c == ',' || c == 'e' || c == 'E') {
				if (start == -1) {
					start = i;
				}
			} else {
				switch (c) {
				case '-':
				case '+':
				case '*':
				case '/':
					if (brackets != 0) {
						break;
					}
					if (start == -1) {
						if (prevOperation != '-' ? c == '-' : c == '+') {
							minus = true;
						} else if (c == '-') {
							minus = false;
						}
					}

					if (start != -1) {
						operation.add(Pair.of(c, minus ? -ParseUtils.getDouble(expression, start, i) : ParseUtils.getDouble(expression, start, i)));
						minus = c == '-';
						start = -1;
					}
					prevOperation = c;
					break;
				case '(':
					if (++brackets == 1) {
						if (prevOperation == 0) {
							prevOperation = '+';
						}
						if (start != -1) {
							operation.add(Pair.of(prevOperation, minus ? -ParseUtils.getDouble(expression, start, i) : ParseUtils.getDouble(expression, start, i)));
							prevOperation = '+';
							minus = false;
						}
						start = i + 1;
					}
					break;
				case ')':
					if (--brackets <= 0) {
						operation.add(Pair.of(prevOperation, minus ? -calculate(expression, start, i) : calculate(expression, start, i)));
						start = -1;
						prevOperation = '+';
						minus = false;
					}
					break;
				default:
					break;
				}
			}
		}
		if (start != -1) {
			operation.add(Pair.of(prevOperation, minus ? -ParseUtils.getDouble(expression, start, endPos) : ParseUtils.getDouble(expression, start, endPos)));
		}
		// *, /
		Iterator<Pair> itr = operation.iterator();
		while (itr.hasNext()) {
			Pair pair = itr.next();
			switch ((char) pair.getKey()) {
				case '*': {
					itr.remove();
					if(itr.hasNext()) {
						Pair current = itr.next();
						current.setValue((double) pair.getValue() * (double) current.getValue());
					}
					break;
				}
				case '/': {
					itr.remove();
					if(itr.hasNext()) {
						Pair current = itr.next();
						current.setValue((double) pair.getValue() / (double) current.getValue());
					}
					break;
				}
			}
		}
		// -, +
		itr = operation.iterator();
		while (itr.hasNext()) {
			Pair pair = itr.next();
			switch ((char) pair.getKey()) {
				case '-':
				case '+':
					if(itr.hasNext()) {
						Pair current = itr.next();
						if ((char) current.getKey() == '/') {
							current.setValue((double) pair.getValue() / (double) current.getValue());
						} else if ((char) current.getKey() == '*') {
							current.setValue((double) pair.getValue() * (double) current.getValue());
						} else {
							current.setValue((double) current.getValue() + (double) pair.getValue());
						}
					}
					break;
			}
		}
		double result = 0;
        for (Pair pair : operation) {
            result += (double) pair.getValue();
        }
		return result;
	}

	public static int getLongLength(long num) {
		if (num == 0) {
			return 1;
		}
		return (int) (Math.log10(Math.abs(num)) + 1);
	}
}
