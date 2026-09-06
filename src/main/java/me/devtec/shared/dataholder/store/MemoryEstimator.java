package me.devtec.shared.dataholder.store;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

public final class MemoryEstimator {
	private MemoryEstimator() {
	}

	public static long estimate(Object value) {
		if (value == null)
			return 0;
		if (value instanceof ValueRef)
			return ((ValueRef) value).estimatedHeap();
		if (value instanceof CharSequence)
			return 40L + 2L * ((CharSequence) value).length();
		if (value instanceof Number || value instanceof Boolean || value instanceof Character)
			return 24;
		return estimate(value, new IdentityHashMap<>(), 0);
	}

	private static long estimate(Object v, IdentityHashMap<Object, Boolean> seen, int depth) {
		if (v == null)
			return 0;
		if (v instanceof ValueRef)
			return ((ValueRef) v).estimatedHeap();
		if (v instanceof CharSequence)
			return 40L + 2L * ((CharSequence) v).length();
		if (v instanceof Number || v instanceof Boolean || v instanceof Character)
			return 24;
		if (depth > 256 || seen.put(v, Boolean.TRUE) != null)
			throw new IllegalArgumentException("Cyclic or excessively nested Config value");
		long result = 48;
		if (v instanceof Map)
			for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet())
				result += 40 + estimate(e.getKey(), seen, depth + 1) + estimate(e.getValue(), seen, depth + 1);
		else if (v instanceof Collection)
			for (Object x : (Collection<?>) v)
				result += 8 + estimate(x, seen, depth + 1);
		else if (v.getClass().isArray())
			for (int i = 0; i < java.lang.reflect.Array.getLength(v); i++)
				result += 8 + estimate(java.lang.reflect.Array.get(v, i), seen, depth + 1);
		else
			result += 256;
		seen.remove(v);
		return result;
	}
}