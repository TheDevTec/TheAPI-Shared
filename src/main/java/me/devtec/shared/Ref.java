package me.devtec.shared;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.devtec.shared.utility.ParseUtils;

/**
 * Reflection utilities.
 *
 * Compatible with Java 8+.
 */
public final class Ref {

	private Ref() {
	}

	// =====================================================================
	// Server
	// =====================================================================

	public enum ServerType {
		BUKKIT(true), SPIGOT(true), PAPER(true), BUNGEECORD(false), VELOCITY(false), CUSTOM(false);

		private final boolean bukkit;

		ServerType(boolean bukkit) {
			this.bukkit = bukkit;
		}

		public boolean isBukkit() {
			return bukkit;
		}
	}

	private static volatile String versionString = "UNKNOWN";
	private static volatile int version;
	private static volatile int release;
	private static volatile ServerType type = ServerType.CUSTOM;

	public static void init(ServerType type, String versionString) {
		Ref.type = type == null ? ServerType.CUSTOM : type;
		Ref.versionString = versionString == null ? "UNKNOWN" : versionString;

		version = 0;
		release = 0;

		if (!Ref.type.isBukkit())
			return;

		parseVersion(Ref.versionString);
	}

	private static void parseVersion(String value) {
		if (value == null || value.isEmpty())
			return;

		try {
			/*
			 * Semantic versions:
			 *
			 * 1.8 1.8.8 1.20.6 1.21.5
			 *
			 * ->
			 *
			 * version = 8 / 20 / 21 release = 0 / 8 / 6 / 5
			 */
			if (value.indexOf('.') != -1) {
				String[] split = value.split("\\.");

				if (split.length == 0)
					return;

				int first = ParseUtils.getInt(split[0]);

				/*
				 * New Minecraft versioning:
				 *
				 * 26.1 26.2
				 *
				 * ->
				 *
				 * version = 26 release = 1 / 2
				 */
				if (first >= 26) {
					version = first;

					if (split.length > 1)
						release = ParseUtils.getInt(split[1]);

					return;
				}

				/*
				 * Legacy semantic format:
				 *
				 * 1.21.5
				 */
				if (split.length > 1)
					version = ParseUtils.getInt(split[1]);

				if (split.length > 2)
					release = ParseUtils.getInt(split[2]);

				return;
			}

			/*
			 * CraftBukkit / NMS version:
			 *
			 * v1_8_R3 v1_16_R3 v1_20_R4
			 */
			if (value.indexOf('_') != -1) {
				String[] split = value.split("_");

				if (split.length > 1)
					version = ParseUtils.getInt(split[1]);

				if (split.length > 2)
					release = ParseUtils.getInt(split[2]);
			}
		} catch (Throwable ignored) {
		}
	}

	public static String versionString() {
		return versionString;
	}

	public static int version() {
		return version;
	}

	public static int release() {
		return release;
	}

	public static ServerType type() {
		return type;
	}

	private static int compareVersion(int version, int release) {
		if (Ref.version != version)
			return Integer.compare(Ref.version, version);

		return Integer.compare(Ref.release, release);
	}

	public static boolean isAtLeast(int version, int release) {
		return compareVersion(version, release) >= 0;
	}

	public static boolean isAtMost(int version, int release) {
		return compareVersion(version, release) <= 0;
	}

	public static boolean isAfter(int version, int release) {
		return compareVersion(version, release) > 0;
	}

	public static boolean isBefore(int version, int release) {
		return compareVersion(version, release) < 0;
	}

	public static boolean isVersion(int version, int release) {
		return compareVersion(version, release) == 0;
	}

	// =====================================================================
	// Class
	// =====================================================================

	public static Class<?> getClass(String name) {
		if (name == null)
			return null;

		try {
			return Class.forName(name);
		} catch (Throwable ignored) {
			return null;
		}
	}

	public static Class<?> getClass(String name, ClassLoader loader) {
		if (name == null)
			return null;

		if (loader != null)
			try {
				return Class.forName(name, true, loader);
			} catch (Throwable ignored) {
			}

		return getClass(name);
	}

	public static boolean classExists(String name) {
		return getClass(name) != null;
	}

	public static boolean classExists(String name, ClassLoader loader) {
		return getClass(name, loader) != null;
	}

