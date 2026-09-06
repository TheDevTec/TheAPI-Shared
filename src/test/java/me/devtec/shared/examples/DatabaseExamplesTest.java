package me.devtec.shared.examples;

import static me.devtec.shared.database.Sql.*;
import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import me.devtec.shared.database.*;
import me.devtec.shared.database.DatabaseAPI.DatabaseType;
import me.devtec.shared.database.DatabaseHandler.Row;

/** Copyable examples, executed against both embedded database engines. */
@RunWith(Parameterized.class)
public class DatabaseExamplesTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> engines() {
        return Arrays.asList(new Object[]{DatabaseType.H2}, new Object[]{DatabaseType.SQLITE});
    }

    private final DatabaseType type;
    private DatabaseHandler db;

    public DatabaseExamplesTest(DatabaseType type) { this.type = type; }

    @Before public void createDatabase() throws SQLException {
        String url = type == DatabaseType.H2 ? "jdbc:h2:mem:examples" : "jdbc:sqlite::memory:";
        db = DatabaseAPI.openConnection(type, DatabaseAPI.jdbc(url, "sa", ""));
        db.createTable("users", new Row[]{new Row("id", "INTEGER", false, "PRI", "", ""), new Row("name", "VARCHAR(80)")});
        db.createTable("orders", new Row[]{new Row("user_id", "INTEGER"), new Row("total", "INTEGER")});
        db.update(insertInto("users", "id", "name").values(1, "Alice").values(2, "Bob"));
        db.update(insertInto("orders", "user_id", "total").values(1, 20).values(1, 30).values(2, 5));
    }

    @After public void closeDatabase() throws SQLException { if (db != null) db.close(); }

    @Test public void selectWithCorrelatedSubquery() throws SQLException {
        // Each user gets the sum of their own orders as an additional column.
        Sql.Select spent = select(sum(column("o.total")))
            .from(table("orders").as("o"))
            .where(column("o.user_id").eq(column("u.id")));
        List<SqlRow> rows = db.query(select(column("u.name"), spent.as("spent"))
            .from(table("users").as("u")).orderBy(column("u.id").asc()));
        assertEquals("Alice", rows.get(0).getString("name"));
        assertEquals(50, ((Number) rows.get(0).get("spent")).intValue());
    }

    @Test public void joinGroupingAndHaving() throws SQLException {
        List<SqlRow> rows = db.query(select(column("u.name"), sum(column("o.total")).as("spent"))
            .from(table("users").as("u"))
            .join(table("orders").as("o"), column("o.user_id").eq(column("u.id")))
            .groupBy("u.name").having(sum(column("o.total")).gt(10)));
        assertEquals(1, rows.size());
        assertEquals("Alice", rows.get(0).getString("name"));
    }

    @Test public void filteringPagingAndCustomMapping() throws SQLException {
        Sql.Select buyers = select("user_id").from("orders").where(column("total").gt(10));
        List<String> names = db.query(select("name").from("users")
            .where(column("id").in(buyers)).orderBy(column("id").asc()).limit(1),
            result -> result.getString("name"));
        assertEquals(Arrays.asList("Alice"), names);
    }

    @Test public void insertUpdateAndDelete() throws SQLException {
        assertEquals(1, db.update(insertInto("users", "id", "name").values(3, "Eve")));
        assertEquals(1, db.update(Sql.update("users").set("name", "Eva").where("id", 3)));
        assertTrue(db.exists(select().from("users").where("name", "Eva")));
        assertEquals(1, db.update(deleteFrom("users").where("id", 3)));
    }

    @Test public void transactionRollsBackAllChanges() throws SQLException {
        try {
            db.transaction(tx -> {
                tx.update(Sql.update("orders").set("total", 0).where("user_id", 1));
                throw new SQLException("Cancel the operation");
            });
            fail("The exception must propagate");
        } catch (SQLException expected) {
            assertEquals("Cancel the operation", expected.getMessage());
        }
        assertEquals(50, ((Number) db.query(select(sum(column("total"))).from("orders")
            .where("user_id", 1)).get(0).get(0)).intValue());
    }

    @Test public void commonTableAndWindowFunction() throws SQLException {
        List<SqlRow> rows = db.query(select(column("name"),
            over(function("ROW_NUMBER")).orderBy(column("id").asc()).as("position"))
            .with("buyers", select("id", "name").from("users"))
            .from("buyers").orderBy(column("id").asc()));
        assertEquals(2, ((Number) rows.get(1).get("position")).intValue());
    }
}
