# DatabaseAPI

Nové API odděluje sestavení dotazu (`Sql`), výsledné SQL a parametry (`SqlStatement`)
a práci se spojením (`DatabaseHandler` / `SqlHandler`). Kompiluje pro MySQL, MariaDB,
SQL Server, SQLite a H2. Produkční kód zůstává kompatibilní s Java 8.

## Připojení

```java
import me.devtec.shared.database.*;
import me.devtec.shared.database.DatabaseAPI.*;
import static me.devtec.shared.database.Sql.*;

try (DatabaseHandler db = DatabaseAPI.openConnection(
        DatabaseType.MYSQL,
        new SqlDatabaseSettings(DatabaseType.MYSQL, "localhost", 3306,
                "app", "user", "password"))) {
    // Dotazy používají jedno spojení.
}
```

Alternativy:

```java
DatabaseAPI.openConnection(DatabaseType.SQLITE,
    new SqliteDatabaseSettings(DatabaseType.SQLITE, "app.db", null, null));
DatabaseAPI.openConnection(DatabaseType.H2,
    DatabaseAPI.jdbc("jdbc:h2:mem:app", "sa", ""));
DatabaseAPI.openConnection(DatabaseType.SQLSERVER,
    DatabaseAPI.jdbc("jdbc:sqlserver://localhost:1433;databaseName=app;encrypt=true",
        "user", "password"));
DatabaseAPI.openConnection(DatabaseType.MARIADB, dataSource);
```

Handler vlastní spojení získané z `DataSource`; `close()` ho vrací poolu.
Samotný pool nevytváří. Operace jednoho `SqlHandler` jsou synchronizované,
včetně celého callbacku transakce. Pro paralelní práci používej samostatná spojení
z poolu. Builders jsou mutable, patří jedné operaci a nesdílejí se mezi vlákny.

JDBC driver musí být dostupný aplikaci. Pokud chybí a je inicializované `API.library`,
zůstává zachované původní načtení driveru z knihoven TheAPI. Testovací JDBC závislosti
v POM nejsou součástí výsledného JARu. `jdbc(...).attributes(...)` připojuje doslovný
suffix; oddělovače odpovídají JDBC URL konkrétního driveru.

## SELECT uvnitř SELECTu, JOIN a parametry

```java
Sql.Select spent = select(sum(column("o.total")))
    .from(table("orders").as("o"))
    .where(column("o.user_id").eq(column("u.id")));

Sql.Select query = select(column("u.id"), column("u.name"), spent.as("spent"))
    .from(table("users").as("u"))
    .leftJoin(table("profiles").as("p"),
        column("p.user_id").eq(column("u.id")))
    .where(column("u.active").eq(true)
        .and(column("p.country").eq("CZ").or(column("p.country").isNull())))
    .orderBy(column("u.id").asc())
    .limit(20).offset(40);

List<SqlRow> rows = db.query(query);
for (SqlRow row : rows) {
    String name = row.getString("name");
    Number total = (Number) row.get("spent"); // SQL NULL zůstává null.
}
```

Hodnoty se vážou přes `PreparedStatement`. `column("u.name")` je identifikátor,
`value("u.name")` je hodnota. `select(String...)` přijímá názvy sloupců, nikoli
SQL výrazy. Poddotaz jako skalární hodnota musí podle pravidel SQL vrátit nejvýše
jeden řádek a jeden sloupec; více řádků patří do `IN` nebo `EXISTS`.

```java
Sql.Select ids = select("user_id").from("orders").where(column("total").gt(100));
db.query(select("name").from("users").where(column("id").in(ids)));
db.query(select("x.name").from(select("name").from("users"), "x"));

db.query(select(column("user_id"), sum(column("total")).as("total"))
    .from("orders").groupBy("user_id").having(sum(column("total")).gt(100)));

db.query(select("recent.name")
    .with("recent", select("name").from("users").where(column("active").eq(true)))
    .from("recent"));

db.query(select(column("name"),
    over(function("ROW_NUMBER")).orderBy(column("id").asc()).as("position"))
    .from("users"));
```

