package me.devtec.shared.utility.colors;

import me.devtec.shared.dataholder.StringContainer;

public class ExclamationFinder implements GradientFinder {

	private transient int i;

	private final StringContainer container;

	private int firstRGB = -1;
	private int secondRGB = -1;

	private int startAt;
	private int endAt;

	// !#rrggbb text !#rrggbb
	public ExclamationFinder(StringContainer container) {
		this.container = container;
	}

	@Override
	public boolean find() {
		if (container.length() <= i)
			return false;

		byte mode = 0;
		byte count = 0;

		int rgb = 0;

		for (; i < container.length(); ++i) {
			char c = container.charAt(i);

			switch (mode) {
			case 0:
				if (c == '!'
				&& i + 7 < container.length()
				&& container.charAt(i + 1) == '#') {

					++i;
					rgb = 0;

					for (byte ic = 1;; ++ic) {
						c = container.charAt(i + ic);

						int hex = hexValue(c);

						if (hex == -1) {
							count = 0;
							rgb = 0;
							break;
						}

						rgb = rgb << 4 | hex;

						if (++count == 6) {
							/*
							 * i points to '#'.
							 *
							 * Content starts 7 chars later:
							 *
							 * #rrggbb
							 *        ^
							 */
							startAt = i + 7;

							/*
							 * Outer for-loop increments i once more.
							 * This makes the next iteration begin
							 * exactly at startAt.
							 */
							i += 6;

							firstRGB = rgb;

							count = 0;
							rgb = 0;
							mode = 1;

							break;
						}
					}
				}

				break;

			case 1:
				if (c == '!'
				&& i + 7 < container.length()
				&& container.charAt(i + 1) == '#') {

					++i;
					rgb = 0;

					for (byte ic = 1;; ++ic) {
						c = container.charAt(i + ic);

						int hex = hexValue(c);

						if (hex == -1) {
							count = 0;
							rgb = 0;
							break;
						}

						rgb = rgb << 4 | hex;

						if (++count == 6) {
							/*
							 * i currently points to '#',
							 * so marker started one char before.
							 */
							endAt = i - 1;

							/*
							 * Move to immediately after:
							 *
							 * !#rrggbb
							 */
							i += 7;

							secondRGB = rgb;

							return true;
						}
					}
				}

				break;
			}
		}

		return false;
	}

	private static int hexValue(char c) {
		if (c >= '0' && c <= '9')
			return c - '0';

		if (c >= 'A' && c <= 'F')
			return c - 'A' + 10;

		if (c >= 'a' && c <= 'f')
			return c - 'a' + 10;

		return -1;
	}

	@Override
	public int getFirstRGB() {
		return firstRGB;
	}

	@Override
	public int getSecondRGB() {
		return secondRGB;
	}

	@Override
	public String getFirstHex() {
		return firstRGB == -1
				? null
						: rgbToHex(firstRGB);
	}

	@Override
	public int getFirstHexLength() {
		return 8;
	}

	@Override
	public String getSecondHex() {
		return secondRGB == -1
				? null
						: rgbToHex(secondRGB);
	}

	@Override
	public int getSecondHexLength() {
		return 8;
	}

	@Override
	public int getStart() {
		return startAt;
	}

	@Override
	public int getEnd() {
		return endAt;
	}

	@Override
	public void skip(int characters) {
		i += characters;
	}

	private static String rgbToHex(int rgb) {
		char[] out = new char[7];

		out[0] = '#';

		out[1] = hexChar(rgb >> 20 & 0xF);
		out[2] = hexChar(rgb >> 16 & 0xF);
		out[3] = hexChar(rgb >> 12 & 0xF);
		out[4] = hexChar(rgb >> 8 & 0xF);
		out[5] = hexChar(rgb >> 4 & 0xF);
		out[6] = hexChar(rgb & 0xF);

		return new String(out);
	}

	private static char hexChar(int value) {
		return (char) (value < 10
				? '0' + value
						: 'a' + value - 10);
	}
}