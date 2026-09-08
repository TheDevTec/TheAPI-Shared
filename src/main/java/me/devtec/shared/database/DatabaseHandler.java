package me.devtec.shared.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.database.DatabaseAPI.DatabaseType;

public interface DatabaseHandler extends AutoCloseable {
	@FunctionalInterface
	interface RowMapper<T> {
		T map(ResultSet result) throws SQLException;
	}

	@FunctionalInterface
	interface RowConsumer {
		void accept(ResultSet result) throws SQLException;
	}

	@FunctionalInterface
	interface Transaction<T> {
		T execute(DatabaseHandler database) throws SQLException;
	}

	default SqlStatement compile(Sql.Query query) throws SQLException {
		return Sql.compile(query, getType());
	}

	default List<SqlRow> query(Sql.Query query) throws SQLException {
		return query(query, SqlRow::new);
	}

	default <T> List<T> query(Sql.Query query, RowMapper<T> mapper) throws SQLException {
		List<T> rows = new ArrayList<>();
		forEach(query, result -> rows.add(mapper.map(result)));
		return rows;
	}

	default void forEach(Sql.Query query, RowConsumer consumer) throws SQLException {
		SqlStatement compiled = compile(query);
		try (PreparedStatement statement = prepareStatement(compiled.sql())) {
			compiled.bind(statement);
			try (ResultSet result = statement.executeQuery()) {
				while (result.next())
					consumer.accept(result);
			}
		}
	}

	default int update(Sql.Query query) throws SQLException {
		SqlStatement compiled = compile(query);
		try (PreparedStatement statement = prepareStatement(compiled.sql())) {
			compiled.bind(statement);
			return statement.executeUpdate();
		}
	}

	default boolean exists(Sql.Select query) throws SQLException {
		SqlStatement compiled = compile(query);
		try (PreparedStatement statement = prepareStatement(compiled.sql())) {
			compiled.bind(statement);
			statement.setMaxRows(1);
			try (ResultSet result = statement.executeQuery()) {
				return result.next();
			}
		}
	}

	default <T> T transaction(Transaction<T> work) throws SQLException {
		throw new java.sql.SQLFeatureNotSupportedException("Transactions are not implemented by this handler");
	}

	default List<Object> insertKeys(Sql.Insert insert) throws SQLException {
		throw new java.sql.SQLFeatureNotSupportedException("Generated keys are not implemented by this handler");
	}

	default int[] batch(String sql, List<Object[]> rows) throws SQLException {
		try (PreparedStatement statement = prepareStatement(sql)) {
			for (Object[] row : rows) {
				statement.clearParameters();
				for (int i = 0; i < row.length; i++)
					statement.setObject(i + 1, row[i]);
				statement.addBatch();
			}
			return statement.executeBatch();
		}
	}

	class Row {
		private final String field;
		private final String type;
		private final boolean nulled;
		private final String key;
		private final String defaultVal;
		private final String extra;

		public Row(String fieldName, String fieldType, boolean nulled, String key, String defVal, String extra) {
			field = fieldName;
			type = fieldType;
			this.nulled = nulled;
			this.key = key;
			defaultVal = defVal;
			this.extra = extra;
		}

		public Row(String fieldName, String fieldType, boolean nulled) {
			field = fieldName;
			type = fieldType;
			this.nulled = nulled;
			key = "";
			defaultVal = "";
			extra = "";
		}

		public Row(String fieldName, String fieldType) {
			field = fieldName;
			type = fieldType;
			nulled = false;
			key = "";
			defaultVal = "";
			extra = "";
		}

		public Row(String fieldName, SqlFieldType fieldType, int size, boolean nulled, String key, String defVal,
				String extra) {
			field = fieldName;
			type = fieldType.name() + (size == 0 ? "" : "(" + size + ")");
			this.nulled = nulled;
			this.key = key;
			defaultVal = defVal;
			this.extra = extra;
		}

		public Row(String fieldName, SqlFieldType fieldType, int size, boolean nulled) {
			field = fieldName;
			type = fieldType.name() + (size == 0 ? "" : "(" + size + ")");
			this.nulled = nulled;
			key = "";
			defaultVal = "";
			extra = "";
		}

		public Row(String fieldName, SqlFieldType fieldType, int size) {
			field = fieldName;
			type = fieldType.name() + (size == 0 ? "" : "(" + size + ")");
			nulled = false;
			key = "";
			defaultVal = "";
			extra = "";
		}

		public Row(String fieldName, SqlFieldType fieldType, boolean nulled, String key, String defVal, String extra) {
			field = fieldName;
			type = fieldType.name();
			this.nulled = nulled;
			this.key = key;
			defaultVal = defVal;
			this.extra = extra;
		}

		public Row(String fieldName, SqlFieldType fieldType, boolean nulled) {
			field = fieldName;
			type = fieldType.name();
			this.nulled = nulled;
			key = "";
			defaultVal = "";
			extra = "";
		}

		public Row(String fieldName, SqlFieldType fieldType) {
			field = fieldName;
			type = fieldType.name();
			nulled = false;
			key = "";
			defaultVal = "";
			extra = "";
		}

		public String getFieldName() {
			return field;
		}

		public String getFieldType() {
			return type;
		}

		public boolean isNulled() {
			return nulled;
		}

		public String getKey() {
			return key;
		}

		public String getDefaultValue() {
			return defaultVal;
		}

		public String getExtra() {
			return extra;
		}
	}

	DatabaseType getType();

	boolean isConnected() throws SQLException;

	void open() throws SQLException;

	@Override
	void close() throws SQLException;

	boolean createTable(String name, Row[] values) throws SQLException;

	boolean deleteTable(String name) throws SQLException;

	PreparedStatement prepareStatement(String sql) throws SQLException;

	boolean execute(PreparedStatement sql) throws SQLException;

	int executeUpdate(PreparedStatement sql) throws SQLException;

	int[] executeBatch(PreparedStatement sql) throws SQLException;

	long[] executeLargeBatch(PreparedStatement sql) throws SQLException;

	ResultSet executeQuery(PreparedStatement sql) throws SQLException;

	List<String> getTables() throws SQLException;

	Row[] getTableValues(String name) throws SQLException;
}