Další výrazy: `distinct`, `union`, `unionAll`, `count`, `sum`, `min`, `max`, `avg`,
`coalesce`, `cast`, `when(...).when(...).otherwise(...)`, `between`, `like`,
`notLike`, `notIn`, `notExists`, `and`, `or`, `not`, aritmetika `plus/minus/multiply`.
Prázdné `IN` vrací false, prázdné `NOT IN` true. `eq(null)` / `ne(null)` generují
`IS NULL` / `IS NOT NULL`. Opakované `where` a `having` se spojují přes AND.

`UNION` má společné vnější řazení a stránkování. Operand s vlastním LIMIT/ORDER BY
obal do odvozené tabulky. H2 může u parametrů bez kontextu vyžadovat explicitní typ:
`select(cast(value(1), "INTEGER")).unionAll(select(cast(value(2), "INTEGER")))`.
Typ pro CAST je SQL typ konkrétního dialektu; například MySQL používá také `SIGNED`.

## Zápis a transakce

```java
int inserted = db.update(insertInto("users", "name").values("Alice").values("Bob"));
List<Object> keys = db.insertKeys(insertInto("users", "name").values("Eve"));
db.update(insertInto("archive", "name").select(select("name").from("users")));
db.update(Sql.update("orders").set("total", column("total").plus(10)).where("id", 1));
db.update(deleteFrom("orders").where(column("total").lt(0)));

db.transaction(tx -> {
    tx.update(Sql.update("accounts").set("balance", column("balance").minus(100))
        .where("id", 1));
    tx.update(Sql.update("accounts").set("balance", column("balance").plus(100))
        .where("id", 2));
    return null;
});
```

Nové UPDATE/DELETE nemají implicitní limit. Bez WHERE zasáhnou všechny řádky,
stejně jako SQL. Počet změněných řádků a seznam generovaných klíčů odpovídají
JDBC driveru; u vícenásobného INSERTu nemusí driver vrátit všechny klíče.

Vnořená transakce používá savepoint. Pokud spojení už má vypnutý auto-commit,
handler necommitne cizí transakci a používá savepoint i pro první callback.
SQL/runtime výjimka nebo Error vyvolá rollback; chyba se předá volajícímu.
DDL může podle databáze implicitně commitnout, proto nelze slibovat rollback DDL
na všech backendech. `batch(sql, rows)` používá JDBC batch; atomicitu zajistí
obalení do `transaction` a transakční storage engine.

## Výsledky a vlastní SQL

`query` vrací seznam řádků. Indexy `SqlRow.get(int)` jsou od nuly; názvy sloupců
jsou labely/aliasy a lookup ignoruje velikost písmen. Duplicitní label vrací první
sloupec; všechny sloupce jsou dostupné indexem. `get(label, Class)` kontroluje typ,
neprovádí převod čísla. Použij například `((Number) row.get("count")).longValue()`.
BLOB/CLOB/SQLXML se zkopírují do byte[]/String před zavřením výsledku. Další
vendorové JDBC objekty mohou mít vlastní životní cyklus.

Pro vlastní mapování nebo velké výsledky:

```java
List<String> names = db.query(select("name").from("users"), rs -> rs.getString(1));
db.forEach(select().from("orders"), rs -> consume(rs));
```

`forEach` neakumuluje seznam v knihovně; skutečné síťové streamování závisí na
driveru a jeho nastavení fetch size. ResultSet platí pouze uvnitř callbacku.

```java
db.update(statement("CREATE INDEX users_name ON users(name)"));
db.query(statement("SELECT name FROM users WHERE id = ?", 42));
SqlStatement compiled = db.compile(query);
try (PreparedStatement statement = db.prepareStatement(compiled.sql())) {
    compiled.bind(statement);
    // Zde lze nastavit fetchSize/queryTimeout a převzít správu ResultSetu.
}
```

