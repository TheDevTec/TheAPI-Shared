package me.devtec.shared.json;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import me.devtec.shared.Pair;
import me.devtec.shared.Ref;

public final class JsonUtils {

	private static final String KEY_CLASS = "c";
	private static final String KEY_TYPE = "t";
	private static final String KEY_ENUM = "e";
	private static final String KEY_STATE = "s";
	private static final String KEY_FIELDS = "f";
	private static final String KEY_SUPER_FIELDS = "sf";

	private static final String TYPE_MAP = "map";
	private static final String TYPE_ARRAY = "array";
	private static final String TYPE_ENUM = "enum";
	private static final String TYPE_COLLECTION = "collection";

	private JsonUtils() {
	}

	// =====================================================================
	// Write
	// =====================================================================

	public static Object writeWithoutParseStatic(Object value) {
		try {
			return write(value, new IdentityHashMap<>());
		} catch (Exception err) {
			err.printStackTrace();
			return null;
		}
	}

	private static Object write(Object value, IdentityHashMap<Object, Boolean> visiting) throws Exception {
		if (value == null)
			return null;

		if (isSimpleValue(value))
			return value;

		Object custom = Json.processDataWriters(value);

		if (custom != null)
			return custom;

		if (value instanceof Enum) {
			Enum<?> enumValue = (Enum<?>) value;

			Map<String, Object> object = new LinkedHashMap<>();
			object.put(KEY_CLASS, enumValue.getDeclaringClass().getName());
			object.put(KEY_ENUM, enumValue.name());
			object.put(KEY_TYPE, TYPE_ENUM);

			return object;
		}

		/*
		 * Prevent:
		 *
		 * A -> B -> A
		 *
		 * Direct object.field = object references are handled separately below and
		 * preserve the old "~" format.
		 */
		if (visiting.put(value, Boolean.TRUE) != null)
			throw new IllegalStateException(
					"Circular reference detected while serializing " + value.getClass().getName());

		try {
			if (value instanceof Map)
				return writeMap((Map<?, ?>) value, visiting);

			if (value instanceof Collection)
				return writeCollection((Collection<?>) value, visiting);

			if (value.getClass().isArray())
				return writeArray(value, visiting);

			return writeObject(value, visiting);
		} finally {
			visiting.remove(value);
		}
	}

	private static boolean isSimpleValue(Object value) {
		return value instanceof CharSequence || value instanceof Boolean || value instanceof Number
				|| value instanceof Character || value instanceof UUID;
	}

	// =====================================================================
	// Write - Map
	// =====================================================================

	private static Object writeMap(Map<?, ?> source, IdentityHashMap<Object, Boolean> visiting) throws Exception {
		Class<?> type = source.getClass();

		if (isSimpleMap(type)) {
			Map<Object, Object> result = source instanceof LinkedHashMap ? new LinkedHashMap<>() : new HashMap<>();

			for (Map.Entry<?, ?> entry : source.entrySet())
				result.put(write(entry.getKey(), visiting), write(entry.getValue(), visiting));

			return result;
		}

		Map<String, Object> object = new LinkedHashMap<>();
		object.put(KEY_CLASS, type.getName());
		object.put(KEY_TYPE, TYPE_MAP);

		List<Object> values = new ArrayList<>(source.size());

		for (Map.Entry<?, ?> entry : source.entrySet())
			values.add(write(new Pair(entry.getKey(), entry.getValue()), visiting));

		object.put(KEY_STATE, values);

		return object;
	}

	private static boolean isSimpleMap(Class<?> type) {
		if (HashMap.class.isAssignableFrom(type))
			return true;

		String name = type.getName();

		/*
		 * Collections.unmodifiableMap(...) Collections.singletonMap(...)
		 * Collections.emptyMap(...) Collections.synchronizedMap(...)
		 * Collections.checkedMap(...)
		 */
		if (name.startsWith("java.util.Collections$"))
			return true;

		/*
		 * Map.of(...) Map.copyOf(...)
		 *
		 * Java 9+
		 */
		return name.startsWith("java.util.ImmutableCollections$");
	}

	// =====================================================================
	// Write - Collection
	// =====================================================================

