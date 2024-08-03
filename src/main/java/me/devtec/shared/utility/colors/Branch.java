package me.devtec.shared.utility.colors;

public class Branch {
	public char c;
	public Branch[] sub;
	public String value;
	public int length;

	public Branch(char c, Branch[] sub) {
		this.c = c;
		this.sub = sub;
	}

	@Override
	public String toString() {
		return "" + c;
	}
}
