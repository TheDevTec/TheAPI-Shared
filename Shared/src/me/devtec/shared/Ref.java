package me.devtec.shared;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.devtec.shared.utility.ParseUtils;

public class Ref {

	public static enum ServerType {
		BUKKIT(true), SPIGOT(true), PAPER(true), BUNGEECORD(false), VELOCITY(false), CUSTOM(false); // Is it minecraft?

		boolean bukkit;

		ServerType(boolean bukkit) {
			this.bukkit = bukkit;
		}

		public boolean isBukkit() {
			return bukkit;
		}
	}

	static String ver;
	static int intVer;
	static int intRelease;
	static ServerType type;

	public static void init(ServerType type, String serverVersion) {
		Ref.ver = serverVersion;
		if (type.isBukkit()) {
			Ref.intVer = ParseUtils.getInt(Ref.ver.split("_")[1]);
			Ref.intRelease = ParseUtils.getInt(Ref.ver.split("_")[2]);
		}
		Ref.type = type;
	}

	public static String serverVersion() {
		return Ref.ver;
	}

	public static int serverVersionInt() {
		return Ref.intVer;
	}

	public static int serverVersionRelease() {
		return Ref.intRelease;
	}

	public static ServerType serverType() {
		return Ref.type;
	}

	public static boolean isNewerThan(int i) {
		return Ref.intVer > i;
	}

	public static boolean isOlderThan(int i) {
		return Ref.intVer < i;
	}

	public static void set(Object main, Field f, Object o) {
		try {
			f.setAccessible(true);
			f.set(main, o);
		} catch (Exception e) {
		}
	}

	public static void set(Object main, String field, Object o) {
		try {
			Field f = Ref.field(main.getClass(), field);
			f.setAccessible(true);
			f.set(main, o);
		} catch (Exception e) {
		}
	}

	public static Class<?> getClass(String name) {
		try {
			return Class.forName(name);
		} catch (Exception e) {
			return null;
		}
	}

	public static Class<?> getClass(String name, ClassLoader loader) {
		try {
			return Class.forName(name, true, loader);
		} catch (Exception e) {
			return null;
		}
	}

	public static boolean existsMethod(Class<?> c, String name) {
		boolean a = false;
		for (Method d : Ref.getMethods(c))
			if (d.getName().equals(name)) {
				a = true;
				break;
			}
		return a;
	}

	public static Object cast(Class<?> c, Object item) {
		try {
			return c.cast(item);
		} catch (Exception e) {
			return null;
		}
	}

	public static Constructor<?> constructor(Class<?> main, Class<?>... bricks) {
		try {
			return main.getDeclaredConstructor(bricks);
		} catch (Exception es) {
			return null;
		}
	}

	public static Class<?>[] getClasses(Class<?> main) {
		try {
			return main.getClasses();
		} catch (Exception es) {
			return new Class<?>[0];
		}
	}

	public static Class<?>[] getDeclaredClasses(Class<?> main) {
		try {
			return main.getDeclaredClasses();
		} catch (Exception es) {
			return new Class<?>[0];
		}
	}

	public static Field[] getFields(Class<?> main) {
		try {
			return main.getFields();
		} catch (Exception es) {
			return new Field[0];
		}
	}

	public static List<Field> getAllFields(Class<?> main) {
		List<Field> f = new ArrayList<>();
		Class<?> superclass = main;
		while (superclass != null) {
			f.addAll(Arrays.asList(Ref.getDeclaredFields(superclass)));
			superclass = superclass.getSuperclass();
		}
		return f;
	}

	public static Field[] getDeclaredFields(Class<?> main) {
		try {
			return main.getDeclaredFields();
		} catch (Exception es) {
			return new Field[0];
		}
	}

	public static Method[] getMethods(Class<?> main) {
		try {
			return main.getMethods();
		} catch (Exception es) {
			return new Method[0];
		}
	}

