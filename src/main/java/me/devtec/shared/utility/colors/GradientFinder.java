package me.devtec.shared.utility.colors;

public interface GradientFinder {
	public boolean find();

	public String getFirstHex();

	public int getFirstHexLength();

	public String getSecondHex();

	public int getSecondHexLength();

	public int getStart();

	public int getEnd();

	public void skip(int characters);
}
