package me.devtec.shared;

public class Pair {
	Object key;
	Object value;

	public Pair(Object key, Object value) {
		this.key = key;
		this.value = value;
	}

	public Object getKey() {
		return key;
	}

	public Object getValue() {
		return value;
	}

	@Override
	public String toString() {
		return "Pair{" + key + "=" + value + "}";
	}

	public static Pair of(Object key, Object value) {
		return new Pair(key, value);
	}
}