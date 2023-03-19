package me.devtec.shared.utility;

import java.util.ArrayList;
import java.util.List;

public class PercentageList<V> {
	private final List<V> keys = new ArrayList<>();
	private final List<Double> values = new ArrayList<>();
	private double totalChance;

	public boolean isEmpty() {
		return keys.isEmpty();
	}

	public boolean contains(V object) {
		return keys.contains(object);
	}

	public boolean add(V object, double chance) {
		if (chance <= 0)
			throw new IllegalArgumentException("Chance must be greater than 0");
		int index = keys.indexOf(object);
		if (index != -1) {
			totalChance -= values.get(index);
			values.set(index, chance);
		} else {
			keys.add(object);
			values.add(chance);
		}
		totalChance += chance;
		return true;
	}

	public boolean remove(V object) {
		int index = keys.indexOf(object);
		if (index != -1) {
			keys.remove(index);
			totalChance -= values.remove(index);
			return true;
		}
		return false;
	}

	public int size() {
		return keys.size();
	}

	public double getChance(V object) {
		int index = keys.indexOf(object);
		if (index != -1)
			return values.get(index);
		return 0;
	}

	public void clear() {
		keys.clear();
		values.clear();
		totalChance = 0;
	}

	public List<V> keySet() {
		return keys;
	}

	public List<Double> values() {
		return values;
	}

	public V getRandom() {
		if (isEmpty())
			return null;
		if (keys.size() == 1)
			return keys.get(0);

		double random = StringUtils.randomDouble(totalChance);
		double value = 0.0;
		for (int i = 0; i < values.size(); i++) {
			double upperBound = value + values.get(i);
			if (random <= upperBound)
				return keys.get(i);
			value = upperBound;
		}
		// If we get here, it means that random was greater than totalChance, so we
		// return the last key
		return keys.get(keys.size() - 1);
	}
}