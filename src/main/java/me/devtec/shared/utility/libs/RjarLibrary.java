package me.devtec.shared.utility.libs;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class RjarLibrary {

	/**
	 * @apiNote Add to the MANIFEST.MF, Class-Path: .
	 */
	public static Class<?> loadJars(List<String> pathToJars, String mainClass) throws Exception {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		URL.setURLStreamHandlerFactory(new RjarURLStreamHandlerFactory(cl));

		pathToJars.add(0, "./");

		String[] libsToLoad = pathToJars.toArray(new String[0]);

		URL[] rjarUrls = new URL[libsToLoad.length];

		for (int i = 0; i < libsToLoad.length; ++i) {
			String rjarPath = libsToLoad[i];
			if (rjarPath.endsWith("/"))
				rjarUrls[i] = new URL("rjar:" + rjarPath);
			else
				rjarUrls[i] = new URL("jar:rjar:" + rjarPath + "!/");
		}

		ClassLoader classLoader = new URLClassLoader(rjarUrls, getPlatformClassLoader());
		Thread.currentThread().setContextClassLoader(classLoader);

		return Class.forName(mainClass, true, classLoader);
	}

	private static ClassLoader getPlatformClassLoader() throws Exception {
		try {
			Method platformClassLoader = ClassLoader.class.getMethod("getPlatformClassLoader");
			return (ClassLoader) platformClassLoader.invoke(null);
		} catch (NoSuchMethodException var1) {
			return ClassLoader.getSystemClassLoader().getParent();
		}
	}

}
