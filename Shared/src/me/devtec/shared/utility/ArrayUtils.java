package me.devtec.shared.utility;

import java.lang.reflect.Array;

@SuppressWarnings("unchecked")
public class ArrayUtils {
	public static <T> T[] sumArrays(T[] first, T... second) {
		if (first == null || first.length == 0)
			return second;
		if (second == null || second.length == 0)
			return first;

		T[] result = newInstance(first.getClass().getComponentType(), first.length + second.length);

		System.arraycopy(first, 0, result, 0, first.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	public static <T> T[] sumArrays(T[]... arrays) {
		if (arrays == null)
			throw new NullPointerException("Array cannot be null");
		if (arrays.length == 0)
			return newInstance(arrays.getClass().getComponentType().getComponentType(), 0);

		int totalSize = 0;
		for (int i = 0; i < arrays.length; ++i)
			totalSize += arrays[i].length;

		T[] result = newInstance(arrays.getClass().getComponentType().getComponentType(), totalSize);

		int endPos = 0;
		for (int i = 0; i < arrays.length; ++i) {
			T[] array = arrays[i];
			System.arraycopy(array, 0, result, endPos, array.length);
			endPos += array.length;
		}
		return result;
	}

	public static <T> T[] newInstance(Class<?> clazz, int size) {
		return (T[]) (clazz == Object[].class ? new Object[size] : Array.newInstance(clazz, size));
	}
}
