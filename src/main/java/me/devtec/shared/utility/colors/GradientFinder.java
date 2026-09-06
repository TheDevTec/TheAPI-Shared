package me.devtec.shared.utility.colors;

public interface GradientFinder {

	boolean find();

	String getFirstHex();

	int getFirstHexLength();

	String getSecondHex();

	int getSecondHexLength();

	int getStart();

	int getEnd();

	void skip(int characters);

	default int getFirstRGB() {
		return -1;
	}

	default int getSecondRGB() {
		return -1;
	}
}