	private static Object writeCollection(Collection<?> source, IdentityHashMap<Object, Boolean> visiting)
			throws Exception {
		Class<?> type = source.getClass();

		if (isSimpleCollection(type)) {
			List<Object> result = new ArrayList<>(source.size());

			for (Object value : source)
				result.add(write(value, visiting));

			return result;
		}

		Map<String, Object> object = new LinkedHashMap<>();
		object.put(KEY_CLASS, type.getName());
		object.put(KEY_TYPE, TYPE_COLLECTION);

		List<Object> values = new ArrayList<>(source.size());

		for (Object value : source)
			values.add(write(value, visiting));

		object.put(KEY_STATE, values);

		return object;
	}

	private static boolean isSimpleCollection(Class<?> type) {
		if (type == ArrayList.class || type == LinkedList.class)
			return true;

		String name = type.getName();

		/*
		 * Arrays.asList(...)
		 */
		/*
		 * Collections.unmodifiable* Collections.singleton* Collections.empty*
		 * Collections.synchronized* Collections.checked*
		 */
		if ("java.util.Arrays$ArrayList".equals(name) || name.startsWith("java.util.Collections$"))
			return true;

		/*
		 * List.of(...) Set.of(...) List.copyOf(...)
		 *
		 * Java 9+
		 */
		return name.startsWith("java.util.ImmutableCollections$");
	}

	// =====================================================================
	// Write - Array
	// =====================================================================

	private static Object writeArray(Object source, IdentityHashMap<Object, Boolean> visiting) throws Exception {
		Class<?> componentType = source.getClass().getComponentType();
		int length = Array.getLength(source);

		Map<String, Object> object = new LinkedHashMap<>();
		object.put(KEY_CLASS, componentType.getName());
		object.put(KEY_TYPE, TYPE_ARRAY);

		List<Object> values = new ArrayList<>(length);

		for (int i = 0; i < length; ++i)
			values.add(write(Array.get(source, i), visiting));

		object.put(KEY_STATE, values);

		return object;
	}

	// =====================================================================
	// Write - Object
	// =====================================================================

	private static Object writeObject(Object source, IdentityHashMap<Object, Boolean> visiting) throws Exception {
		Class<?> sourceClass = source.getClass();

		Map<String, Object> object = new LinkedHashMap<>();
		Map<String, Object> fields = new LinkedHashMap<>();
		Map<String, Object> superFields = new LinkedHashMap<>();

		object.put(KEY_CLASS, sourceClass.getName());
		object.put(KEY_FIELDS, fields);

		Class<?> owner = sourceClass;

		while (owner != null && owner != Object.class) {
			Field[] declaredFields;

			try {
				declaredFields = owner.getDeclaredFields();
			} catch (Throwable ignored) {
				owner = owner.getSuperclass();
				continue;
			}

			for (Field field : declaredFields) {
				if (!shouldSerialize(field))
					continue;

				Object fieldValue;

				try {
					field.setAccessible(true);
					fieldValue = field.get(source);
				} catch (Throwable ignored) {
					continue;
				}

				boolean direct = owner == sourceClass;

				if (fieldValue == source) {
					if (direct)
						fields.put("~" + field.getName(), "~");
					else
						superFields.put(owner.getName() + ":~" + field.getName(), "~");

					continue;
				}

				Object serialized = write(fieldValue, visiting);

				if (direct)
					fields.put(field.getName(), serialized);
				else
					superFields.put(owner.getName() + ":" + field.getName(), serialized);
			}

			owner = owner.getSuperclass();
		}

		if (!superFields.isEmpty())
			object.put(KEY_SUPER_FIELDS, superFields);

		return object;
	}

	private static boolean shouldSerialize(Field field) {
		return !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic();
	}

	// =====================================================================
	// Read
	// =====================================================================

