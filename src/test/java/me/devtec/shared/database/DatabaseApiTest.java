package me.devtec.shared.database;

import org.junit.Test;
import static org.junit.Assert.*;
import static me.devtec.shared.database.Sql.*;
import me.devtec.shared.database.DatabaseAPI.*;
import me.devtec.shared.database.DatabaseHandler.*;
import java.sql.*;
import java.util.*;

public class DatabaseApiTest {
	@org.junit.Rule public org.junit.rules.TemporaryFolder files = new org.junit.rules.TemporaryFolder();
	@Test public void fileDatabasesPersistAcrossReopen() throws Exception {
		for (DatabaseType type : new DatabaseType[]{DatabaseType.H2, DatabaseType.SQLITE}) {
			String path = new java.io.File(files.newFolder(type.name()), "database with spaces").getAbsolutePath();
			DatabaseSettings settings = new SqliteDatabaseSettings(type, path, type == DatabaseType.H2 ? "sa" : null, "");
			try (DatabaseHandler database = DatabaseAPI.openConnection(type, settings)) {
				database.createTable("saved", new Row[]{new Row("value", "INTEGER")});
				database.update(insertInto("saved", "value").values(42));
			}
			try (DatabaseHandler database = DatabaseAPI.openConnection(type, settings)) { assertEquals(42, ((Number) database.query(select("value").from("saved")).get(0).get(0)).intValue()); }
		}
	}
	@Test public void dataSourceConnection() throws Exception {
		org.h2.jdbcx.JdbcDataSource source = new org.h2.jdbcx.JdbcDataSource(); source.setURL("jdbc:h2:mem:source");
		try (DatabaseHandler database = DatabaseAPI.openConnection(DatabaseType.H2, source)) { assertTrue(database.isConnected()); assertEquals(1, database.query(select(value(1))).size()); }
	}
	@Test public void dialectsAndParameterOrder() {
		for (DatabaseType type : DatabaseType.values()) {
			Sql.Select count = select(count()).from(table("orders").as("o")).where(column("o.user_id").eq(column("u.id"))).where(column("o.total").gt(10));
			Sql.Select query = select(column("u.name"), count.as("orders"), value("literal").as("bound"))
					.from(table("users").as("u")).leftJoin(table("profiles").as("p"), column("p.user_id").eq(column("u.id")))
					.where(column("u.name").eq("Robert'); DROP TABLE users;--").or(column("u.name").isNull()))
					.orderBy(column("u.id").asc()).limit(5).offset(2);
			SqlStatement compiled = compile(query, type);
			assertEquals(Arrays.asList(10, "literal", "Robert'); DROP TABLE users;--"), compiled.parameters());
			assertFalse(compiled.sql().contains("DROP TABLE"));
			assertTrue(compiled.sql().contains("LEFT JOIN"));
			assertTrue(compiled.sql().contains("SELECT COUNT(*)"));
			assertEquals(3, compiled.sql().chars().filter(c -> c == '?').count());
			if (type == DatabaseType.SQLSERVER) assertTrue(compiled.sql().contains("OFFSET 2 ROWS FETCH NEXT 5 ROWS ONLY"));
			else if (type == DatabaseType.H2) assertTrue(compiled.sql().contains("FETCH FIRST 5 ROWS ONLY"));
			else assertTrue(compiled.sql().endsWith("LIMIT 5 OFFSET 2"));
		}
	}
	@Test public void quotingNullAndEmptyIn() {
		assertEquals("SELECT `a``b`", compile(select(column("a`b")), DatabaseType.MYSQL).sql());
		assertEquals("SELECT [a]]b]", compile(select(column("a]b")), DatabaseType.SQLSERVER).sql());
		assertTrue(compile(select().from("t").where(column("a").in(Collections.emptyList())), DatabaseType.H2).sql().endsWith("WHERE 1=0"));
		assertTrue(compile(select().from("t").where(column("a").ne(null)), DatabaseType.SQLITE).parameters().isEmpty());
	}
	@Test public void cyclesAndUnsupportedOperationsFailBeforeExecution() {
		Sql.Select cycle = select("a"); cycle.where(exists(cycle));
		try { compile(cycle, DatabaseType.H2); fail(); } catch (IllegalArgumentException expected) {}
		Sql.Case cyclicCase = when(value(1).eq(1), "yes"); cyclicCase.otherwise(cyclicCase);
		try { compile(select(cyclicCase), DatabaseType.H2); fail(); } catch (IllegalArgumentException expected) {}
		try { compile(select().from("a").fullJoin(table("b"), column("a.id").eq(column("b.id"))), DatabaseType.MYSQL); fail(); } catch (UnsupportedOperationException expected) {}
		try { compile(select().from("a").limit(1).offset(2), DatabaseType.SQLSERVER); fail(); } catch (IllegalArgumentException expected) {}
	}
	@Test public void connectionStrings() {
		assertEquals("jdbc:sqlite::memory:", new SqliteDatabaseSettings(DatabaseType.SQLITE, ":memory:", null, null).getConnectionString());
		assertEquals("jdbc:h2:mem:test", new SqliteDatabaseSettings(DatabaseType.H2, "mem:test", "sa", "").getConnectionString());
		String server = new SqlDatabaseSettings(DatabaseType.SQLSERVER, "localhost", 1433, "demo", "user", "secret").getConnectionString();
		assertFalse(server.contains("secret")); assertFalse(server.contains("integratedSecurity"));
	}
	@Test public void mutationDialects() {
		for (DatabaseType type : DatabaseType.values()) {
			SqlStatement update = compile(Sql.update("t").set("a", column("a").plus(2)).where("b", "bound").limit(3).keyColumns("id"), type);
			assertEquals(Arrays.asList(2, "bound"), update.parameters());
			String deletion = compile(deleteFrom("t").where("b", 1).limit(3).keyColumns("id"), type).sql();
			if (type == DatabaseType.SQLSERVER) { assertTrue(update.sql().startsWith("UPDATE TOP (3)")); assertTrue(deletion.startsWith("DELETE TOP (3)")); }
			else if (type == DatabaseType.H2) assertTrue(deletion.endsWith("FETCH FIRST 3 ROWS ONLY"));
			else if (type == DatabaseType.SQLITE) assertTrue(deletion.contains("IN (SELECT"));
			else assertTrue(deletion.endsWith("LIMIT 3"));
			assertEquals(Arrays.asList(1, "a", 2, "b"), compile(insertInto("t", "a", "b").values(1, "a").values(2, "b"), type).parameters());
		}
	}
	@Test public void h2Integration() throws Exception { integration(DatabaseType.H2, "jdbc:h2:mem:database_api;DB_CLOSE_DELAY=-1"); }
	@Test public void sqliteIntegration() throws Exception { integration(DatabaseType.SQLITE, "jdbc:sqlite::memory:"); }
	@Test public void rollbackFailureNeverCommitsPartialWork() throws Exception {
		boolean[] state = {true, false};
		Connection connection = (Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
			switch (method.getName()) {
			case "isClosed": return state[1];
			case "getAutoCommit": return state[0];
			case "setAutoCommit": if ((Boolean) args[0]) fail("Must not enable auto-commit after failed rollback"); state[0] = false; return null;
			case "rollback": throw new SQLException("rollback failed");
			case "close": state[1] = true; return null;
			default: throw new AssertionError(method.getName());
			}
		});
		try (DatabaseHandler database = new SqlHandler(connection, DatabaseType.H2)) {
			try { database.transaction(db -> { throw new SQLException("original"); }); fail(); }
			catch (SQLException expected) { assertEquals("original", expected.getMessage()); assertEquals("rollback failed", expected.getSuppressed()[0].getMessage()); }
			assertTrue(state[1]);
		}
	}
	@Test public void externalTransactionAndDetachedLobs() throws Exception {
		Connection connection = DriverManager.getConnection("jdbc:h2:mem:external", "sa", "");
		SqlRow row;
		try (DatabaseHandler database = new SqlHandler(connection, DatabaseType.H2)) {
			database.update(statement("CREATE TABLE t (v INTEGER, b BLOB, c CLOB)"));
			connection.setAutoCommit(false);
			database.transaction(db -> db.update(statement("INSERT INTO t VALUES (?, ?, ?)", 1, new byte[]{1, 2}, "text")));
			assertFalse(connection.getAutoCommit());
			row = database.query(statement("SELECT b,c FROM t")).get(0);
			connection.rollback();
			assertFalse(database.exists(select().from("T")));
		}
		assertArrayEquals(new byte[]{1, 2}, (byte[]) row.get("b")); assertEquals("text", row.get("c"));
	}
	private void integration(DatabaseType type, String url) throws Exception {
		try (DatabaseHandler database = DatabaseAPI.openConnection(type, DatabaseAPI.jdbc(url, type == DatabaseType.H2 ? "sa" : null, ""))) {
			assertEquals(type, database.getType());
			assertTrue(database.createTable("users", new Row[]{new Row("id", "INTEGER", false, "PRI", "", "AUTO_INCREMENT"), new Row("name", "VARCHAR(80)", true, "", "", "")}));
			assertTrue(database.createTable("orders", new Row[]{new Row("user_id", "INTEGER"), new Row("total", "INTEGER")}));
			assertTrue(database.getTables().contains("users")); assertEquals(2, database.getTableValues("users").length);
			assertEquals("id", database.getTableValues("users")[0].getFieldName());
			assertEquals(2, database.update(insertInto("users", "name").values("Alice").values("Bob")));
			assertEquals(3, database.update(insertInto("orders", "user_id", "total").values(1, 20).values(1, 30).values(2, 5)));
			Sql.Select total = select(sum(column("o.total"))).from(table("orders").as("o")).where(column("o.user_id").eq(column("u.id")));
			List<SqlRow> rows = database.query(select(column("u.name"), total.as("spent")).from(table("users").as("u")).orderBy(column("u.id").asc()));
			assertEquals(2, rows.size()); assertEquals("Alice", rows.get(0).getString("name")); assertEquals(50, ((Number) rows.get(0).get("spent")).intValue());
			assertEquals(1, database.query(select(column("u.name"), sum(column("o.total")).as("total")).from(table("users").as("u"))
					.join(table("orders").as("o"), column("o.user_id").eq(column("u.id"))).groupBy("u.name").having(sum(column("o.total")).gt(10))).size());
			Sql.Select qualifying = select("user_id").from("orders").where(column("total").gt(10));
			assertEquals(1, database.query(select("name").from("users").where(column("id").in(qualifying))).size());
			assertEquals(2, database.query(select("x.name").from(select("name").from("users"), "x")).size());
			assertEquals(2, database.query(select("named.name").with("named", select("name").from("users")).from("named")).size());
			assertEquals(2, database.query(select(column("name"), over(function("ROW_NUMBER")).orderBy(column("id").asc()).as("position")).from("users")).size());
			assertEquals(2, database.query(select(cast(value(1), "INTEGER").as("n")).unionAll(select(cast(value(2), "INTEGER").as("n")))).size());
			assertEquals("yes", database.query(select(when(value(1).eq(1), "yes").otherwise("no").as("answer"))).get(0).getString("answer"));
			assertEquals(1, database.update(Sql.update("users").set("name", "changed").limit(1)));
			assertEquals(1, database.update(deleteFrom("orders").limit(1)));
			assertEquals(1, database.query(select("id").from("users").orderBy(column("id").asc()).limit(1).offset(1)).size());
			try { database.transaction(db -> { db.update(insertInto("users", "name").values("rollback")); throw new SQLException("rollback"); }); fail(); } catch (SQLException expected) { assertEquals("rollback", expected.getMessage()); }
			assertFalse(database.exists(select().from("users").where("name", "rollback")));
			database.transaction(db -> {
				db.update(insertInto("users", "name").values("outer"));
				try { db.transaction(inner -> { inner.update(insertInto("users", "name").values("inner")); throw new SQLException("nested"); }); } catch (SQLException expected) {}
				return null;
			});
			assertTrue(database.exists(select().from("users").where("name", "outer"))); assertFalse(database.exists(select().from("users").where("name", "inner")));
			assertEquals(1, database.update(insertInto("orders", "user_id", "total").values(2, 100)));
			assertTrue(database.exists(select().from("users").where(column("id").eq(select("user_id").from("orders").where("total", 100)))));
			assertTrue(database.exists(select().from("users").where(column("name").like("outer").or(column("name").like("missing")))));
			assertEquals(3, ((Number) database.query(select(count().as("total")).from("users")).get(0).get("total")).intValue());
			assertEquals(1, database.update(Sql.update("users").set("name", "renamed").where("name", "outer")));
			assertEquals(1, database.update(deleteFrom("users").where("name", "renamed")));
			assertEquals(1, database.insertKeys(insertInto("users", "name").values("key")).size());
			assertTrue(database.createTable("copy_users", new Row[]{new Row("name", "VARCHAR(80)", true, "", "", "")}));
			assertEquals(3, database.update(insertInto("copy_users", "name").select(select("name").from("users"))));
			assertEquals(1, database.update(Sql.update("copy_users").set("name", select("name").from("users").where("name", "key")).where("name", "Bob")));
			String hostile = "O'Reilly'); DROP TABLE users; --";
			database.update(insertInto("copy_users", "name").values(hostile).values((Object) null));
			assertTrue(database.exists(select().from("copy_users").where("name", hostile)));
			assertNull(database.query(select("name").from("copy_users").where(column("name").isNull())).get(0).get("name"));
			assertEquals(3, database.getTables().size());
			assertEquals(1, database.update(Sql.update("copy_users").set("name", "limited").limit(1)));
			assertTrue(database.createTable("pair", new Row[]{new Row("a", "INTEGER", false, "PRI", "", ""), new Row("b", "INTEGER", false, "PRI", "", "")}));
			database.update(insertInto("pair", "a", "b").values(1, 1).values(1, 2));
			try { database.update(insertInto("pair", "a", "b").values(1, 1)); fail("Composite key must be enforced"); } catch (SQLException expected) {}
			assertEquals(2, database.query(select().from("pair")).size());
			try { database.transaction(db -> db.batch(compile(insertInto("pair", "a", "b").values(0, 0), type).sql(), Arrays.asList(new Object[]{2, 1}, new Object[]{1, 1}))); fail(); } catch (SQLException expected) {}
			assertEquals(2, database.query(select().from("pair")).size());
			if (type == DatabaseType.SQLITE) {
				database.update(statement("CREATE TABLE composite (a INTEGER, b INTEGER, name TEXT, PRIMARY KEY(a,b)) WITHOUT ROWID"));
				database.update(insertInto("composite", "a", "b", "name").values(1, 1, "a").values(1, 2, "b"));
				assertEquals(1, database.update(Sql.update("composite").set("name", "x").limit(1)));
				assertEquals(1, database.update(deleteFrom("composite").limit(1)));
				database.update(statement("CREATE TABLE rekey (v INTEGER)"));
				database.update(insertInto("rekey", "v").values(1).values(2));
				Sql.Delete reused = deleteFrom("rekey").limit(1);
				assertEquals(1, database.update(reused));
				database.deleteTable("rekey");
				database.update(statement("CREATE TABLE rekey (_rowid_ TEXT, id INTEGER PRIMARY KEY NOT NULL)"));
				database.update(insertInto("rekey", "_rowid_", "id").values("same", 1).values("same", 2));
				assertEquals(1, database.update(reused));
			}
			database.close(); assertFalse(database.isConnected());
			try { database.query(select().from("users")); fail("Closed handler reopened itself"); } catch (SQLException expected) {}
		}
	}
}