	public static <T> T cast(Class<T> type, Object value) {
		if (type == null || value == null)
			return null;

		try {
			return type.cast(value);
		} catch (Throwable ignored) {
			return null;
		}
	}

	// =====================================================================
	// Accessibility
	// =====================================================================

	public static boolean makeAccessible(AccessibleObject object) {
		if (object == null)
			return false;

		try {
			if (!object.isAccessible())
				object.setAccessible(true);

			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	// =====================================================================
	// Fields
	// =====================================================================

	/**
	 * Finds a field in the class and its superclasses.
	 */
	public static Field field(Class<?> type, String name) {
		if (type == null || name == null)
			return null;

		Class<?> current = type;

		while (current != null) {
			try {
				Field field = current.getDeclaredField(name);
				makeAccessible(field);
				return field;
			} catch (Throwable ignored) {
			}

			current = current.getSuperclass();
		}

		return findFieldInInterfaces(type, name, new HashSet<>());
	}

	private static Field findFieldInInterfaces(Class<?> type, String name, Set<Class<?>> visited) {
		if (type == null)
			return null;

		Class<?>[] interfaces;

		try {
			interfaces = type.getInterfaces();
		} catch (Throwable ignored) {
			return null;
		}

		for (Class<?> iface : interfaces) {
			if (!visited.add(iface))
				continue;

			try {
				Field field = iface.getDeclaredField(name);
				makeAccessible(field);
				return field;
			} catch (Throwable ignored) {
			}

			Field nested = findFieldInInterfaces(iface, name, visited);

			if (nested != null)
				return nested;
		}

		return null;
	}

	/**
	 * Returns first field with an exact type.
	 */
	public static Field field(Class<?> type, Class<?> fieldType) {
		if (type == null || fieldType == null)
			return null;

		for (Field field : getAllFields(type))
			if (field.getType() == fieldType)
				return field;

		return null;
	}

	public static List<Field> getAllFields(Class<?> type) {
		if (type == null)
			return Collections.emptyList();

		List<Field> fields = new ArrayList<>();

		Class<?> current = type;

		while (current != null) {
			try {
				Field[] declared = current.getDeclaredFields();

				for (Field field : declared) {
					makeAccessible(field);
					fields.add(field);
				}
			} catch (Throwable ignored) {
			}

			current = current.getSuperclass();
		}

		return fields;
	}

	public static List<Field> fieldsByName(Class<?> type, String name) {
		if (type == null || name == null)
			return Collections.emptyList();

		List<Field> result = new ArrayList<>();

		for (Field field : getAllFields(type))
			if (name.equals(field.getName()))
				result.add(field);

		return result;
	}

	/**
	 * Exact field type.
	 */
	public static List<Field> fieldsByType(Class<?> type, Class<?> fieldType) {
		if (type == null || fieldType == null)
			return Collections.emptyList();

		List<Field> result = new ArrayList<>();

		for (Field field : getAllFields(type))
			if (field.getType() == fieldType)
				result.add(field);

		return result;
	}

	/**
	 * Example:
	 *
	 * fieldsAssignableTo(clazz, Number.class)
	 *
	 * will also find Integer, Double, etc.
	 */
	public static List<Field> fieldsAssignableTo(Class<?> type, Class<?> fieldType) {
		if (type == null || fieldType == null)
			return Collections.emptyList();

		List<Field> result = new ArrayList<>();

		for (Field field : getAllFields(type))
			if (fieldType.isAssignableFrom(field.getType()))
				result.add(field);

		return result;
	}

	public static Object get(Object instance, Field field) {
		if (field == null)
			return null;

		try {
			makeAccessible(field);
			return field.get(instance);
		} catch (Throwable ignored) {
			return null;
		}
	}

	public static Object get(Object instance, String fieldName) {
		if (instance == null)
			return null;

		return get(instance, field(instance.getClass(), fieldName));
	}

	public static Object get(Object instance, Class<?> fieldType) {
		if (instance == null)
			return null;

		return get(instance, field(instance.getClass(), fieldType));
	}

	public static Object getStatic(Field field) {
		return get(null, field);
	}

	public static Object getStatic(Class<?> type, String fieldName) {
		return get(null, field(type, fieldName));
	}

	/**
	 * Attempts to set a field.
	 *
	 * final fields may or may not be writable depending on JVM/version. static
	 * final fields should not be considered reliably writable.
	 */
	public static boolean trySet(Object instance, Field field, Object value) {
		if (field == null)
			return false;

		try {
			makeAccessible(field);
			field.set(instance, value);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static boolean trySet(Object instance, String fieldName, Object value) {
		if (instance == null)
			return false;

		return trySet(instance, field(instance.getClass(), fieldName), value);
	}

	public static void set(Object instance, Field field, Object value) {
		trySet(instance, field, value);
	}

	public static void set(Object instance, String fieldName, Object value) {
		trySet(instance, fieldName, value);
	}

	public static boolean trySetStatic(Field field, Object value) {
		return trySet(null, field, value);
	}

	public static boolean trySetStatic(Class<?> type, String fieldName, Object value) {
		return trySet(null, field(type, fieldName), value);
	}

	public static void setStatic(Field field, Object value) {
		trySetStatic(field, value);
	}

	public static void setStatic(Class<?> type, String fieldName, Object value) {
		trySetStatic(type, fieldName, value);
	}

	// =====================================================================
	// Methods
	// =====================================================================

	/**
	 * All declared methods from:
	 *
	 * class superclass interfaces
	 *
	 * Methods overridden in subclasses and superclasses are both returned.
	 */
	public static List<Method> getAllMethods(Class<?> type) {
		if (type == null)
			return Collections.emptyList();

		LinkedHashSet<Method> methods = new LinkedHashSet<>();
		Set<Class<?>> interfaces = new HashSet<>();

		Class<?> current = type;

		while (current != null) {
			try {
				Method[] declared = current.getDeclaredMethods();

				for (Method method : declared) {
					makeAccessible(method);
					methods.add(method);
				}
			} catch (Throwable ignored) {
			}

			collectInterfaceMethods(current, methods, interfaces);

			current = current.getSuperclass();
		}

		return new ArrayList<>(methods);
	}

	private static void collectInterfaceMethods(Class<?> type, Set<Method> result, Set<Class<?>> visited) {
		if (type == null)
			return;

		Class<?>[] interfaces;

		try {
			interfaces = type.getInterfaces();
		} catch (Throwable ignored) {
			return;
		}

		for (Class<?> iface : interfaces) {
			if (!visited.add(iface))
				continue;

			try {
				Method[] methods = iface.getDeclaredMethods();

				for (Method method : methods) {
					makeAccessible(method);
					result.add(method);
				}
			} catch (Throwable ignored) {
			}

			collectInterfaceMethods(iface, result, visited);
		}
	}

	/**
	 * Exact method signature lookup.
	 */
	public static Method method(Class<?> type, String name, Class<?>... parameterTypes) {
		if (type == null || name == null)
			return null;

		if (parameterTypes == null)
			parameterTypes = new Class<?>[0];

		Class<?> current = type;

		while (current != null) {
			try {
				Method method = current.getDeclaredMethod(name, parameterTypes);
				makeAccessible(method);
				return method;
			} catch (Throwable ignored) {
			}

			Method fromInterface = methodFromInterfaces(current, name, parameterTypes, new HashSet<>());

			if (fromInterface != null)
				return fromInterface;

			current = current.getSuperclass();
		}

		return null;
	}

	private static Method methodFromInterfaces(Class<?> type, String name, Class<?>[] parameterTypes,
			Set<Class<?>> visited) {

		Class<?>[] interfaces;

		try {
			interfaces = type.getInterfaces();
		} catch (Throwable ignored) {
			return null;
		}

		for (Class<?> iface : interfaces) {
			if (!visited.add(iface))
				continue;

			try {
				Method method = iface.getDeclaredMethod(name, parameterTypes);
				makeAccessible(method);
				return method;
			} catch (Throwable ignored) {
			}

			Method nested = methodFromInterfaces(iface, name, parameterTypes, visited);

			if (nested != null)
				return nested;
		}

		return null;
	}

	/**
	 * Returns every method with this name.
	 */
	public static List<Method> methodByName(Class<?> type, String name) {
		if (type == null || name == null)
			return Collections.emptyList();

		List<Method> result = new ArrayList<>();

		for (Method method : getAllMethods(type))
			if (name.equals(method.getName()))
				result.add(method);

		return result;
	}

	/**
	 * Alias with grammatically plural name.
	 */
	public static List<Method> methodsByName(Class<?> type, String name) {
		return methodByName(type, name);
	}

	/**
	 * Exact return type.
	 */
	public static List<Method> methodByNameWithReturnType(Class<?> type, String name, Class<?> returnType) {

		if (type == null || name == null || returnType == null)
			return Collections.emptyList();

		List<Method> result = new ArrayList<>();

		for (Method method : getAllMethods(type))
			if (name.equals(method.getName()) && method.getReturnType() == returnType)
				result.add(method);

		return result;
	}

	public static List<Method> methodsByNameWithReturnType(Class<?> type, String name, Class<?> returnType) {

		return methodByNameWithReturnType(type, name, returnType);
	}

	public static List<Method> methodsByReturnType(Class<?> type, Class<?> returnType) {

		if (type == null || returnType == null)
			return Collections.emptyList();

		List<Method> result = new ArrayList<>();

		for (Method method : getAllMethods(type))
			if (method.getReturnType() == returnType)
				result.add(method);

		return result;
	}

	public static List<Method> methodsByAssignableReturnType(Class<?> type, Class<?> returnType) {

		if (type == null || returnType == null)
			return Collections.emptyList();

		List<Method> result = new ArrayList<>();

		for (Method method : getAllMethods(type))
			if (returnType.isAssignableFrom(method.getReturnType()))
				result.add(method);

		return result;
	}

	public static Method findMethodByName(Class<?> type, String name) {
		List<Method> methods = methodByName(type, name);
		return methods.isEmpty() ? null : methods.get(0);
	}

	public static boolean existsMethod(Class<?> type, String name) {
		return findMethodByName(type, name) != null;
	}

	public static boolean hasMethod(Class<?> type, String name) {
		return existsMethod(type, name);
	}

	/**
	 * Finds the best matching overload for runtime arguments.
	 *
	 * Supports: - inheritance - interfaces - null - primitive <-> wrapper -
	 * primitive widening
	 */
	public static Method findMethod(Class<?> type, String name, Object... args) {
		return findBestMethod(type, name, false, args);
	}

	public static Method findMethod(Object instance, String name, Object... args) {
		if (instance == null)
			return null;

		return findMethod(instance.getClass(), name, args);
	}

	public static Method findStaticMethod(Class<?> type, String name, Object... args) {
		return findBestMethod(type, name, true, args);
	}

	private static Method findBestMethod(Class<?> type, String name, boolean staticOnly, Object[] args) {

		if (type == null || name == null)
			return null;

		if (args == null)
			args = new Object[0];

		Method best = null;
		int bestScore = -1;

		for (Method method : methodByName(type, name)) {
			if (staticOnly && !Modifier.isStatic(method.getModifiers()))
				continue;

			Class<?>[] parameters = method.getParameterTypes();

			if (parameters.length != args.length)
				continue;

			int score = matchScore(parameters, args);

			if (score < 0)
				continue;

			if (!method.isBridge())
				score += 2;

			if (!method.isSynthetic())
				score += 1;

			if (best == null || score > bestScore || score == bestScore && isMoreSpecific(method, best)) {

				best = method;
				bestScore = score;
			}
		}

		if (best != null)
			makeAccessible(best);

		return best;
	}

	private static boolean isMoreSpecific(Method candidate, Method current) {
		Class<?>[] candidateParams = candidate.getParameterTypes();
		Class<?>[] currentParams = current.getParameterTypes();

		if (candidateParams.length != currentParams.length)
			return false;

		boolean moreSpecific = false;

		for (int i = 0; i < candidateParams.length; ++i) {
			Class<?> candidateType = wrap(candidateParams[i]);
			Class<?> currentType = wrap(currentParams[i]);

			if (candidateType == currentType)
				continue;

			if (currentType.isAssignableFrom(candidateType)) {
				moreSpecific = true;
				continue;
			}

			if (!candidateType.isAssignableFrom(currentType))
				return false;
		}

		return moreSpecific;
	}

	public static Object invoke(Object instance, Method method, Object... args) {
		if (method == null)
			return null;

		try {
			makeAccessible(method);
			return method.invoke(instance, args);
		} catch (Throwable ignored) {
			return null;
		}
	}

	public static Object invoke(Object instance, String methodName, Object... args) {
		if (instance == null)
			return null;

		Method method = findMethod(instance.getClass(), methodName, args);

		if (method == null)
			return null;

		return invoke(instance, method, args);
	}

	public static Object invokeStatic(Method method, Object... args) {
		if (method == null || !Modifier.isStatic(method.getModifiers()))
			return null;

		return invoke(null, method, args);
	}

	public static Object invokeStatic(Class<?> type, String methodName, Object... args) {
		Method method = findStaticMethod(type, methodName, args);

		if (method == null)
			return null;

		return invoke(null, method, args);
	}

	// =====================================================================
	// Constructors
	// =====================================================================

	public static Constructor<?> constructor(Class<?> type, Class<?>... parameterTypes) {

		if (type == null)
			return null;

		if (parameterTypes == null)
			parameterTypes = new Class<?>[0];

		try {
			Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
			makeAccessible(constructor);
			return constructor;
		} catch (Throwable ignored) {
			return null;
		}
	}

	public static List<Constructor<?>> constructors(Class<?> type) {
		if (type == null)
			return Collections.emptyList();

		List<Constructor<?>> result = new ArrayList<>();

		try {
			Constructor<?>[] constructors = type.getDeclaredConstructors();

			for (Constructor<?> constructor : constructors) {
				makeAccessible(constructor);
				result.add(constructor);
			}
		} catch (Throwable ignored) {
		}

		return result;
	}

	public static Constructor<?> findConstructor(Class<?> type, Object... args) {
		if (type == null)
			return null;

		if (args == null)
			args = new Object[0];

		Constructor<?> best = null;
		int bestScore = -1;

		for (Constructor<?> constructor : constructors(type)) {
			Class<?>[] parameters = constructor.getParameterTypes();

			if (parameters.length != args.length)
				continue;

			int score = matchScore(parameters, args);

			if (score < 0)
				continue;

			if (best == null || score > bestScore || score == bestScore && isMoreSpecific(constructor, best)) {

				best = constructor;
				bestScore = score;
			}
		}

		if (best != null)
			makeAccessible(best);

		return best;
	}

	private static boolean isMoreSpecific(Constructor<?> candidate, Constructor<?> current) {

		Class<?>[] candidateParams = candidate.getParameterTypes();
		Class<?>[] currentParams = current.getParameterTypes();

		if (candidateParams.length != currentParams.length)
			return false;

		boolean moreSpecific = false;

		for (int i = 0; i < candidateParams.length; ++i) {
			Class<?> candidateType = wrap(candidateParams[i]);
			Class<?> currentType = wrap(currentParams[i]);

			if (candidateType == currentType)
				continue;

			if (currentType.isAssignableFrom(candidateType)) {
				moreSpecific = true;
				continue;
			}

			if (!candidateType.isAssignableFrom(currentType))
				return false;
		}

		return moreSpecific;
	}

	@SuppressWarnings("unchecked")
	public static <T> T newInstance(Constructor<?> constructor, Object... args) {
		if (constructor == null)
			return null;

		try {
			makeAccessible(constructor);
			return (T) constructor.newInstance(args);
		} catch (Throwable ignored) {
			return null;
		}
	}

	public static <T> T newInstance(Class<T> type, Object... args) {
		return newInstance(findConstructor(type, args), args);
	}

	public static Object newInstanceByClass(String className, Object... args) {
		Class<?> type = getClass(className);

		if (type == null)
			return null;

		return newInstance(type, args);
	}

	public static <T> T newInstanceByClass(Class<T> type, Object... args) {
		return newInstance(type, args);
	}

	// =====================================================================
	// Constructor-less allocation
	// =====================================================================

	/**
	 * Allocates an instance without invoking the target class constructor.
	 *
	 * Strategy:
	 *
	 * 1. sun.reflect.ReflectionFactory 2. sun.misc.Unsafe.allocateInstance via
	 * reflection
	 *
	 * Neither class is referenced at compile time, so this class can be compiled
	 * with Java 8 without importing internal APIs.
	 *
	 * Returns null if the running JVM does not provide either mechanism.
	 */
	public static <T> T allocateInstance(Class<T> type) {
		return ConstructorlessAllocator.allocate(type);
	}

	/**
	 * Backwards-compatible name.
	 */
	public static <T> T newUnsafeInstance(Class<T> type) {
		return allocateInstance(type);
	}

	private static final class ConstructorlessAllocator {

		private static final Object REFLECTION_FACTORY;
		private static final Method NEW_SERIALIZATION_CONSTRUCTOR;
		private static final Constructor<Object> OBJECT_CONSTRUCTOR;

		private static final Object UNSAFE;
		private static final Method UNSAFE_ALLOCATE_INSTANCE;

		static {
			Object reflectionFactory = null;
			Method serializationConstructor = null;
			Constructor<Object> objectConstructor = null;

			Object unsafe = null;
			Method unsafeAllocateInstance = null;

			// -------------------------------------------------------------
			// ReflectionFactory
			// -------------------------------------------------------------

			try {
				Class<?> reflectionFactoryClass = Class.forName("sun.reflect.ReflectionFactory");

				Method getReflectionFactory = reflectionFactoryClass.getMethod("getReflectionFactory");

				reflectionFactory = getReflectionFactory.invoke(null);

				serializationConstructor = reflectionFactoryClass.getMethod("newConstructorForSerialization",
						Class.class, Constructor.class);

				objectConstructor = Object.class.getDeclaredConstructor();

			} catch (Throwable ignored) {
			}

			// -------------------------------------------------------------
			// Unsafe fallback
			//
			// No direct import or compile-time dependency.
			// -------------------------------------------------------------

			try {
				Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");

				Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");

				unsafeField.setAccessible(true);

				unsafe = unsafeField.get(null);

				unsafeAllocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);

			} catch (Throwable ignored) {
			}

			REFLECTION_FACTORY = reflectionFactory;
			NEW_SERIALIZATION_CONSTRUCTOR = serializationConstructor;
			OBJECT_CONSTRUCTOR = objectConstructor;

			UNSAFE = unsafe;
			UNSAFE_ALLOCATE_INSTANCE = unsafeAllocateInstance;
		}

		private ConstructorlessAllocator() {
		}

		@SuppressWarnings("unchecked")
		private static <T> T allocate(Class<T> type) {
			if (type == null)
				return null;

			int modifiers = type.getModifiers();

			if (type.isPrimitive() || type.isArray() || type.isInterface() || Modifier.isAbstract(modifiers)
					|| type == Void.TYPE)
				return null;

			// -------------------------------------------------------------
			// ReflectionFactory first
			// -------------------------------------------------------------

			if (REFLECTION_FACTORY != null && NEW_SERIALIZATION_CONSTRUCTOR != null && OBJECT_CONSTRUCTOR != null)
				try {
					Constructor<?> constructor = (Constructor<?>) NEW_SERIALIZATION_CONSTRUCTOR
							.invoke(REFLECTION_FACTORY, type, OBJECT_CONSTRUCTOR);

					if (constructor != null) {
						constructor.setAccessible(true);

						Object instance = constructor.newInstance();

						return (T) instance;
					}
				} catch (Throwable ignored) {
				}

			// -------------------------------------------------------------
			// Unsafe fallback
			// -------------------------------------------------------------

			if (UNSAFE != null && UNSAFE_ALLOCATE_INSTANCE != null)
				try {
					return (T) UNSAFE_ALLOCATE_INSTANCE.invoke(UNSAFE, type);

				} catch (Throwable ignored) {
				}

			return null;
		}
	}

	// =====================================================================
	// Argument matching
	// =====================================================================

	private static int matchScore(Class<?>[] parameterTypes, Object[] args) {
		if (parameterTypes.length != args.length)
			return -1;

		int score = 0;

		for (int i = 0; i < parameterTypes.length; ++i) {
			int parameterScore = matchScore(parameterTypes[i], args[i]);

			if (parameterScore < 0)
				return -1;

			score += parameterScore;
		}

		return score;
	}

	private static int matchScore(Class<?> expected, Object value) {
		if (value == null)
			return expected.isPrimitive() ? -1 : 10;

		Class<?> actual = value.getClass();

		// Exact reference type
		if (expected == actual)
			return 100;

		// Primitive
		if (expected.isPrimitive()) {
			Class<?> actualPrimitive = unwrap(actual);

			if (actualPrimitive == null)
				return -1;

			// Integer -> int
			if (actualPrimitive == expected)
				return 95;

			int widening = primitiveWideningDistance(actualPrimitive, expected);

			if (widening >= 0)
				return 80 - widening;

			return -1;
		}

		// Normal inheritance/interface
		if (expected.isAssignableFrom(actual))
			return 60;

		return -1;
	}

	private static Class<?> wrap(Class<?> type) {
		if (type == null || !type.isPrimitive())
			return type;

		if (type == Boolean.TYPE)
			return Boolean.class;

		if (type == Byte.TYPE)
			return Byte.class;

		if (type == Short.TYPE)
			return Short.class;

		if (type == Character.TYPE)
			return Character.class;

		if (type == Integer.TYPE)
			return Integer.class;

		if (type == Long.TYPE)
			return Long.class;

		if (type == Float.TYPE)
			return Float.class;

		if (type == Double.TYPE)
			return Double.class;

		if (type == Void.TYPE)
			return Void.class;

		return type;
	}

	private static Class<?> unwrap(Class<?> type) {
		if (type == Boolean.class)
			return Boolean.TYPE;

		if (type == Byte.class)
			return Byte.TYPE;

		if (type == Short.class)
			return Short.TYPE;

		if (type == Character.class)
			return Character.TYPE;

		if (type == Integer.class)
			return Integer.TYPE;

		if (type == Long.class)
			return Long.TYPE;

		if (type == Float.class)
			return Float.TYPE;

		if (type == Double.class)
			return Double.TYPE;

		if (type == Void.class)
			return Void.TYPE;

		return type.isPrimitive() ? type : null;
	}

	private static int primitiveWideningDistance(Class<?> from, Class<?> to) {

		if (from == to)
			return 0;

		if (from == Boolean.TYPE || to == Boolean.TYPE)
			return -1;

		if (from == Byte.TYPE) {
			if (to == Short.TYPE)
				return 1;
			if (to == Integer.TYPE)
				return 2;
			if (to == Long.TYPE)
				return 3;
			if (to == Float.TYPE)
				return 4;
			if (to == Double.TYPE)
				return 5;
		}

		if (from == Short.TYPE) {
			if (to == Integer.TYPE)
				return 1;
			if (to == Long.TYPE)
				return 2;
			if (to == Float.TYPE)
				return 3;
			if (to == Double.TYPE)
				return 4;
		}

		if (from == Character.TYPE) {
			if (to == Integer.TYPE)
				return 1;
			if (to == Long.TYPE)
				return 2;
			if (to == Float.TYPE)
				return 3;
			if (to == Double.TYPE)
				return 4;
		}

		if (from == Integer.TYPE) {
			if (to == Long.TYPE)
				return 1;
			if (to == Float.TYPE)
				return 2;
			if (to == Double.TYPE)
				return 3;
		}

		if (from == Long.TYPE) {
			if (to == Float.TYPE)
				return 1;
			if (to == Double.TYPE)
				return 2;
		}

		if (from == Float.TYPE && to == Double.TYPE)
			return 1;

		return -1;
	}

	// =====================================================================
	// Minecraft
	// =====================================================================

	public static Class<?> nms(String modernPackageName, String name) {
		if (name == null)
			return null;

		try {
			if (isAtLeast(16, 0)) {
				if (modernPackageName == null || modernPackageName.isEmpty())
					return Class.forName("net.minecraft." + name);

				return Class.forName("net.minecraft." + modernPackageName + "." + name);
			}

			return Class.forName("net.minecraft.server." + versionString() + "." + name);

		} catch (Throwable ignored) {
			return null;
		}
	}

	public static Class<?> craft(String name) {
		if (name == null)
			return null;

		try {
			if (type() == ServerType.PAPER && isAtLeast(20, 5))
				return Class.forName("org.bukkit.craftbukkit." + name);

			return Class.forName("org.bukkit.craftbukkit." + versionString() + "." + name);

		} catch (Throwable ignored) {
			return null;
		}
	}
}