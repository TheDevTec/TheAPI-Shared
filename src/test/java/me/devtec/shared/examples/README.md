# Spustitelné příklady

Každý příklad je samostatný JUnit test s ověřením výsledku.

- `DatabaseExamplesTest`: připojení, tabulky, CRUD, korelovaný SELECT v SELECTu,
  JOIN + HAVING, IN poddotaz, stránkování, vlastní mapování, rollback, CTE a window funkce.
  Každý příklad běží na H2 i SQLite.
- `ConfigExamplesTest`: typované hodnoty, seznamy, sekce, odstranění klíče,
  YAML komentáře, JSON převod a uložení/načtení souboru.
- `ComponentExamplesTest`: click/hover události, JSON round-trip, placeholdery,
  zprávy z Configu, více řádků a rendering strukturovaného JSONu.
- `MessagingExamplesTest`: chat jednotlivci/skupině, action bar, title s časováním
  a propojení Config → TextRenderer → ComponentAPI → Messenger.

Spuštění všech příkladů:

```shell
mvn "-Dtest=*ExamplesTest" test
```

V IDE lze spustit jednotlivou testovací metodu. Není potřeba Minecraft server
ani externí databáze. Messaging používá lokální recording provider a nikomu
skutečně nic neposílá. V pluginu instaluje reálný provider platformní loader;
testovací reflexe slouží jen k obnovení globálního stavu po testu.

Config, ComponentAPI a Messaging mají zachované funkce. SQL examples používají
výhradně nové `Sql.*` API; staré query builders, Result a adaptér jsou odstraněné.
