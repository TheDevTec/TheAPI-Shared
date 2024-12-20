package me.devtec.shared.utility.libs;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;

public class RjarURLConnection extends URLConnection {
	private final ClassLoader classLoader;

	public RjarURLConnection(URL url, ClassLoader classLoader) {
		super(url);
		this.classLoader = classLoader;
	}

	@Override
	public void connect() {
	}

	@Override
	public InputStream getInputStream() throws IOException {
		String file = URLDecoder.decode(url.getFile(), "UTF-8");
		InputStream result = classLoader.getResourceAsStream(file);
		if (result == null) {
			throw new MalformedURLException("Could not open InputStream for URL '" + url + "'");
		}
		return result;
	}
}
