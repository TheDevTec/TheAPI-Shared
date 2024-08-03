package me.devtec.shared.utility;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.SecureClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import me.devtec.shared.Ref;

public class MemoryCompiler {

	public static String allJars = System.getProperty("java.class.path").charAt(0) == '/' ? System.getProperty("java.class.path") : "./" + System.getProperty("java.class.path");

	private JavaFileManager fileManager;
	private String fullName;
	private String sourceCode;
	private ClassLoader original;

	public MemoryCompiler(ClassLoader loader, String fullName, File pathToJavaFile) {
		if (!pathToJavaFile.exists())
			throw new RuntimeException("File doesn't exist.");

		if (ToolProvider.getSystemJavaCompiler() == null)
			throw new UnsupportedOperationException("MemoryCompiler class cannot be initialized. You need an installed version of the Java JDK to run this class.");

		original = loader == null ? Thread.currentThread().getContextClassLoader() : loader;
		this.fullName = fullName;
		sourceCode = StreamUtils.fromStream(pathToJavaFile);
		fileManager = initFileManager();
	}

	public MemoryCompiler(ClassLoader loader, String fullName, String srcCode) {
		if (ToolProvider.getSystemJavaCompiler() == null)
			throw new UnsupportedOperationException("MemoryCompiler class cannot be initialized. You need an installed version of the Java JDK to run this class.");

		original = loader == null ? Thread.currentThread().getContextClassLoader() : loader;
		this.fullName = fullName;
		sourceCode = srcCode;
		fileManager = initFileManager();
	}

	public JavaFileManager initFileManager() {
		if (fileManager != null)
			return fileManager;
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		fileManager = new ClassFileManager(compiler.getStandardFileManager(null, null, null));
		return fileManager;
	}

	private void compile() {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		compiler.getTask(null, fileManager, null, Arrays.asList("-nowarn", "-cp", allJars), null, Arrays.asList(new CharSequenceJavaFileObject(fullName, sourceCode))).call();
	}

	public Class<?> buildClass() {
		compile();
		try {
			return fileManager.getClassLoader(null).loadClass(fullName);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public class CharSequenceJavaFileObject extends SimpleJavaFileObject {

		/**
		 * CharSequence representing the source code to be compiled
		 */
		private CharSequence content;

		public CharSequenceJavaFileObject(String className, CharSequence content) {
			super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
			this.content = content;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return content;
		}
	}

	@SuppressWarnings("rawtypes")
	public class ClassFileManager extends ForwardingJavaFileManager {
		Map<String, JavaClassObject> loaded = new HashMap<>();

		@SuppressWarnings("unchecked")
		public ClassFileManager(StandardJavaFileManager standardManager) {
			super(standardManager);
		}

		@Override
		public ClassLoader getClassLoader(Location location) {
			return new SecureClassLoader(original) {

				@Override
				protected Class<?> findClass(String name) throws ClassNotFoundException {
					JavaClassObject javaClassObject = loaded.get(name);
					if (javaClassObject == null)
						return Ref.getClass(name);

					byte[] b = javaClassObject.getBytes();
					return super.defineClass(name, javaClassObject.getBytes(), 0, b.length);
				}
			};
		}

		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
			JavaClassObject javaClassObject = new JavaClassObject(className, kind);
			loaded.put(className, javaClassObject);
			return javaClassObject;
		}
	}

	public class JavaClassObject extends SimpleJavaFileObject {
		protected final ByteArrayOutputStream bos = new ByteArrayOutputStream();

		public JavaClassObject(String name, Kind kind) {
			super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
		}

		public byte[] getBytes() {
			return bos.toByteArray();
		}

		@Override
		public OutputStream openOutputStream() throws IOException {
			return bos;
		}
	}
}