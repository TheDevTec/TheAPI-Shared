package me.devtec.shared.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import me.devtec.shared.database.DatabaseAPI.DatabaseSettings;
import me.devtec.shared.database.DatabaseAPI.DatabaseType;
import me.devtec.shared.dataholder.StringContainer;

/**
 * One serialized JDBC session. Prefer one handler per unit of work or a pooled
 * DataSource.
 */
public class SqlHandler implements DatabaseHandler {
	private Connection sql;
	private final DatabaseSettings settings;
	private final String path;
	private final DatabaseType type;
	private int transactionDepth;

	public SqlHandler(String path, DatabaseSettings settings) throws SQLException {
		this(detect(path), path, settings);
	}

	public SqlHandler(DatabaseType type, String path, DatabaseSettings settings) throws SQLException {
		this.type = Objects.requireNonNull(type);
		this.path = Objects.requireNonNull(path);
		this.settings = Objects.requireNonNull(settings);
		open();
	}

	/**
	 * Owns the supplied connection; close returns a pooled connection to its pool.
	 */
	public SqlHandler(Connection connection, DatabaseType type) throws SQLException {
		this.sql = Objects.requireNonNull(connection);
		this.type = Objects.requireNonNull(type);
		path = null;
		settings = null;
		if (connection.isClosed())
			throw new SQLException("Connection is closed");
	}

	private static DatabaseType detect(String path) throws SQLException {
		for (DatabaseType type : DatabaseType.values())
			if (path.toLowerCase(Locale.ROOT).startsWith("jdbc:" + type.getName() + ":"))
				return type;
		throw new SQLException("Unsupported JDBC protocol");
	}

	@Override
	public DatabaseType getType() {
		return type;
	}

	@Override
	public synchronized boolean isConnected() throws SQLException {
		return sql != null && !sql.isClosed() && sql.isValid(2);
	}

	private Connection connection() throws SQLException {
		if (sql == null || sql.isClosed())
			throw new SQLException("Database is closed; call open() explicitly");
		return sql;
	}

	@Override
	public synchronized void open() throws SQLException {
		if (transactionDepth != 0)
			throw new SQLException("Cannot reopen during a transaction");
		if (sql != null && !sql.isClosed())
			return;
		if (path == null)
			throw new SQLException("Externally supplied connections cannot be reopened");
		Properties properties = new Properties();
		if (settings.getUser() != null)
			properties.setProperty("user", settings.getUser());
		if (settings.getPassword() != null)
			properties.setProperty("password", settings.getPassword());
		sql = DriverManager.getConnection(path, properties);
	}

	@Override
	public synchronized void close() throws SQLException {
		if (transactionDepth != 0)
			throw new SQLException("Cannot close during a transaction");
		if (sql != null)
			try {
				sql.close();
			} finally {
				sql = null;
			}
	}

	@Override
	public synchronized PreparedStatement prepareStatement(String command) throws SQLException {
		return connection().prepareStatement(command);
	}

	@Override
	public synchronized SqlStatement compile(Sql.Query query) throws SQLException {
		if (query instanceof Sql.Mutation && type == DatabaseType.SQLITE) {
			Sql.Mutation<?> mutation = (Sql.Mutation<?>) query;
			if (mutation.limit != null && mutation.keys == null) {
				Sql.Context context = new Sql.Context(type);
				context.mutationKeys = sqliteIdentity(mutation.table);
				context.query(query);
				return new SqlStatement(context.sql.toString(), context.parameters);
			}
		}
		return Sql.compile(query, type);
	}

