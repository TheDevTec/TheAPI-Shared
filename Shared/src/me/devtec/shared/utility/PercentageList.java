package me.devtec.shared.utility;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PercentageList<V> {
	private final Map<V, Double> map = new ConcurrentHashMap<>();

	public boolean isEmpty() {
		return map.isEmpty();
	}

	public boolean contains(V object) {
		return map.containsKey(object);
	}

	public boolean add(V object, double chance) {
		if (chance <= 0)
			throw new IllegalArgumentException("Chance must be greater than 0");
		map.put(object, chance);
		return true;
	}

	public boolean remove(V object) {
		return map.remove(object) != null;
	}

	public int size() {
		return map.size();
	}

	public double getChance(V object) {
		return map.getOrDefault(object, 0.0);
	}

	public void clear() {
		map.clear();
	}

	public Set<Entry<V, Double>> entrySet() {
		return map.entrySet();
	}

	public Set<V> keySet() {
		return map.keySet();
	}

	public Collection<Double> values() {
		return map.values();
	}

	public V getRandom() {
		if (isEmpty())
			return null;
		if (map.size() == 1)
			return keySet().iterator().next();

		double total = values().stream().mapToDouble(e -> e).sum();
		double value = 0.0;
		double random = StringUtils.randomDouble(total);
		for (Entry<V, Double> obj : entrySet()) {
			double upperBound = value + obj.getValue();
			if (random <= Math.max(value, upperBound) && random >= Math.min(value, upperBound))
				return obj.getKey();
			value = upperBound;
		}
		return null;
	}
}