package me.devtec.shared.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import me.devtec.shared.database.DatabaseAPI.DatabaseType;

public interface DatabaseHandler {
	class SelectQuery {

		public enum Sorting {
			HIGHEST_TO_LOWEST, UP, LOWEST_TO_HIGHEST, DOWN
        }

		protected final String table;
		protected final String[] search;
		protected String limit;
		protected List<Object[]> where = new ArrayList<>();
		protected List<Object[]> like = new ArrayList<>();
		protected final List<List<Object[]>[]> whereOr = new ArrayList<>();
		protected byte mode;

		protected Sorting sorting;
		protected final List<String> sortingKey = new ArrayList<>();

		private SelectQuery(String table, String... value) {
			this.table = table;
			search = value == null || value.length == 0 ? new String[] { "*" } : value;
		}

		public static SelectQuery table(String table, String... search) {
			return new SelectQuery(table, search);
		}

		public SelectQuery where(String key, String value) {
			where.add(new Object[] { key, value });
			return this;
		}

		public SelectQuery where(String key, SelectQuery value) {
			where.add(new Object[] { key, value });
			return this;
		}

		public SelectQuery like(String key, String value) {
			like.add(new Object[] { key, value });
			return this;
		}

		public SelectQuery like(String key, SelectQuery value) {
			like.add(new Object[] { key, value });
			return this;
		}

		@SuppressWarnings("unchecked")
		public SelectQuery or() {
			if (!where.isEmpty()) {
				mode = 1;
				whereOr.add(new List[] { where, like });
				where = new ArrayList<>();
				like = new ArrayList<>();
			}
			return this;
		}

		public SelectQuery sortType(Sorting type) {
			sorting = type;
			return this;
		}

		public SelectQuery sortBy(String key) {
			sortingKey.add(key);
			return this;
		}

		public SelectQuery limit(int limit) {
			this.limit = limit == 0 ? null : "" + limit;
			return this;
		}

		public SelectQuery limit(int limitFrom, int limitTo) {
			limit = limitFrom + "," + limitTo;
			return this;
		}

		public String getTable() {
			return table;
		}

		public String[] getSearch() {
			return search;
		}
	}

	class InsertQuery {

		protected final String table;
		protected final List<String> values = new ArrayList<>();

		private InsertQuery(String table) {
			this.table = table;
		}

		public static InsertQuery table(String table, String... values) {
			InsertQuery query = new InsertQuery(table);
			Collections.addAll(query.values, values);
			return query;
		}

		public String getTable() {
			return table;
		}
	}

	class UpdateQuery {

		protected final String table;
		protected List<Object[]> where = new ArrayList<>();
		protected List<Object[]> like = new ArrayList<>();
		protected final List<List<Object[]>[]> whereOr = new ArrayList<>();
		protected byte mode;
		protected final List<String[]> values = new ArrayList<>();
		protected String limit = "1";

		protected enum Action {
			WHERE, VALUE
		}

		private UpdateQuery(String table) {
			this.table = table;
		}

		public static UpdateQuery table(String table) {
			return new UpdateQuery(table);
		}

		public UpdateQuery where(String key, String value) {
			where.add(new Object[] { key, value });
			return this;
		}

		public UpdateQuery where(String key, SelectQuery value) {
			where.add(new Object[] { key, value });
			return this;
		}

		public UpdateQuery like(String key, String value) {
			like.add(new Object[] { key, value });
			return this;
		}

		public UpdateQuery like(String key, SelectQuery value) {
			like.add(new Object[] { key, value });
			return this;
		}

		@SuppressWarnings("unchecked")
		public UpdateQuery or() {
			if (!where.isEmpty()) {
				mode = 1;
				whereOr.add(new List[] { where, like });
				where = new ArrayList<>();
				like = new ArrayList<>();
			}
			return this;
		}

		public UpdateQuery value(String key, String value) {
			values.add(new String[] { key, value });
			return this;
		}

		public UpdateQuery limit(int limit) {
			this.limit = limit == 0 ? null : "" + limit;
			return this;
		}

		public UpdateQuery limit(int limitFrom, int limitTo) {
			limit = limitFrom + "," + limitTo;
			return this;
		}

		public String getTable() {
			return table;
		}
	}

	class RemoveQuery {

		protected final String table;
		protected List<Object[]> where = new ArrayList<>();
		protected List<Object[]> like = new ArrayList<>();
		protected final List<List<Object[]>[]> whereOr = new ArrayList<>();
		protected byte mode;
		protected String limit = "1";

		private RemoveQuery(String table) {
			this.table = table;
		}

		public static RemoveQuery table(String table) {
			return new RemoveQuery(table);
		}

		public RemoveQuery where(String key, String value) {
			where.add(new Object[] { key, value });
			return this;
		}

		public RemoveQuery where(String key, SelectQuery value) {
			where.add(new Object[] { key, value });
			return this;
		}

		public RemoveQuery like(String key, String value) {
			like.add(new Object[] { key, value });
			return this;
		}

		public RemoveQuery like(String key, SelectQuery value) {
			like.add(new Object[] { key, value });
			return this;
		}

		@SuppressWarnings("unchecked")
		public RemoveQuery or() {
			if (!where.isEmpty()) {
				mode = 1;
				whereOr.add(new List[] { where, like });
				where = new ArrayList<>();
				like = new ArrayList<>();
			}
			return this;
		}

		public RemoveQuery limit(int limit) {
			this.limit = limit == 0 ? null : "" + limit;
			return this;
		}

		public RemoveQuery limit(int limitFrom, int limitTo) {
			limit = limitFrom + "," + limitTo;
			return this;
		}

		public String getTable() {
			return table;
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

		public Row(String fieldName, SqlFieldType fieldType, int size, boolean nulled, String key, String defVal, String extra) {
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

	class Result implements Iterator<Result> {
		private Result next;
		private final String[] values;

		protected Result(String[] value) {
			values = value;
		}

		protected void nextResult(Result next) {
			if (next != null) {
				this.next = next;
			}
		}

		@Override
		public Result next() {
			return next;
		}

		@Override
		public boolean hasNext() {
			return next != null;
		}

		public String[] getValue() {
			return values;
		}
	}

	DatabaseType getType();

	boolean isConnected() throws SQLException;

	void open() throws SQLException;

	void close() throws SQLException;

	boolean exists(SelectQuery query) throws SQLException;

	boolean createTable(String name, Row[] values) throws SQLException;

	boolean deleteTable(String name) throws SQLException;

	default Result select(SelectQuery query) throws SQLException {
		return get(query);
	}

	Result get(SelectQuery query) throws SQLException;

	boolean insert(InsertQuery query) throws SQLException;

	default boolean set(UpdateQuery query) throws SQLException {
		return update(query);
	}

	boolean update(UpdateQuery query) throws SQLException;

	boolean remove(RemoveQuery query) throws SQLException;

	PreparedStatement prepareStatement(String sql) throws SQLException;

	boolean execute(PreparedStatement sql) throws SQLException;

	int executeUpdate(PreparedStatement sql) throws SQLException;

	int[] executeBatch(PreparedStatement sql) throws SQLException;

	long[] executeLargeBatch(PreparedStatement sql) throws SQLException;

	ResultSet executeQuery(PreparedStatement sql) throws SQLException;

	List<String> getTables() throws SQLException;

	Row[] getTableValues(String name) throws SQLException;
}