	@SuppressWarnings({ "unchecked" })
	public static Object read(Object value) {
		if (value == null)
			return null;

		try {
			/*
			 * Plain JSON array.
			 *
			 * The old implementation didn't recursively process these, therefore serialized
			 * objects inside a List could remain Maps.
			 */
			if (value instanceof Collection)
				return readCollection((Collection<?>) value);

			if (!(value instanceof Map))
				return value;

			Map<?, ?> rawMap = (Map<?, ?>) value;

			/*
			 * JSON objects normally have String keys. The cast itself is safe due to type
			 * erasure and metadata lookups only use String keys.
			 */
			Map<String, Object> map = (Map<String, Object>) rawMap;

			Object custom = Json.processDataReaders(map);

			if (custom != null)
				return custom;

			if (!isSerializedObject(map))
				return readMap(rawMap);

			String className = getAsString(map, KEY_CLASS);

			Class<?> type;

			try {
				type = getClassByName(className);
			} catch (ClassNotFoundException ignored) {
				return readMap(rawMap);
			}

			if (type == null)
				return readMap(rawMap);

			String dataType = getAsString(map, KEY_TYPE);

			if (dataType != null)
				switch (dataType) {
				case TYPE_MAP:
					return readCustomMap(map, type);

				case TYPE_ARRAY:
					return readArray(map, type);

				case TYPE_ENUM:
					return readEnum(map, type, value);

				case TYPE_COLLECTION:
					return readCustomCollection(map, type);

				default:
					return value;
				}

			Object object = newInstance(type);

			if (object == null)
				return value;

			restoreFields(object, type, map);

			return object;

		} catch (Exception err) {
			err.printStackTrace();
			return value;
		}
	}

	private static boolean isSerializedObject(Map<String, Object> map) {
		String className = getAsString(map, KEY_CLASS);

		if (className == null)
			return false;

		if (map.containsKey(KEY_FIELDS))
			return true;

		String type = getAsString(map, KEY_TYPE);

		return TYPE_MAP.equals(type) || TYPE_ARRAY.equals(type) || TYPE_ENUM.equals(type)
				|| TYPE_COLLECTION.equals(type);
	}

	// =====================================================================
	// Read - Plain Collection
	// =====================================================================

	private static Object readCollection(Collection<?> source) {
		Collection<Object> result;

		if (source instanceof Set)
			result = new LinkedHashSet<>();
		else if (source instanceof LinkedList)
			result = new LinkedList<>();
		else
			result = new ArrayList<>(source.size());

		for (Object value : source)
			result.add(read(value));

		return result;
	}

	// =====================================================================
	// Read - Plain Map
	// =====================================================================

	private static Object readMap(Map<?, ?> source) {
		Map<Object, Object> result = source instanceof LinkedHashMap ? new LinkedHashMap<>() : new HashMap<>();

		for (Map.Entry<?, ?> entry : source.entrySet())
			result.put(read(entry.getKey()), read(entry.getValue()));

		return result;
	}

	// =====================================================================
	// Read - Custom Map
	// =====================================================================

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object readCustomMap(Map<String, Object> map, Class<?> type) {
		Object object = newInstance(type);

		if (!(object instanceof Map))
			return map;

		Map result = (Map) object;

		Object state = map.get(KEY_STATE);

		if (state instanceof Collection)
			for (Object value : (Collection<?>) state) {
				Object decoded = read(value);

				if (!(decoded instanceof Pair))
					continue;

				Pair pair = (Pair) decoded;

				result.put(pair.getKey(), pair.getValue());
			}

		return result;
	}

	// =====================================================================
	// Read - Collection
	// =====================================================================

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object readCustomCollection(Map<String, Object> map, Class<?> type) {
		Object object = newInstance(type);

		if (!(object instanceof Collection))
			return map;

		Collection result = (Collection) object;

		Object state = map.get(KEY_STATE);

		if (state instanceof Collection)
			for (Object value : (Collection<?>) state)
				result.add(read(value));

		return result;
	}

	// =====================================================================
	// Read - Array
	// =====================================================================

	private static Object readArray(Map<String, Object> map, Class<?> componentType) {
		Object state = map.get(KEY_STATE);

		int size = state instanceof Collection ? ((Collection<?>) state).size() : 0;

		Object array = Array.newInstance(componentType, size);

		if (!(state instanceof Collection))
			return array;

		int index = 0;

		for (Object value : (Collection<?>) state)
			Array.set(array, index++, cast(value, componentType));

		return array;
	}

	// =====================================================================
	// Read - Enum
	// =====================================================================

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object readEnum(Map<String, Object> map, Class<?> type, Object fallback) {
		Object enumName = map.get(KEY_ENUM);

		if (enumName == null || !type.isEnum())
			return fallback;

		try {
			return Enum.valueOf((Class) type, String.valueOf(enumName));
		} catch (Exception ignored) {
			return fallback;
		}
	}