	private String[] sqliteIdentity(String table) throws SQLException {
		// rowid tables may have nullable PKs; hidden row identity is the safe default.
		String schema = schemaPart(table), name = tablePart(table);
		String master = schema == null
				? "(SELECT name, type, sql FROM sqlite_temp_master UNION ALL SELECT name, type, sql FROM sqlite_master)"
				: quote(schema) + ".sqlite_master";
		String ddl = null;
		try (PreparedStatement statement = connection()
				.prepareStatement("SELECT sql FROM " + master + " WHERE type='table' AND name=?")) {
			statement.setString(1, name);
			try (ResultSet result = statement.executeQuery()) {
				if (result.next())
					ddl = result.getString(1);
			}
		}
		if (ddl == null)
			throw new SQLException("Unknown SQLite table: " + table);
		Set<String> names = new HashSet<>(), nullable = new HashSet<>();
		try (ResultSet columns = connection().getMetaData().getColumns(null, schema, metadataPattern(name), null)) {
			while (columns.next()) {
				String column = columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
				names.add(column);
				if (columns.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls)
					nullable.add(column);
			}
		}
		boolean withoutRowid = ddl.toUpperCase(Locale.ROOT).matches("(?s).*\\bWITHOUT\\s+ROWID\\b.*");
		if (!withoutRowid)
			for (String rowid : new String[] { "_rowid_", "rowid", "oid" })
				if (!names.contains(rowid))
					return new String[] { rowid };
		SortedMap<Short, String> keys = new TreeMap<>();
		try (ResultSet primary = connection().getMetaData().getPrimaryKeys(null, schema, name)) {
			while (primary.next())
				keys.put(primary.getShort("KEY_SEQ"), primary.getString("COLUMN_NAME"));
		}
		if (keys.isEmpty())
			throw new SQLFeatureNotSupportedException("No unambiguous SQLite row identity; specify keyColumns");
		if (!withoutRowid)
			for (String key : keys.values())
				if (nullable.contains(key.toLowerCase(Locale.ROOT)))
					throw new SQLFeatureNotSupportedException(
							"Nullable SQLite primary key is not a safe row identity; specify non-null keyColumns");
		return keys.values().toArray(new String[0]);
	}

	@Override
	public synchronized <T> List<T> query(Sql.Query query, RowMapper<T> mapper) throws SQLException {
		return DatabaseHandler.super.query(query, mapper);
	}

	@Override
	public synchronized void forEach(Sql.Query query, RowConsumer consumer) throws SQLException {
		DatabaseHandler.super.forEach(query, consumer);
	}

	@Override
	public synchronized int update(Sql.Query query) throws SQLException {
		return DatabaseHandler.super.update(query);
	}

	@Override
	public synchronized boolean exists(Sql.Select query) throws SQLException {
		return DatabaseHandler.super.exists(query);
	}

	@Override
	public synchronized int[] batch(String sql, List<Object[]> rows) throws SQLException {
		return DatabaseHandler.super.batch(sql, rows);
	}

	@Override
	public synchronized List<Object> insertKeys(Sql.Insert query) throws SQLException {
		SqlStatement compiled = compile(query);
		List<Object> keys = new ArrayList<>();
		try (PreparedStatement statement = connection().prepareStatement(compiled.sql(),
				Statement.RETURN_GENERATED_KEYS)) {
			compiled.bind(statement);
			statement.executeUpdate();
			try (ResultSet result = statement.getGeneratedKeys()) {
				while (result.next())
					keys.add(result.getObject(1));
			}
		}
		return keys;
	}

	@Override
	public synchronized <T> T transaction(Transaction<T> work) throws SQLException {
		Connection connection = connection();
		boolean owns = connection.getAutoCommit();
		Savepoint savepoint = null;
		if (owns)
			connection.setAutoCommit(false);
		else
			savepoint = connection.setSavepoint();
		transactionDepth++;
		Throwable failure = null;
		boolean rollbackFailed = false;
		try {
			T result = work.execute(this);
			if (owns)
				connection.commit();
			else if (type != DatabaseType.SQLSERVER)
				connection.releaseSavepoint(savepoint);
			return result;
		} catch (SQLException | RuntimeException | Error error) {
			failure = error;
			try {
				if (owns)
					connection.rollback();
				else
					connection.rollback(savepoint);
			} catch (SQLException rollback) {
				rollbackFailed = true;
				error.addSuppressed(rollback);
				// Restoring auto-commit after a failed rollback could commit partial work.
				try {
					connection.close();
				} catch (SQLException close) {
					error.addSuppressed(close);
				}
			}
			throw error;
		} finally {
			transactionDepth--;
			if (owns && !rollbackFailed)
				try {
					if (!connection.isClosed())
						connection.setAutoCommit(true);
				} catch (SQLException restore) {
					if (failure == null)
						throw restore;
					failure.addSuppressed(restore);
				}
		}
	}

	@Override
	public boolean execute(PreparedStatement statement) throws SQLException {
		return statement.execute();
	}

