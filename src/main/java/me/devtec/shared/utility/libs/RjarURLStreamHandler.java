package me.devtec.shared.utility.libs;

import java.net.URL;
import java.net.URLConnection;

public class RjarURLStreamHandler extends java.net.URLStreamHandler {
	private final ClassLoader classLoader;

	public RjarURLStreamHandler(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	protected URLConnection openConnection(URL u) {
		return new RjarURLConnection(u, classLoader);
	}

	@Override
	protected void parseURL(URL url, String spec, int start, int limit) {
		String file;
		if (spec.startsWith("rjar:")) {
			file = spec.substring(5);
		} else if (url.getFile().equals("./")) {
			file = spec;
		} else if (url.getFile().endsWith("/")) {
			file = url.getFile() + spec;
		} else if ("#runtime".equals(spec)) {
			file = url.getFile();
		} else {
			file = spec;
		}

		this.setURL(url, "rjar", "", -1, null, null, file, null, null);
	}
}
