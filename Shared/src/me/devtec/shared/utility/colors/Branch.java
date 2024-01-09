package me.devtec.shared.utility.colors;

import me.devtec.shared.utility.ColorUtils;

public class Branch {
	private char key;
	private Branch[] branches;
	private String value;
	private boolean init;

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
		if (!init) {
			init = true;
			value = ColorUtils.color.replaceHex(value);
		}
		return value;
	}
}