	@Override
	public int executeUpdate(PreparedStatement statement) throws SQLException {
		return statement.executeUpdate();
	}

	@Override
	public ResultSet executeQuery(PreparedStatement statement) throws SQLException {
		return statement.executeQuery();
	}

	@Override
	public int[] executeBatch(PreparedStatement statement) throws SQLException {
		return statement.executeBatch();
	}

	@Override
	public long[] executeLargeBatch(PreparedStatement statement) throws SQLException {
		return statement.executeLargeBatch();
	}

	private String quote(String name) {
		Sql.Context context = new Sql.Context(type);
		context.identifier(name);
		return context.sql.toString();
	}

	private String schema() throws SQLException {
		try {
			return connection().getSchema();
		} catch (SQLFeatureNotSupportedException | AbstractMethodError ignored) {
			return null;
		}
	}

	private static String schemaPart(String table) {
		int split = table.lastIndexOf('.');
		return split < 0 ? null : table.substring(0, split);
	}

	private static String tablePart(String table) {
		int split = table.lastIndexOf('.');
		return split < 0 ? table : table.substring(split + 1);
	}

	private String metadataPattern(String name) throws SQLException {
		String escape = connection().getMetaData().getSearchStringEscape();
		return escape == null || escape.isEmpty() ? name
				: name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
	}

	@Override
	public synchronized List<String> getTables() throws SQLException {
		List<String> tables = new ArrayList<>();
		try (ResultSet result = connection().getMetaData().getTables(connection().getCatalog(), schema(), "%",
				new String[] { "TABLE" })) {
			while (result.next())
				tables.add(result.getString("TABLE_NAME"));
		}
		return tables;
	}

	@Override
	public synchronized Row[] getTableValues(String name) throws SQLException {
		String schema = schemaPart(name), catalog = connection().getCatalog();
		if (type == DatabaseType.MYSQL || type == DatabaseType.MARIADB) {
			if (schema != null)
				catalog = schema;
			schema = null;
		} else if (schema == null)
			schema = schema();
		String table = tablePart(name);
		Set<String> primary = new HashSet<>();
		DatabaseMetaData metadata = connection().getMetaData();
		try (ResultSet result = metadata.getPrimaryKeys(catalog, schema, table)) {
			while (result.next())
				primary.add(result.getString("COLUMN_NAME"));
		}
		List<Row> rows = new ArrayList<>();
		try (ResultSet result = metadata.getColumns(catalog, schema, metadataPattern(table), null)) {
			while (result.next())
				rows.add(new Row(result.getString("COLUMN_NAME"), result.getString("TYPE_NAME"),
						result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
						primary.contains(result.getString("COLUMN_NAME")) ? "PRI" : "", result.getString("COLUMN_DEF"),
						"YES".equalsIgnoreCase(result.getString("IS_AUTOINCREMENT")) ? "AUTO_INCREMENT" : ""));
		}
		return rows.toArray(new Row[0]);
	}

	@Override
	public synchronized boolean createTable(String name, Row[] values) throws SQLException {
		String columns = buildTableValues(values), table = quote(name);
		String command = "CREATE TABLE " + (type == DatabaseType.SQLSERVER ? "" : "IF NOT EXISTS ") + table + " ("
				+ columns + ")";
		if (type == DatabaseType.SQLSERVER)
			command = "IF OBJECT_ID(N'" + name.replace("'", "''") + "', N'U') IS NULL " + command;
		try (PreparedStatement statement = prepareStatement(command)) {
			statement.executeUpdate();
			return true;
		}
	}

	@Override
	public synchronized boolean deleteTable(String name) throws SQLException {
		try (PreparedStatement statement = prepareStatement("DROP TABLE " + quote(name))) {
			statement.executeUpdate();
			return true;
		}
	}

