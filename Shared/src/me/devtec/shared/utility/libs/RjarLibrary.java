package me.devtec.shared.utility.libs;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class RjarLibrary {

	public static Class<?> loadJars(List<String> pathToJars, String mainClass) throws Exception {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		URL.setURLStreamHandlerFactory(new RjarURLStreamHandlerFactory(cl));

		pathToJars.add(0, "./");

		String[] libsToLoad = pathToJars.toArray(new String[0]);

		URL[] rsrcUrls = new URL[libsToLoad.length];

		for (int i = 0; i < libsToLoad.length; ++i) {
			String rsrcPath = libsToLoad[i];
			if (rsrcPath.endsWith("/"))
				rsrcUrls[i] = new URL("rjar:" + rsrcPath);
			else
				rsrcUrls[i] = new URL("jar:rjar:" + rsrcPath + "!/");
		}

		ClassLoader classLoader = new URLClassLoader(rsrcUrls, ClassLoader.getPlatformClassLoader());
		Thread.currentThread().setContextClassLoader(classLoader);

		return Class.forName(mainClass, true, classLoader);
	}

}