	// =====================================================================
	// Restore fields
	// =====================================================================

	private static void restoreFields(Object object, Class<?> objectType, Map<String, Object> map) {
		Object fields = map.get(KEY_FIELDS);

		if (fields instanceof Map)
			restoreDirectFields(object, objectType, (Map<?, ?>) fields);

		Object superFields = map.get(KEY_SUPER_FIELDS);

		if (superFields instanceof Map)
			restoreSuperFields(object, objectType, (Map<?, ?>) superFields);
	}

	private static void restoreDirectFields(Object object, Class<?> owner, Map<?, ?> fields) {
		for (Map.Entry<?, ?> entry : fields.entrySet()) {
			if (!(entry.getKey() instanceof String))
				continue;

			String fieldName = (String) entry.getKey();
			boolean self = fieldName.startsWith("~");

			if (self)
				fieldName = fieldName.substring(1);

			Field field;

			try {
				field = owner.getDeclaredField(fieldName);
				field.setAccessible(true);
			} catch (Throwable ignored) {
				continue;
			}

			try {
				if (self)
					field.set(object, object);
				else
					field.set(object, cast(entry.getValue(), field.getType()));
			} catch (Throwable ignored) {
			}
		}
	}

	private static void restoreSuperFields(Object object, Class<?> objectType, Map<?, ?> fields) {
		for (Map.Entry<?, ?> entry : fields.entrySet()) {
			if (!(entry.getKey() instanceof String))
				continue;

			String key = (String) entry.getKey();

			/*
			 * No split(":").
			 *
			 * Format:
			 *
			 * com.example.Parent:field com.example.Parent:~selfField
			 */
			int separator = key.indexOf(':');

			if (separator == -1)
				continue;

			String className = key.substring(0, separator);
			String fieldName = key.substring(separator + 1);

			boolean self = fieldName.startsWith("~");

			if (self)
				fieldName = fieldName.substring(1);

			Class<?> owner;

			try {
				owner = getClassByName(className);
			} catch (ClassNotFoundException ignored) {
				continue;
			}

			/*
			 * Do not allow arbitrary unrelated classes from malformed data.
			 */
			if (!owner.isAssignableFrom(objectType))
				continue;

			Field field;

			try {
				field = owner.getDeclaredField(fieldName);
				field.setAccessible(true);
			} catch (Throwable ignored) {
				continue;
			}

			try {
				if (self)
					field.set(object, object);
				else
					field.set(object, cast(entry.getValue(), field.getType()));
			} catch (Throwable ignored) {
			}
		}
	}

	// =====================================================================
	// Cast
	// =====================================================================

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Object cast(Object value, Class<?> type) {
		if (value == null || type == null)
			return null;

		Object parsed = read(value);

		if (parsed == null)
			return null;

		/*
		 * Already the correct type.
		 */
		if (!type.isPrimitive() && type.isInstance(parsed))
			return parsed;

		// -------------------------------------------------------------
		// Array
		// -------------------------------------------------------------

		if (type.isArray())
			return castArray(parsed, type.getComponentType());

		// -------------------------------------------------------------
		// Collection
		// -------------------------------------------------------------

		if (Collection.class.isAssignableFrom(type) && parsed instanceof Collection)
			return castCollection((Collection<?>) parsed, type);

		// -------------------------------------------------------------
		// Map
		// -------------------------------------------------------------

		if (Map.class.isAssignableFrom(type) && parsed instanceof Map)
			return castMap((Map<?, ?>) parsed, type);

		// -------------------------------------------------------------
		// Numbers
		// -------------------------------------------------------------

		if (parsed instanceof Number) {
			Number number = (Number) parsed;

			if (type == double.class || type == Double.class)
				return number.doubleValue();

			if (type == long.class || type == Long.class)
				return number.longValue();

			if (type == int.class || type == Integer.class)
				return number.intValue();

			if (type == float.class || type == Float.class)
				return number.floatValue();

			if (type == byte.class || type == Byte.class)
				return number.byteValue();

			if (type == short.class || type == Short.class)
				return number.shortValue();
		}

		// -------------------------------------------------------------
		// Boolean
		// -------------------------------------------------------------

		if (type == boolean.class || type == Boolean.class) {
			if (parsed instanceof Boolean)
				return parsed;

			return Boolean.valueOf(String.valueOf(parsed));
		}

		// -------------------------------------------------------------
		// Character
		// -------------------------------------------------------------

		if (type == char.class || type == Character.class) {
			if (parsed instanceof Character)
				return parsed;

			String text = String.valueOf(parsed);

			return text.isEmpty() ? Character.valueOf('\0') : Character.valueOf(text.charAt(0));
		}

		// -------------------------------------------------------------
		// Enum fallback
		// -------------------------------------------------------------

		if (type.isEnum() && parsed instanceof CharSequence)
			try {
				return Enum.valueOf((Class) type, parsed.toString());
			} catch (Exception ignored) {
			}

		return parsed;
	}

