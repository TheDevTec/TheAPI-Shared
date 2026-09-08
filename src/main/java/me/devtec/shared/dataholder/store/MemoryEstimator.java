package me.devtec.shared.dataholder.store;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

public final class MemoryEstimator {

	private static final long OBJECT_BASE = 48L;

	private MemoryEstimator() {
	}

	public static long estimate(Object value) {
		return estimate(value, null, 0);
	}

	private static long estimate(Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
		if (value == null)
			return 0;

		if (value instanceof ValueRef)
			return ((ValueRef) value).estimatedHeap();

		if (value instanceof CharSequence)
			return 40L + 2L * ((CharSequence) value).length();

		if (value instanceof Number || value instanceof Boolean || value instanceof Character)
			return 24L;

		/*
		 * Primitive arrays cannot contain cycles.
		 *
		 * Handle them before allocating IdentityHashMap and without
		 * java.lang.reflect.Array.get(), which would box every element.
		 */
		if (value instanceof byte[])
			return primitiveArray(((byte[]) value).length, 1);

		if (value instanceof boolean[])
			return primitiveArray(((boolean[]) value).length, 1);

		if (value instanceof short[])
			return primitiveArray(((short[]) value).length, 2);

		if (value instanceof char[])
			return primitiveArray(((char[]) value).length, 2);

		if (value instanceof int[])
			return primitiveArray(((int[]) value).length, 4);

		if (value instanceof float[])
			return primitiveArray(((float[]) value).length, 4);

		if (value instanceof long[])
			return primitiveArray(((long[]) value).length, 8);

		if (value instanceof double[])
			return primitiveArray(((double[]) value).length, 8);

		if (depth > 256)
			throw new IllegalArgumentException("Cyclic or excessively nested Config value");

		if (seen == null)
			seen = new IdentityHashMap<>(8);

		if (seen.put(value, Boolean.TRUE) != null)
			throw new IllegalArgumentException("Cyclic or excessively nested Config value");

		long result;

		try {
			if (value instanceof Map) {
				Map<?, ?> map = (Map<?, ?>) value;

				result = OBJECT_BASE + 40L * map.size();

				for (Map.Entry<?, ?> entry : map.entrySet()) {
					result += estimate(entry.getKey(), seen, depth + 1);
					result += estimate(entry.getValue(), seen, depth + 1);
				}

				return result;
			}

			if (value instanceof Collection) {
				Collection<?> collection = (Collection<?>) value;

				result = OBJECT_BASE + 8L * collection.size();

				for (Object element : collection)
					result += estimate(element, seen, depth + 1);

				return result;
			}

			if (value instanceof Object[]) {
				Object[] array = (Object[]) value;

				result = OBJECT_BASE + 8L * array.length;

				for (Object element : array)
					result += estimate(element, seen, depth + 1);

				return result;
			}

			/*
			 * Unknown array type. In practice primitive and Object arrays have already been
			 * handled above, but retain a safe fallback.
			 */
			Class<?> type = value.getClass();

			if (type.isArray()) {
				int length = java.lang.reflect.Array.getLength(value);

				result = OBJECT_BASE + 8L * length;

				for (int i = 0; i < length; i++)
					result += estimate(java.lang.reflect.Array.get(value, i), seen, depth + 1);

				return result;
			}

			return OBJECT_BASE + 256L;
		} finally {
			seen.remove(value);
		}
	}

	private static long primitiveArray(int length, int bytesPerElement) {
		long size = OBJECT_BASE + (long) length * bytesPerElement;

		// 8-byte alignment.
		return size + 7L & ~7L;
	}
}