	public static Method[] getDeclaredMethods(Class<?> main) {
		try {
			return main.getDeclaredMethods();
		} catch (Exception es) {
			return null;
		}
	}

	public static Constructor<?>[] getConstructors(Class<?> main) {
		try {
			return main.getConstructors();
		} catch (Exception es) {
			return null;
		}
	}

	public static Constructor<?>[] getDeclaredConstructors(Class<?> main) {
		try {
			return main.getDeclaredConstructors();
		} catch (Exception es) {
			return null;
		}
	}

	public static Field field(Class<?> main, String name) {
		Class<?> mainClass = main;
		while (mainClass != null) {
			try {
				Field field = mainClass.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			} catch (Exception | NoSuchFieldError err) {
			}
			mainClass = mainClass.getSuperclass();
		}
		return null;
	}

	public static Field field(Class<?> main, Class<?> returnValue) {
		Class<?> mainClass = main;
		while (mainClass != null) {
			for (Field field : Ref.getDeclaredFields(mainClass))
				if (field.getType() == returnValue) {
					field.setAccessible(true);
					return field;
				}
			mainClass = mainClass.getSuperclass();
		}
		return null;
	}

	public static Object get(Object main, Field field) {
		try {
			field.setAccessible(true);
			return field.get(main);
		} catch (Exception es) {
			return null;
		}
	}

	public static Object getNulled(Field field) {
		return get(null, field);
	}

	public static Object getNulled(Class<?> clazz, String field) {
		return get(null, Ref.field(clazz, field));
	}

	public static Object getStatic(Field field) {
		return get(null, field);
	}

	public static Object getStatic(Class<?> clazz, String field) {
		return get(null, Ref.field(clazz, field));
	}

	public static Object get(Object main, String field) {
		return Ref.get(main, Ref.field(main.getClass(), field));
	}

	public static Object invoke(Object main, Method method, Object... bricks) {
		try {
			method.setAccessible(true);
			return method.invoke(main, bricks);
		} catch (Exception | NoSuchMethodError es) {
			return null;
		}
	}

	public static Object invoke(Object main, String method, Object... bricks) {
		try {
			return Ref.findMethod(main.getClass(), method, bricks).invoke(main, bricks);
		} catch (Exception es) {
			return null;
		}
	}

	public static Object get(Object main, Class<?> returnValue) {
		return Ref.get(main, field(main.getClass(), returnValue));
	}

	public static Object invokeNulled(Class<?> classInMethod, String method, Object... bricks) {
		try {
			return Ref.findMethod(classInMethod, method, bricks).invoke(null, bricks);
		} catch (Exception es) {
			return null;
		}
	}

	public static Object invokeNulled(Method method, Object... bricks) {
		return invoke(null, method, bricks);
	}

	public static Object invokeStatic(Class<?> classInMethod, String method, Object... bricks) {
		return Ref.invokeNulled(classInMethod, method, bricks);
	}

	public static Object invokeStatic(Method method, Object... bricks) {
		return Ref.invokeNulled(method, bricks);
	}

	public static Method findMethod(Object clazz, String name, Object... bricks) {
		return Ref.findMethod(clazz.getClass(), name, bricks);
	}

	public static Method method(Class<?> clazz, String name, Class<?>... params) {
		if (params.length == 0) {
			Class<?> startClass = clazz;
			while (startClass != null) {
				Method found;
				try {
					found = startClass.getDeclaredMethod(name);
					found.setAccessible(true);
					return found;
				} catch (Exception | NoSuchMethodError err) {

				}
				startClass = startClass.getSuperclass();
			}
		} else {
			Class<?> startClass = clazz;
			while (startClass != null) {
				Method found;
				try {
					found = startClass.getDeclaredMethod(name, params);
					found.setAccessible(true);
					return found;
				} catch (Exception | NoSuchMethodError err) {

				}
				startClass = startClass.getSuperclass();
			}
		}
		return null;
	}

