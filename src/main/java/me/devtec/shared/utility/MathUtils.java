package me.devtec.shared.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
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
		if (range <= 0)
			return min;
		double randomValue = random.nextDouble() * range + min;
		if (randomValue >= max)
			return Math.nextDown(max);
		return randomValue;
	}

	/**
	 * @apiNote Generate random int within limits
	 * @param min Minimum int
	 * @param max Maximum int
	 * @return int
	 */
	public static int randomInt(int min, int max) {
		if (min == max)
			return min;

		boolean isNegative = max < 0;
		if (isNegative) {
			max *= -1;
			min *= -1;
		}

		int range = max - min;
		if (range <= 0)
			throw new IllegalArgumentException("Invalid range: min > max");
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
	 * @param percent     Inserted chance
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
				if (start == -1)
					start = i;
			} else
				switch (c) {
				case '-':
				case '+':
				case '*':
				case '/':
					if (brackets != 0)
						break;
					if (start != -1) {
						char d = swap(prevOperation);
						operation.add(Pair.of(d, d == '-' ? -ParseUtils.getDouble(expression, start, i)
								: ParseUtils.getDouble(expression, start, i)));
						start = -1;
					}
					prevOperation = c;
					minus = false;
					break;
				case '√':
					if (brackets != 0)
						break;
					if (start != -1) {
						char d = swap(prevOperation);
						operation.add(Pair.of(d, d == '-' ? -Math.sqrt(ParseUtils.getDouble(expression, start, i))
								: Math.sqrt(ParseUtils.getDouble(expression, start, i))));
						start = -1;
					}
					minus = prevOperation == '-';
					prevOperation = c;
					break;
				case '^':
					if (brackets != 0)
						break;
					if (start != -1) {
						char d = swap(prevOperation);
						operation.add(Pair.of(c, d == '-' ? -ParseUtils.getDouble(expression, start, i)
								: ParseUtils.getDouble(expression, start, i)));
						start = -1;
					}
					prevOperation = c;
					minus = false;
					break;
				case '!':
					if (brackets != 0)
						break;
					if (start != -1) {
						char d = swap(prevOperation);
						operation.add(Pair.of(d, d == '-' ? -factorial(ParseUtils.getDouble(expression, start, i))
								: factorial(ParseUtils.getDouble(expression, start, i))));
						start = -1;
					} else {
						Pair last = operation.get(operation.size() - 1);
						double result = (double) last.getValue();
						last.setValue(result < 0 ? -factorial(-result) : factorial(result));
					}
					++i;
					minus = false;
					break;
				case '(':
					if (++brackets == 1) {
						if (prevOperation == 0)
							prevOperation = '+';
						if (start != -1) {
							operation.add(Pair.of(prevOperation,
									prevOperation == '-' ? -ParseUtils.getDouble(expression, start, i)
											: ParseUtils.getDouble(expression, start, i)));
							prevOperation = '+';
						}
						start = i + 1;
					}
					break;
				case ')':
					if (--brackets <= 0) {
						switch (prevOperation) {
						case '√':
							operation.add(Pair.of(minus ? '-' : '+', minus ? -Math.sqrt(calculate(expression, start, i))
									: Math.sqrt(calculate(expression, start, i))));
							break;
						default:
							operation.add(Pair.of(prevOperation, prevOperation == '-' ? -calculate(expression, start, i)
									: calculate(expression, start, i)));
							break;
						}
						start = -1;
						prevOperation = '+';
						minus = false;
					}
					break;
				default:
					break;
				}
		}
		if (start != -1)
			operation.add(Pair.of(prevOperation, prevOperation == '-' ? -ParseUtils.getDouble(expression, start, endPos)
					: ParseUtils.getDouble(expression, start, endPos)));
		// √
		ListIterator<Pair> itr = operation.listIterator();
		while (itr.hasNext()) {
			Pair current = itr.next();
			switch ((char) current.getKey()) {
			case '√': {
				current.setValue(Math.sqrt((double) current.getValue()));
				break;
			}
			}
		}
		// ^
		itr = operation.listIterator();
		while (itr.hasNext()) {
			Pair current = itr.next();
			if (itr.hasNext()) {
				Pair pair = itr.next();
				itr.previous();
				switch ((char) current.getKey()) {
				case '^': {
					itr.remove();
					itr.previous();
					minus = (double) current.getValue() < 0;
					double result = Math.pow(minus ? -(double) current.getValue() : (double) current.getValue(),
							(double) pair.getValue());
					current.setValue(minus ? -result : result);
					break;
				}
				}
			}
		}
		// *, /
		itr = operation.listIterator();
		while (itr.hasNext()) {
			Pair current = itr.next();
			if (itr.hasNext()) {
				Pair pair = itr.next();
				itr.previous();
				switch ((char) pair.getKey()) {
				case '*': {
					itr.remove();
					itr.previous();
					current.setValue((double) current.getValue() * (double) pair.getValue());
					break;
				}
				case '/': {
					itr.remove();
					itr.previous();
					current.setValue((double) current.getValue() / (double) pair.getValue());
					break;
				}
				}
			}
		}
		// -, +
		itr = operation.listIterator();
		while (itr.hasNext()) {
			Pair current = itr.next();
			if (itr.hasNext()) {
				Pair pair = itr.next();
				itr.previous();
				switch ((char) pair.getKey()) {
				case '-':
				case '+': {
					itr.remove();
					itr.previous();
					current.setValue((double) current.getValue() + (double) pair.getValue());
					break;
				}
				}
			}
		}
		double result = 0;
		for (Pair pair : operation)
			result += (double) pair.getValue();
		return result;
	}

	private static double factorial(double n) {
		if (n < 0 && Math.floor(n) == n)
			return n;
		++n;
		int g = 7;
		double[] p = { 0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
				-176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6,
				1.5056327351493116e-7 };

		if (n < 0.5)
			return Math.PI / (Math.sin(Math.PI * n) * factorial(1 - n));

		n -= 1;
		double x = p[0];
		for (int i = 1; i < p.length; i++)
			x += p[i] / (n + i);

		double t = n + g + 0.5;
		return Math.sqrt(2 * Math.PI) * Math.pow(t, n + 0.5) * Math.exp(-t) * x;
	}

	private static char swap(char prevOperation) {
		switch (prevOperation) {
		case '-':
		case '+':
		case '*':
		case '/':
		case '^':
		case '√':
		case '!':
			return prevOperation;
		default:
			break;
		}
		return '+';
	}

	public static int getLongLength(long num) {
		if (num == 0)
			return 1;
		return (int) (Math.log10(Math.abs(num)) + 1);
	}
}
