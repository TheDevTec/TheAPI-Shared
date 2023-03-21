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

	public Object setKey(Object key) {
		Object prev = this.key;
		this.key = key;
		return prev;
	}

	public Object getValue() {
		return value;
	}

	public Object setValue(Object value) {
		Object prev = this.value;
		this.value = value;
		return prev;
	}

	@Override
	public String toString() {
		return "Pair{" + key + "=" + value + "}";
	}

	public static Pair of(Object key, Object value) {
		return new Pair(key, value);
	}
}