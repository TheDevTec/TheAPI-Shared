package me.devtec.shared.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

public class PercentageList<V> {
	private final List<Entry<V, Double>> entries = Collections.synchronizedList(new ArrayList<>());
	private double totalChance;

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public boolean contains(V object) {
		for (Entry<V, Double> entry : entries)
			if (Objects.equals(entry.getKey(), object))
				return true;
		return false;
	}

	public boolean add(V object, double chance) {
		if (chance <= 0)
			throw new IllegalArgumentException("Chance must be greater than 0");
		for (Entry<V, Double> entry : entries)
			if (Objects.equals(entry.getKey(), object)) {
				totalChance -= entry.setValue(chance);
				totalChance += chance;
				return true;
			}
		entries.add(new Entry<V, Double>() {

			double privateChance = chance;

			@Override
			public V getKey() {
				return object;
			}

			@Override
			public Double getValue() {
				return privateChance;
			}

			@Override
			public Double setValue(Double value) {
				double prev = privateChance;
				privateChance = value;
				return prev;
			}
		});
		totalChance += chance;
		return true;
	}

	public boolean remove(V object) {
		Iterator<Entry<V, Double>> itr = entries.iterator();
		while (itr.hasNext()) {
			Entry<V, Double> entry = itr.next();
			if (Objects.equals(entry.getKey(), object)) {
				itr.remove();
				return true;
			}
		}
		return false;
	}

	public int size() {
		return entries.size();
	}

	public double getChance(V object) {
		for (Entry<V, Double> entry : entries)
			if (Objects.equals(entry.getKey(), object))
				return entry.getValue();
		return 0;
	}

	public void clear() {
		entries.clear();
		totalChance = 0;
	}

	public List<V> keySet() {
		List<V> keys = new ArrayList<>(entries.size());
		for (Entry<V, Double> entry : entries)
			keys.add(entry.getKey());
		return keys;
	}

	public List<Double> values() {
		List<Double> chances = new ArrayList<>(entries.size());
		for (Entry<V, Double> entry : entries)
			chances.add(entry.getValue());
		return chances;
	}

	public List<Entry<V, Double>> entrySet() {
		return entries;
	}

	public V getRandom() {
		if (isEmpty())
			return null;
		if (entries.size() == 1)
			return entries.get(0).getKey();

		double random = MathUtils.randomDouble(totalChance);
		double value = 0.0;
		for (int i = 0; i < entries.size(); i++) {
			Entry<V, Double> entry = entries.get(i);
			double upperBound = value + entry.getValue();
			if (random <= upperBound)
				return entry.getKey();
			value = upperBound;
		}
		// If we get here, it means that random was greater than totalChance, so we
		// return the last key
		return entries.get(entries.size() - 1).getKey();
	}
}