	public String buildTableValues(Row[] rows) {
		if (rows == null || rows.length == 0)
			throw new IllegalArgumentException("Table needs columns");
		StringContainer result = new StringContainer();
		List<String> primaryColumns = new ArrayList<>();
		for (Row row : rows)
			if ("PRI".equalsIgnoreCase(row.getKey()) || "PRIMARY KEY".equalsIgnoreCase(row.getKey()))
				primaryColumns.add(row.getFieldName());
		for (Row row : rows) {
			if (result.length() != 0)
				result.append(", ");
			String fieldType = row.getFieldType().toUpperCase(Locale.ROOT);
			String extra = row.getExtra() == null ? "" : row.getExtra().trim();
			boolean identity = "AUTO_INCREMENT".equalsIgnoreCase(extra) || "AUTOINCREMENT".equalsIgnoreCase(extra);
			boolean primary = "PRI".equalsIgnoreCase(row.getKey()) || "PRIMARY KEY".equalsIgnoreCase(row.getKey());
			if (!extra.isEmpty() && !identity)
				throw new IllegalArgumentException("Use explicit DDL for vendor-specific column extras");
			if (!fieldType.matches("[A-Z][A-Z0-9_]*(\\([0-9]+(,[ ]*[0-9]+)?\\))?"))
				throw new IllegalArgumentException("Use explicit DDL for vendor-specific field types");
			if ("LONG".equals(fieldType))
				fieldType = "BIGINT";
			if (type == DatabaseType.SQLSERVER && "TIMESTAMP".equals(fieldType))
				fieldType = "DATETIME2";
			if (type == DatabaseType.SQLITE && identity) {
				if (!primary || primaryColumns.size() != 1 || (!"INT".equals(fieldType) && !"INTEGER".equals(fieldType)))
					throw new IllegalArgumentException("SQLite AUTOINCREMENT requires a single INTEGER PRIMARY KEY");
				fieldType = "INTEGER";
			}
			if (type == DatabaseType.SQLSERVER) {
				if ("BOOLEAN".equals(fieldType) || "BOOL".equals(fieldType))
					fieldType = "BIT";
				if ("DOUBLE".equals(fieldType))
					fieldType = "FLOAT";
				if (fieldType.contains("BLOB"))
					fieldType = "VARBINARY(MAX)";
				if (fieldType.endsWith("TEXT"))
					fieldType = "VARCHAR(MAX)";
				if ("DATETIME".equals(fieldType))
					fieldType = "DATETIME2";
			}
			if (type == DatabaseType.H2) {
				if ("DATETIME".equals(fieldType))
					fieldType = "TIMESTAMP";
				if (fieldType.contains("BLOB"))
					fieldType = "BLOB";
				if (fieldType.endsWith("TEXT"))
					fieldType = "CLOB";
			}
			if (type != DatabaseType.MYSQL && type != DatabaseType.MARIADB)
				if ("MEDIUMINT".equals(fieldType) || "YEAR".equals(fieldType))
					fieldType = "INT";
			result.append(quote(row.getFieldName())).append(' ').append(fieldType);
			if (identity && type == DatabaseType.H2)
				result.append(" GENERATED BY DEFAULT AS IDENTITY");
			if (identity && type == DatabaseType.SQLSERVER)
				result.append(" IDENTITY(1,1)");
			if (primary && primaryColumns.size() == 1)
				result.append(" PRIMARY KEY");
			else if ("UNI".equalsIgnoreCase(row.getKey()) || "UNIQUE".equalsIgnoreCase(row.getKey()))
				result.append(" UNIQUE");
			if (identity && type == DatabaseType.SQLITE)
				result.append(" AUTOINCREMENT");
			result.append(row.isNulled() ? " NULL" : " NOT NULL");
			if (identity && (type == DatabaseType.MYSQL || type == DatabaseType.MARIADB))
				result.append(" AUTO_INCREMENT");
			String defaultValue = row.getDefaultValue();
			if (defaultValue != null && !defaultValue.isEmpty()) {
				result.append(" DEFAULT ");
				if ("NULL".equalsIgnoreCase(defaultValue) || "CURRENT_TIMESTAMP".equalsIgnoreCase(defaultValue)
						|| defaultValue.matches("-?[0-9]+(\\.[0-9]+)?"))
					result.append(defaultValue);
				else
					result.append('\'').append(defaultValue.replace("'", "''")).append('\'');
			}
		}
		if (primaryColumns.size() > 1) {
			result.append(", PRIMARY KEY (");
			for (int i = 0; i < primaryColumns.size(); i++) {
				if (i != 0)
					result.append(", ");
				result.append(quote(primaryColumns.get(i)));
			}
			result.append(')');
		}
		return result.toString();
	}
}
