package me.devtec.shared.utility.colors;

public class Branch {
	private char key;
	private Branch[] branches;
	private String value;

	public Branch(char key) {
		this.key = key;
	}

	public char getKey() {
		return key;
	}

	public Branch[] getBranches() {
		return branches;
	}

	public void setBranches(Branch[] branches) {
		this.branches = branches;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public boolean hasValue() {
		return value != null;
	}

	public String getValue() {
		return value;
	}
}
