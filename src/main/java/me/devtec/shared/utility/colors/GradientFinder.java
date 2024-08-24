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
}
