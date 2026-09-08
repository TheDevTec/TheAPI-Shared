package me.devtec.shared.database;

import java.io.File;
import java.sql.SQLException;

import me.devtec.shared.API;

public class DatabaseAPI {
	public interface DatabaseSettings {
		DatabaseSettings attributes(String attributes);

		String getUser();

		String getPassword();

		String getConnectionString();
	}

	public static class SqlDatabaseSettings implements DatabaseSettings {
		private final String ip;
		private final String database;
		private final String username;
		private final String password;
		private final int port;
		private String attributes;
		private final String sqlType;

		public SqlDatabaseSettings(DatabaseType sqlType, String ip, int port, String database, String username,
				String password) {
			this.ip = ip;
			this.port = port;
			this.sqlType = sqlType.getName();
			this.database = database;
			this.username = username;
			this.password = password;
		}

		@Override
		public DatabaseSettings attributes(String attributes) {
			this.attributes = attributes;
			return this;
		}

		@Override
		public String getUser() {
			return username;
		}

		@Override
		public String getPassword() {
			return password;
		}

		@Override
		public String getConnectionString() {
			if ("sqlserver".equals(sqlType)) {
				String attrs = attributes;
				if (attrs == null || attrs.trim().isEmpty())
					attrs = "loginTimeout=5;socketTimeout=30000;";

				return "jdbc:sqlserver://" + ip + ":" + port + ";databaseName={" + database.replace("}", "}}") + "};"
						+ attrs;
			}

			String attrs = attributes;

			if (attrs == null || attrs.trim().isEmpty())
				if ("mysql".equals(sqlType) || "mariadb".equals(sqlType))
					attrs = "connectTimeout=5000&socketTimeout=30000&tcpKeepAlive=true";
				else
					attrs = "";

			return "jdbc:" + sqlType + "://" + ip + ":" + port + "/" + database
					+ (attrs.isEmpty() ? "" : attrs.startsWith("?") ? attrs : "?" + attrs);
		}
	}

	public static class SqliteDatabaseSettings implements DatabaseSettings {
		private final String file;
		private final String username;
		private final String password;
		private String attributes;
		private final String sqlType;

		public SqliteDatabaseSettings(DatabaseType sqlType, String file, String username, String password) {
			this.file = file;
			this.username = username;
			this.password = password;
			this.sqlType = sqlType.getName();
		}

		@Override
		public SqliteDatabaseSettings attributes(String attributes) {
			this.attributes = attributes;
			return this;
		}

		@Override
		public String getUser() {
			return username;
		}

		@Override
		public String getPassword() {
			return password;
		}

		@Override
		public String getConnectionString() {
			String location = file;
			if ("h2".equals(sqlType) && !location.startsWith("mem:") && !location.startsWith("file:")
					&& !location.startsWith("tcp:") && !location.startsWith("ssl:") && !location.startsWith("~")
					&& !new File(location).isAbsolute() && !location.startsWith("./") && !location.startsWith("../"))
				location = "./" + location;
			return "jdbc:" + sqlType + ":" + location + (attributes == null ? "" : attributes);
		}
	}

	public enum DatabaseType {
		MYSQL("mysql", false), MARIADB("mariadb", false), SQLSERVER("sqlserver", false), SQLITE("sqlite", true),
		H2("h2", true);

		private final String name;
		private final boolean fileBased;

		DatabaseType(String name, boolean fileBased) {
			this.name = name;
			this.fileBased = fileBased;
		}

		public String getName() {
			return name;
		}

		public boolean isFileBased() {
			return fileBased;
		}
	}

	public static DatabaseHandler openConnection(DatabaseType type, DatabaseSettings settings) throws SQLException {
		java.util.Objects.requireNonNull(type, "DatabaseType");
		java.util.Objects.requireNonNull(settings, "DatabaseSettings");
		String url = settings.getConnectionString();
		if (!url.startsWith("jdbc:" + type.getName() + ":"))
			throw new SQLException("Settings JDBC protocol does not match DatabaseType");
		String driver;
		switch (type) {
		case MYSQL:
			driver = "com.mysql.cj.jdbc.Driver";
			break;
		case MARIADB:
			driver = "org.mariadb.jdbc.Driver";
			break;
		case SQLSERVER:
			driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
			break;
		case SQLITE:
			driver = "org.sqlite.JDBC";
			break;
		case H2:
			driver = "org.h2.Driver";
			break;
		default:
			throw new SQLException("Unsupported database type");
		}
		try {
			Class.forName(driver);
		} catch (ClassNotFoundException missing) {
			if (API.library == null)
				throw new SQLException("Add JDBC driver to the classpath: " + driver, missing);
			try {
				checkOrDownloadIfNeeded(type.getName());
				Class.forName(driver);
			} catch (Exception | LinkageError failure) {
				throw new SQLException("Unable to load JDBC driver: " + driver, failure);
			}
		}
		return new SqlHandler(type, url, settings);
	}

	public static DatabaseHandler openConnection(DatabaseType type, javax.sql.DataSource source) throws SQLException {
		java.util.Objects.requireNonNull(type);
		java.util.Objects.requireNonNull(source);
		java.sql.Connection connection = source.getConnection();
		try {
			return new SqlHandler(connection, type);
		} catch (SQLException | RuntimeException failure) {
			try {
				connection.close();
			} catch (SQLException close) {
				failure.addSuppressed(close);
			}
			throw failure;
		}
	}

	/**
	 * Allows explicit JDBC URL options, in-memory databases and externally supplied
	 * drivers.
	 */
	public static DatabaseSettings jdbc(String url, String user, String password) {
		java.util.Objects.requireNonNull(url);
		return new DatabaseSettings() {
			private String suffix = "";

			@Override
			public DatabaseSettings attributes(String value) {
				suffix = value == null ? "" : value;
				return this;
			}

			@Override
			public String getUser() {
				return user;
			}

			@Override
			public String getPassword() {
				return password;
			}

			@Override
			public String getConnectionString() {
				return url + suffix;
			}
		};
	}

	private static void checkOrDownloadIfNeeded(String driver) {
		File file = new File("plugins/TheAPI/libraries/" + driver + ".jar");
		if (!file.exists())
			API.library.downloadFileFromUrl("https://github.com/TheDevTec/TheAPI/raw/main/" + driver + ".jar", file);
		API.library.load(file);
	}
}
