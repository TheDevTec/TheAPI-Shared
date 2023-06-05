package me.devtec.shared.json;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devtec.shared.Pair;
import me.devtec.shared.Ref;
import me.devtec.shared.utility.ArrayUtils;
import sun.misc.Unsafe;

public class JsonUtils {
	private static Unsafe unsafe;
	static {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			JsonUtils.unsafe = (Unsafe) f.get(null);
		} catch (Exception err) {
		}
	}

	public static Object writeWithoutParseStatic(Object s) {
		try {
			if (s == null)
				return null;
			if (s instanceof String || s instanceof CharSequence || s instanceof Boolean || s instanceof Number || s instanceof Character)
				return s;
			Object result = Json.processDataWriters(s);
			if (result != null)
				return result;
			if (s instanceof Enum) {
				Map<String, Object> object = new HashMap<>();
				object.put("c", s.getClass().getName());
				object.put("e", ((Enum<?>) s).name());
				object.put("t", "enum");
				return object;
			}
			if (s instanceof Map) {
				Map<String, Object> object = new HashMap<>();
				object.put("c", s.getClass().getName());
				object.put("t", "map");
				List<Object> vals = new ArrayList<>();
				for (Map.Entry<?, ?> o : ((Map<?, ?>) s).entrySet())
					vals.add(JsonUtils.writeWithoutParseStatic(new Pair(o.getKey(), o.getValue())));
				object.put("s", vals);
				return object;
			}
			if (s instanceof Collection) {
				Map<String, Object> object = new HashMap<>();
				object.put("c", s.getClass().getName());
				object.put("t", "collection");
				List<Object> vals = new ArrayList<>();
				for (Object o : (Collection<?>) s)
					vals.add(JsonUtils.writeWithoutParseStatic(o));
				object.put("s", vals);
				return object;
			}
			if (s.getClass().isArray()) {
				Map<String, Object> object = new HashMap<>();
				object.put("c", s.getClass().getComponentType().getName());
				object.put("t", "array");
				List<Object> vals = new ArrayList<>();
				for (int i = 0; i < Array.getLength(s); ++i)
					vals.add(JsonUtils.writeWithoutParseStatic(Array.get(s, i)));
				object.put("s", vals);
				return object;
			}
			Map<String, Object> object = new HashMap<>();
			Map<String, Object> fields = new HashMap<>();
			Map<String, Object> sub_fields = new HashMap<>();
			object.put("c", s.getClass().getName());
			object.put("f", fields);
			Class<?> c = s.getClass();
			for (Field f : c.getDeclaredFields()) {
				if ((f.getModifiers() & Modifier.STATIC) != 0)
					continue;
				f.setAccessible(true);
				Object obj = f.get(s);
				if (s.equals(obj))
					fields.put("~" + f.getName(), "~");
				else
					fields.put(f.getName(), JsonUtils.writeWithoutParseStatic(obj));
			}
			c = c.getSuperclass();
			while (c != null) {
				for (Field f : c.getDeclaredFields()) {
					if ((f.getModifiers() & Modifier.STATIC) != 0)
						continue;
					f.setAccessible(true);
					Object obj = f.get(s);
					if (s.equals(obj))
						sub_fields.put(c.getName() + ":~" + f.getName(), "~");
					else
						sub_fields.put(c.getName() + ":" + f.getName(), JsonUtils.writeWithoutParseStatic(obj));
				}
				c = c.getSuperclass();
			}
			if (!sub_fields.isEmpty())
				object.put("sf", sub_fields);
			return object;
		} catch (Exception err) {
		}
		return null;
	}

	public static Object cast(Object value, Class<?> type) {
		if (value == null)
			return null;
		if (type.isArray()) {
			Collection<?> o = (Collection<?>) value;
			List<Object> c = new ArrayList<>(o.size());
			for (Object a : o)
				c.add(JsonUtils.read(a));
			return c.toArray();
		}
		if (Double.TYPE == type)
			return ((Number) value).doubleValue();
		if (Long.TYPE == type)
			return ((Number) value).longValue();
		if (Integer.TYPE == type)
			return ((Number) value).intValue();
		if (Float.TYPE == type)
			return ((Number) value).floatValue();
		if (Byte.TYPE == type)
			return ((Number) value).byteValue();
		if (Short.TYPE == type)
			return ((Number) value).shortValue();
		if (Character.TYPE == type)
			return ((Character) value).charValue();
		return JsonUtils.read(value);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object read(Object s) {
		if (s == null || s.equals("null"))
			return null;
		try {
			if (s instanceof Map) {
				Map<String, Object> map = (Map<String, Object>) s;
				Object result = Json.processDataReaders(map);
				if (result != null)
					return result;
				String className = (String) map.get("c");
				if (className == null)
					return s;
				Class<?> c = JsonUtils.getClassByName(className);
				if (c == null)
					return s;
				String type = (String) map.get("t");
				if (type != null) { // collection, array or map
					if (type.equals("map")) {
						Object object;
						try {
							object = c.newInstance();
						} catch (Exception e) {
							object = Unsafe.getUnsafe().allocateInstance(c);
						}
						Map o = (Map) object;
						for (Object cc : (List<?>) map.getOrDefault("s", Collections.emptyList())) {
							Pair pair = (Pair) JsonUtils.read(cc);
							o.put(pair.getKey(), pair.getValue());
						}
						return o;
					}
					if (type.equals("array")) {
						List<?> collection = (List<?>) map.getOrDefault("s", Collections.emptyList());
						Object array = ArrayUtils.newInstance(c, collection.size());
						int i = 0;
						for (Object cc : collection)
							Array.set(array, i++, JsonUtils.cast(JsonUtils.read(cc), c));
						return array;
					}
					if (type.equals("enum")) {
						Object obj = map.get("e");
						return obj == null ? s : Ref.getNulled(c, type.toString());
					}
					if (type.equals("collection")) {
						Object object;
						try {
							object = c.newInstance();
						} catch (Exception e) {
							object = Unsafe.getUnsafe().allocateInstance(c);
						}
						Collection<Object> o = (Collection<Object>) object;
						for (Object cc : (List<?>) map.getOrDefault("s", Collections.emptyList()))
							o.add(JsonUtils.read(cc));
						return o;
					}
					return null;
				}
				Object object;
				try {
					object = c.newInstance();
				} catch (Exception e) {
					object = JsonUtils.unsafe.allocateInstance(c);
				}

				Map<String, Object> fields = (Map<String, Object>) map.getOrDefault("f", Collections.emptyMap());
				Map<String, Object> sub_fields = (Map<String, Object>) map.getOrDefault("sf", Collections.emptyMap());
				for (Map.Entry<String, Object> e : fields.entrySet()) {
					if (e.getKey().startsWith("~")) {
						Field f = c.getDeclaredField(e.getKey().substring(1));
						f.setAccessible(true);
						f.set(object, object);
						continue;
					}
					Field f = c.getDeclaredField(e.getKey());
					f.setAccessible(true);
					f.set(object, JsonUtils.cast(e.getValue(), f.getType()));
				}
				if (sub_fields != null)
					for (Map.Entry<String, Object> e : sub_fields.entrySet()) {
						String field = e.getKey().split(":")[1];
						if (field.startsWith("~")) {
							Field f = Class.forName(e.getKey().split(":")[0]).getDeclaredField(field.substring(1));
							f.setAccessible(true);
							f.set(object, object);
							continue;
						}
						Field f = Class.forName(e.getKey().split(":")[0]).getDeclaredField(field);
						f.setAccessible(true);
						f.set(object, JsonUtils.cast(e.getValue(), f.getType()));
					}
				return object;
			}
		} catch (Exception err) {
			err.printStackTrace();
		}
		return s;
	}

	public static Class<?> getClassByName(String className) throws ClassNotFoundException {
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
		}
		return Class.forName(className);
	}

}
