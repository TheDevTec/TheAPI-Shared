package me.devtec.shared.database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compiled SQL and its parameters, in JDBC binding order. */
public final class SqlStatement {
	private final String sql;
	private final List<Object> parameters;
	SqlStatement(String sql, List<Object> parameters) {
		this.sql = sql;
		this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
	}
	public String sql() { return sql; }
	public List<Object> parameters() { return parameters; }
	public void bind(PreparedStatement statement) throws SQLException {
		for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
	}
	@Override public String toString() { return sql; }
}
