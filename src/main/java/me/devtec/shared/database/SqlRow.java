package me.devtec.shared.database;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLXML;
import java.util.*;

/** Detached row. Index access is zero-based; duplicate labels resolve to the first column. */
public final class SqlRow {
	private final List<String> columns;
	private final Object[] values;
	SqlRow(ResultSet result) throws SQLException {
		ResultSetMetaData metadata = result.getMetaData();
		List<String> names = new ArrayList<>(); values = new Object[metadata.getColumnCount()];
		for (int i = 0; i < values.length; i++) { names.add(metadata.getColumnLabel(i + 1)); values[i] = detach(result.getObject(i + 1)); }
		columns = Collections.unmodifiableList(names);
	}
	private static Object detach(Object value) throws SQLException {
		if (value instanceof Blob) {
			Blob blob = (Blob) value;
			try { long length = blob.length(); if (length > Integer.MAX_VALUE) throw new SQLException("LOB too large for a detached row; use forEach/RowMapper streaming"); return blob.getBytes(1, (int) length); }
			finally { blob.free(); }
		}
		if (value instanceof Clob) {
			Clob clob = (Clob) value;
			try { long length = clob.length(); if (length > Integer.MAX_VALUE) throw new SQLException("LOB too large for a detached row; use forEach/RowMapper streaming"); return clob.getSubString(1, (int) length); }
			finally { clob.free(); }
		}
		if (value instanceof SQLXML) { SQLXML xml = (SQLXML) value; try { return xml.getString(); } finally { xml.free(); } }
		return value;
	}
	public List<String> columns() { return columns; }
	public Object get(int index) { return values[index]; }
	public Object get(String label) {
		for (int i = 0; i < values.length; i++) if (columns.get(i).equalsIgnoreCase(label)) return values[i];
		throw new IllegalArgumentException("Unknown result column: " + label);
	}
	public <T> T get(String label, Class<T> type) { return type.cast(get(label)); }
	public String getString(String label) { Object value = get(label); return value == null ? null : value.toString(); }
	public Map<String, Object> asMap() { Map<String,Object> map = new LinkedHashMap<>(); for (int i = 0; i < values.length; i++) if (!map.containsKey(columns.get(i))) map.put(columns.get(i), values[i]); return Collections.unmodifiableMap(map); }
}
