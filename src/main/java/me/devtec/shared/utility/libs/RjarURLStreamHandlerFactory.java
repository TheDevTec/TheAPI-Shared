package me.devtec.shared.utility.libs;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

public class RjarURLStreamHandlerFactory implements URLStreamHandlerFactory {
	private final ClassLoader classLoader;
	private URLStreamHandlerFactory chainFac;

	public RjarURLStreamHandlerFactory(ClassLoader cl) {
		classLoader = cl;
	}

	@Override
	public URLStreamHandler createURLStreamHandler(String protocol) {
		if ("rjar".equals(protocol)) {
			return new RjarURLStreamHandler(classLoader);
		}
		return chainFac != null ? chainFac.createURLStreamHandler(protocol) : null;
	}

	public void setURLStreamHandlerFactory(URLStreamHandlerFactory fac) {
		chainFac = fac;
	}

}