	public static Method findMethodByName(Class<?> clazz, String name) {
		Class<?> startClass = clazz;
		while (startClass != null) {
			for (Method m : Ref.getDeclaredMethods(clazz))
				if (m.getName().equals(name)) {
					m.setAccessible(true);
					return m;
				}
			startClass = startClass.getSuperclass();
		}
		return null;
	}

	public static Method findMethod(Class<?> clazz, String name, Object... bricks) {
		if (bricks.length == 0) {
			Class<?> startClass = clazz;
			while (startClass != null) {
				for (Method m : Ref.getDeclaredMethods(clazz))
					if (m.getName().equals(name) && m.getParameterTypes().length == 0) {
						m.setAccessible(true);
						return m;
					}
				startClass = startClass.getSuperclass();
			}
		} else {
			Class<?> startClass = clazz;
			Class<?>[] params = new Class<?>[bricks.length];
			for (int i = 0; i < params.length; ++i) {
				Object brick = bricks[i];
				params[i] = brick == null ? null : brick instanceof Class ? (Class<?>) brick : brick.getClass();
			}
			while (startClass != null) {
				for (Method m : Ref.getDeclaredMethods(clazz))
					if (m.getName().equals(name) && m.getParameterTypes().length == params.length && areSame(params, m.getParameterTypes())) {
						m.setAccessible(true);
						return m;
					}
				startClass = startClass.getSuperclass();
			}
		}
		return null;
	}

	public static Constructor<?> findConstructor(Class<?> clazz, Object... bricks) {
		if (bricks.length == 0) {
			Class<?> startClass = clazz;
			while (startClass != null) {
				for (Constructor<?> m : Ref.getDeclaredConstructors(clazz))
					if (m.getParameterTypes().length == 0) {
						m.setAccessible(true);
						return m;
					}
				startClass = startClass.getSuperclass();
			}
		} else {
			Class<?> startClass = clazz;
			Class<?>[] params = new Class<?>[bricks.length];
			for (int i = 0; i < params.length; ++i) {
				Object brick = bricks[i];
				params[i] = brick == null ? null : brick instanceof Class ? (Class<?>) brick : brick.getClass();
			}
			while (startClass != null) {
				for (Constructor<?> m : Ref.getDeclaredConstructors(clazz))
					if (m.getParameterTypes().length == params.length && areSame(params, m.getParameterTypes())) {
						m.setAccessible(true);
						return m;
					}
				startClass = startClass.getSuperclass();
			}
		}
		return null;
	}

	private static boolean areSame(Class<?>[] a, Class<?>[] b) {
		for (int i = 0; i < a.length; ++i)
			if (a[i] != null && !a[i].isAssignableFrom(b[i]))
				return false;
		return true;
	}

	public static Object newInstance(Constructor<?> constructor, Object... bricks) {
		try {
			constructor.setAccessible(true);
			return constructor.newInstance(bricks);
		} catch (Exception es) {
			return null;
		}
	}

	public static Object newInstanceByClass(String className, Object... bricks) {
		return Ref.newInstance(Ref.findConstructor(Ref.getClass(className), bricks), bricks);
	}

	public static Object newInstanceByClass(Class<?> clazz, Object... bricks) {
		return Ref.newInstance(Ref.findConstructor(clazz, bricks), bricks);
	}

	public static Class<?> nms(String modernPackageName, String name) {
		try {
			if (Ref.isNewerThan(16))
				return modernPackageName.isEmpty() ? Class.forName("net.minecraft." + name) : Class.forName("net.minecraft." + modernPackageName + "." + name);
			return Class.forName("net.minecraft.server." + Ref.serverVersion() + "." + name);
		} catch (Exception e) {
			return null;
		}
	}

	public static Class<?> craft(String name) {
		try {
			return Class.forName("org.bukkit.craftbukkit." + Ref.serverVersion() + "." + name);
		} catch (Exception e) {
			return null;
		}
	}
}