	private static Object castArray(Object value, Class<?> componentType) {
		if (value == null)
			return null;

		int size;

		if (value instanceof Collection)
			size = ((Collection<?>) value).size();
		else if (value.getClass().isArray())
			size = Array.getLength(value);
		else
			return value;

		Object array = Array.newInstance(componentType, size);

		if (value instanceof Collection) {
			int index = 0;

			for (Object element : (Collection<?>) value)
				Array.set(array, index++, cast(element, componentType));

			return array;
		}

		for (int i = 0; i < size; ++i)
			Array.set(array, i, cast(Array.get(value, i), componentType));

		return array;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object castCollection(Collection<?> source, Class<?> type) {
		if (type.isInstance(source))
			return source;

		Object instance = newInstance(type);

		if (instance instanceof Collection) {
			Collection result = (Collection) instance;
			result.addAll(source);
			return result;
		}

		/*
		 * Interface / abstract type fallbacks.
		 */
		if (type.isAssignableFrom(ArrayList.class))
			return new ArrayList<Object>(source);

		if (type.isAssignableFrom(LinkedHashSet.class))
			return new LinkedHashSet<Object>(source);

		if (type.isAssignableFrom(LinkedList.class))
			return new LinkedList<Object>(source);

		if (type.isAssignableFrom(TreeSet.class))
			try {
				return new TreeSet(source);
			} catch (Exception ignored) {
			}

		return source;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object castMap(Map<?, ?> source, Class<?> type) {
		if (type.isInstance(source))
			return source;

		Object instance = newInstance(type);

		if (instance instanceof Map) {
			Map result = (Map) instance;
			result.putAll(source);
			return result;
		}

		if (type.isAssignableFrom(LinkedHashMap.class))
			return new LinkedHashMap<Object, Object>(source);

		if (type.isAssignableFrom(TreeMap.class))
			try {
				return new TreeMap(source);
			} catch (Exception ignored) {
			}

		return source;
	}

	// =====================================================================
	// Object creation
	// =====================================================================

	private static Object newInstance(Class<?> type) {
		if (type == null)
			return null;

		Object instance = Ref.newInstance(type);

		if (instance != null)
			return instance;

		return Ref.allocateInstance(type);
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private static String getAsString(Map<String, Object> map, String key) {

		Object value = map.get(key);

		return value instanceof String ? (String) value : null;
	}

	// =====================================================================
	// Class resolving
	// =====================================================================

	public static Class<?> getClassByName(String className) throws ClassNotFoundException {
		if (className == null)
			throw new ClassNotFoundException("null");

		switch (className) {
		case "int":
			return int.class;

		case "double":
			return double.class;

		case "float":
			return float.class;

		case "long":
			return long.class;

		case "char":
			return char.class;

		case "byte":
			return byte.class;

		case "short":
			return short.class;

		case "boolean":
			return boolean.class;

		case "void":
			return void.class;

		default:
			break;
		}

		/*
		 * Important for Bukkit/Spigot/Paper plugins where user classes may live in a
		 * different ClassLoader than TheAPI itself.
		 */
		ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();

		if (contextLoader != null)
			try {
				return Class.forName(className, false, contextLoader);
			} catch (ClassNotFoundException ignored) {
			}

		ClassLoader ownLoader = JsonUtils.class.getClassLoader();

		if (ownLoader != null && ownLoader != contextLoader)
			try {
				return Class.forName(className, false, ownLoader);
			} catch (ClassNotFoundException ignored) {
			}

		return Class.forName(className);
	}
}