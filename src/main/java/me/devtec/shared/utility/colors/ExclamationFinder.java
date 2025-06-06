package me.devtec.shared.utility.colors;

import me.devtec.shared.dataholder.StringContainer;

public class ExclamationFinder implements GradientFinder {

	private transient int i;
	private final StringContainer container;
	// Match
	private String firstHex;
	private int firstHexLength;
	private String secondHex;
	private int secondHexLength;
	private int startAt;
	private int endAt;

	// !#rrggbb text !#rrggbb
	public ExclamationFinder(StringContainer container) {
		this.container = container;
	}

	@Override
	public boolean find() {
		if (container.length() <= i) {
			return false;
		}
		byte mode = 0;
		byte count = 0;
		for (; i < container.length(); ++i) {
			char c = container.charAt(i);
			switch (mode) {
			case 0:
                if (c == '!') {
                    if (i + 7 < container.length() && container.charAt(i + 1) == '#') {
                        ++i;
                        for (byte ic = 1; true; ++ic) {
                            c = container.charAt(i + ic);
                            if ((c < 64 || c > 70) && (c < 97 || c > 102) && (c < 48 || c > 57)) {
                                count = 0;
                                break;
                            }
                            if (++count == 6) {
                                i += 7;
                                startAt = i;
                                count = 0;
                                mode = 1; // looking for second
                                firstHex = container.substring(startAt - 7, startAt);
                                firstHexLength = 8;
                                break;
                            }
                        }
                    }
                }
				break;
			case 1:
                if (c == '!') {
                    if (i + 7 < container.length() && container.charAt(i + 1) == '#') {
                        ++i;
                        for (byte ic = 1; true; ++ic) {
                            c = container.charAt(i + ic);
                            if ((c < 64 || c > 70) && (c < 97 || c > 102) && (c < 48 || c > 57)) {
                                count = 0;
                                break;
                            }
                            if (++count == 6) {
                                endAt = i - 1;
                                i += 7;
                                secondHex = container.substring(endAt + 1, endAt + 8);
                                secondHexLength = 8;
                                return true;
                            }
                        }
                    }
                }
				break;
			}
		}
		return false;
	}

	@Override
	public String getFirstHex() {
		return firstHex;
	}

	@Override
	public int getFirstHexLength() {
		return firstHexLength;
	}

	@Override
	public String getSecondHex() {
		return secondHex;
	}

	@Override
	public int getSecondHexLength() {
		return secondHexLength;
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

}