`raw` a `statement` přijímají důvěryhodný SQL text; externí hodnoty vždy předej
jako parametry. JDBC driver kontroluje počet placeholderů. Tudy jsou dostupné
také vendorové příkazy, indexy, foreign keys, upsert, rekurzivní CTE, window frames
a další SQL mimo vestavěný builder. Knihovna nepřevádí libovolné MySQL SQL na jiný
databázový engine. Ruční JDBC operace vyžadují vlastní uzavření prostředků a
koordinaci přístupu ke spojení.

## Rozdíly databází

| Oblast | Chování |
| --- | --- |
| MySQL / MariaDB | Backticky, LIMIT/OFFSET, AUTO_INCREMENT; FULL JOIN není podporován. |
| SQL Server | Hranaté závorky, TOP nebo OFFSET/FETCH; stránkování vyžaduje ORDER BY. |
| H2 | Dvojité uvozovky, OFFSET/FETCH, GENERATED BY DEFAULT AS IDENTITY. |
| SQLite | Dvojité uvozovky, LIMIT/OFFSET; omezené UPDATE/DELETE vybírají identity poddotazem. |
| RIGHT / FULL JOIN na SQLite | Builder je odmítá kvůli kompatibilitě se staršími SQLite drivery. Novější syntax je dostupná přes statement. |
| CTE / window funkce | Vyžadují odpovídající verzi serveru: MySQL 8+, MariaDB 10.2+, moderní H2/SQLite/SQL Server. |
| CTE na SQL Serveru | Deklaruj na vnějším SELECTu; zanořené WITH builder odmítne. |
| LIMIT 0 | Nové SELECT vrací nula řádků; SQL Server nepovoluje nulové FETCH při stránkování/UNION. |
| UPDATE/DELETE offset | Vestavěný offset podporuje SQLite s LIMIT; jinde použij výběr klíčů poddotazem. |

SQLite handler při omezené změně zjistí rowid nebo primární klíč. Pro kompilaci
bez spojení použij `keyColumns("id")`; klíč musí být unikátní a nenulový.
Podporované jsou také složené klíče a WITHOUT ROWID. Výběr bez ORDER BY není
deterministický; pro konkrétní řádky použij explicitní poddotaz s řazením.

`createTable(name, Row[])` podporuje typy, nullability, jednoduché i složené
primární klíče, UNIQUE, základní defaulty a identity. Vendorové typy a extras
patří do explicitního DDL. `getTableValues` čte JDBC metadata, není to export DDL
se všemi délkami, indexy a constraints. Citované identifikátory zachovávají case;
u existujících H2 tabulek používej jejich skutečný název z metadat.

Reference k dialektům: [H2 commands](https://h2database.github.io/html/commands.html),
[SQLite UPDATE](https://www.sqlite.org/lang_update.html),
[SQLite rowid](https://www.sqlite.org/rowidtable.html),
[SQL Server ORDER BY](https://learn.microsoft.com/en-us/sql/t-sql/queries/select-order-by-clause-transact-sql).

## Ověření

`mvn -Dtest=DatabaseApiTest test` ověřuje všech pět dialektů při kompilaci dotazů
a skutečné operace na H2 2.2.224 a SQLite 3.45.3.0: poddotazy, joiny, CTE, window
funkce, UNION, binding, NULL, DDL/metadata, CRUD, složené klíče, souborové znovuotevření,
DataSource, generované klíče, batch rollback, savepointy a LOB.
Test selhání rollbacku kontroluje, že se nezapne auto-commit.

MySQL, MariaDB a SQL Server nebyly integračně spuštěny: lokálně nebyly dostupné
jejich servery. Kontrola SQL dialektu není náhradou integračního testu konkrétního
serveru, verze a JDBC driveru.
