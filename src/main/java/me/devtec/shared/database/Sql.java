package me.devtec.shared.database;

import java.util.*;

import me.devtec.shared.database.DatabaseAPI.DatabaseType;
import me.devtec.shared.dataholder.StringContainer;

/** Composable, parameterized SQL. Builders are mutable and operation-owned. */
public final class Sql {
	private Sql() {
	}

	public interface Query {
		void render(Context context);
	}

	public interface Expression {
		void render(Context context);

		default Expression as(String alias) {
			return c -> {
				render(c);
				c.add(" AS ");
				c.name(alias);
			};
		}

		default Condition eq(Object value) {
			return compare(this, "=", value);
		}

		default Condition ne(Object value) {
			return compare(this, "<>", value);
		}

		default Condition gt(Object value) {
			return compare(this, ">", value);
		}

		default Condition ge(Object value) {
			return compare(this, ">=", value);
		}

		default Condition lt(Object value) {
			return compare(this, "<", value);
		}

		default Condition le(Object value) {
			return compare(this, "<=", value);
		}

		default Condition like(Object value) {
			return compare(this, "LIKE", value);
		}

		default Condition notLike(Object value) {
			return compare(this, "NOT LIKE", value);
		}

		default Condition isNull() {
			return eq(null);
		}

		default Condition isNotNull() {
			return ne(null);
		}

		default Condition in(Object... values) {
			return membership(this, false, Arrays.asList(values));
		}

		default Condition in(Collection<?> values) {
			return membership(this, false, values);
		}

		default Condition notIn(Collection<?> values) {
			return membership(this, true, values);
		}

		default Condition in(Select select) {
			return c -> {
				render(c);
				c.add(" IN (");
				c.query(select);
				c.add(")");
			};
		}

		default Condition notIn(Select select) {
			return c -> {
				render(c);
				c.add(" NOT IN (");
				c.query(select);
				c.add(")");
			};
		}

		default Condition between(Object low, Object high) {
			return c -> {
				render(c);
				c.add(" BETWEEN ");
				expression(low).render(c);
				c.add(" AND ");
				expression(high).render(c);
			};
		}

		default Expression plus(Object value) {
			return arithmetic(this, "+", value);
		}

		default Expression minus(Object value) {
			return arithmetic(this, "-", value);
		}

		default Expression multiply(Object value) {
			return arithmetic(this, "*", value);
		}

		default Order asc() {
			return new Order(this, false);
		}

		default Order desc() {
			return new Order(this, true);
		}
	}

	public interface Condition extends Expression {
		default Condition and(Condition other) {
			return junction("AND", this, other);
		}

		default Condition or(Condition other) {
			return junction("OR", this, other);
		}

		default Condition not() {
			return c -> {
				c.add("NOT (");
				render(c);
				c.add(")");
			};
		}
	}

	public static Expression column(String name) {
		Objects.requireNonNull(name);
		return c -> c.identifier(name);
	}

	public static Expression value(Object value) {
		return c -> c.parameter(value);
	}

	/**
	 * SQL type is trusted schema input, validated structurally; supported types
	 * remain dialect-specific.
	 */
	public static Expression cast(Expression value, String type) {
		if (!type.matches("[A-Za-z][A-Za-z0-9_]*(\\([0-9]+(,\\s*[0-9]+)?\\))?( [A-Za-z]+)*"))
			throw new IllegalArgumentException("Invalid CAST type");
		return c -> {
			c.add("CAST(");
			value.render(c);
			c.add(" AS ").add(type).add(")");
		};
	}

	public static Expression scalar(Select query) {
		return c -> {
			c.add("(");
			c.query(query);
			c.add(")");
		};
	}

	private static Expression expression(Object value) {
		return value instanceof Expression ? (Expression) value
				: value instanceof Select ? scalar((Select) value) : value(value);
	}

	/**
	 * Explicit trusted SQL escape hatch. Arguments bind JDBC placeholders in order;
	 * the driver validates them.
	 */
	public static Expression raw(String sql, Object... values) {
		Objects.requireNonNull(sql);
		Object[] copy = values.clone();
		return c -> {
			c.add(sql);
			c.parameters.addAll(Arrays.asList(copy));
		};
	}

	public static Query statement(String sql, Object... values) {
		Expression expression = raw(sql, values);
		return expression::render;
	}

	public static Expression function(String name, Expression... arguments) {
		if (!name.matches("[A-Za-z_][A-Za-z0-9_]*"))
			throw new IllegalArgumentException("Invalid function name");
		Expression[] copy = arguments.clone();
		return c -> {
			c.add(name).add("(");
			c.expressions(Arrays.asList(copy));
			c.add(")");
		};
	}

	public static Expression count() {
		return function("COUNT", column("*"));
	}

	public static Expression count(Expression field) {
		return function("COUNT", field);
	}

	public static Expression sum(Expression field) {
		return function("SUM", field);
	}

	public static Expression min(Expression field) {
		return function("MIN", field);
	}

	public static Expression max(Expression field) {
		return function("MAX", field);
	}

	public static Expression avg(Expression field) {
		return function("AVG", field);
	}

	public static Expression coalesce(Expression... fields) {
		return function("COALESCE", fields);
	}

	public static Window over(Expression function) {
		return new Window(function);
	}

	/**
	 * Window functions require a server version supporting OVER (for example MySQL
	 * 8+).
	 */
	public static final class Window implements Expression {
		private final Expression function;
		private final List<Expression> partitions = new ArrayList<>();
		private final List<Order> orders = new ArrayList<>();

		Window(Expression function) {
			this.function = Objects.requireNonNull(function);
		}

		public Window partitionBy(String... columns) {
			for (String name : columns)
				partitions.add(column(name));
			return this;
		}

		public Window orderBy(Order... order) {
			orders.addAll(Arrays.asList(order));
			return this;
		}

		@Override
		public void render(Context c) {
			c.enter(this);
			try {
				function.render(c);
				c.add(" OVER (");
				if (!partitions.isEmpty()) {
					c.add("PARTITION BY ");
					c.expressions(partitions);
				}
				if (!orders.isEmpty()) {
					if (!partitions.isEmpty())
						c.add(" ");
					c.orderBy(orders);
				}
				c.add(")");
			} finally {
				c.leave(this);
			}
		}
	}

	public static Condition exists(Select query) {
		return c -> {
			c.add("EXISTS (");
			c.query(query);
			c.add(")");
		};
	}

	public static Condition notExists(Select query) {
		return exists(query).not();
	}

	public static Condition and(Condition... conditions) {
		return junction("AND", conditions);
	}

	public static Condition or(Condition... conditions) {
		return junction("OR", conditions);
	}

	private static Condition junction(String operator, Condition... conditions) {
		Condition[] copy = conditions.clone();
		return c -> {
			c.add("(");
			if (copy.length == 0)
				c.add("AND".equals(operator) ? "1=1" : "1=0");
			for (int i = 0; i < copy.length; i++) {
				if (i != 0)
					c.add(" ").add(operator).add(" ");
				Objects.requireNonNull(copy[i]).render(c);
			}
			c.add(")");
		};
	}

	private static Condition compare(Expression field, String operator, Object value) {
		if (value == null && !"=".equals(operator) && !"<>".equals(operator))
			throw new IllegalArgumentException("Use IS NULL or IS NOT NULL");
		return c -> {
			field.render(c);
			if (value == null)
				c.add("=".equals(operator) ? " IS NULL" : " IS NOT NULL");
			else {
				c.add(" ").add(operator).add(" ");
				expression(value).render(c);
			}
		};
	}

	private static Expression arithmetic(Expression field, String op, Object value) {
		return c -> {
			c.add("(");
			field.render(c);
			c.add(" ").add(op).add(" ");
			expression(value).render(c);
			c.add(")");
		};
	}

	private static Condition membership(Expression field, boolean not, Collection<?> values) {
		List<?> copy = new ArrayList<>(values);
		return c -> {
			if (copy.isEmpty()) {
				c.add(not ? "1=1" : "1=0");
				return;
			}
			field.render(c);
			c.add(not ? " NOT IN (" : " IN (");
			for (int i = 0; i < copy.size(); i++) {
				if (i != 0)
					c.add(", ");
				expression(copy.get(i)).render(c);
			}
			c.add(")");
		};
	}

	public static Case when(Condition condition, Object result) {
		return new Case().when(condition, result);
	}

	public static final class Case implements Expression {
		private final List<Condition> conditions = new ArrayList<>();
		private final List<Expression> results = new ArrayList<>();
		private Expression otherwise;

		public Case when(Condition condition, Object result) {
			conditions.add(condition);
			results.add(expression(result));
			return this;
		}

		public Case otherwise(Object result) {
			otherwise = expression(result);
			return this;
		}

		@Override
		public void render(Context c) {
			c.enter(this);
			try {
				c.add("CASE");
				for (int i = 0; i < conditions.size(); i++) {
					c.add(" WHEN ");
					conditions.get(i).render(c);
					c.add(" THEN ");
					results.get(i).render(c);
				}
				if (otherwise != null) {
					c.add(" ELSE ");
					otherwise.render(c);
				}
				c.add(" END");
			} finally {
				c.leave(this);
			}
		}
	}

	public static final class Order {
		final Expression expression;
		final boolean descending;

		Order(Expression e, boolean d) {
			expression = e;
			descending = d;
		}
	}

	public static final class Table implements Expression {
		final String name, alias;
		final Select query;

		Table(String name, String alias, Select query) {
			this.name = name;
			this.alias = alias;
			this.query = query;
		}

		@Override
		public Table as(String alias) {
			return new Table(name, Objects.requireNonNull(alias), query);
		}

		@Override
		public void render(Context c) {
			if (query == null)
				c.identifier(name);
			else {
				c.add("(");
				c.query(query);
				c.add(")");
			}
			if (alias != null) {
				c.add(" AS ");
				c.name(alias);
			}
		}
	}

	public static Table table(String name) {
		return new Table(Objects.requireNonNull(name), null, null);
	}

	public static Table table(Select query, String alias) {
		return new Table(null, Objects.requireNonNull(alias), Objects.requireNonNull(query));
	}

	public static Select select(Expression... fields) {
		return new Select(Arrays.asList(fields));
	}

	public static Select select(String... fields) {
		List<Expression> columns = new ArrayList<>();
		for (String field : fields)
			columns.add(column(field));
		return new Select(columns);
	}

	public static Select select() {
		return select(column("*"));
	}

	public enum JoinType {
		INNER, LEFT, RIGHT, FULL, CROSS
	}

	private static final class Join {
		final JoinType type;
		final Table table;
		final Condition on;

		Join(JoinType t, Table table, Condition on) {
			type = t;
			this.table = table;
			this.on = on;
		}
	}

	public static final class Select implements Query {
		final List<Expression> fields = new ArrayList<>(), groups = new ArrayList<>();
		final List<Order> orders = new ArrayList<>();
		final List<Join> joins = new ArrayList<>();
		Table from;
		Condition where, having;
		boolean distinct;
		Integer limit;
		int offset;
		final List<Select> unions = new ArrayList<>();
		final List<Boolean> unionAll = new ArrayList<>();
		final LinkedHashMap<String, Select> commonTables = new LinkedHashMap<>();

		Select(List<Expression> fields) {
			this.fields.addAll(fields.isEmpty() ? Collections.singletonList(column("*")) : fields);
		}

		public Select from(String table) {
			return from(table(table));
		}

		public Select from(Table table) {
			from = table;
			return this;
		}

		public Select from(Select query, String alias) {
			return from(table(query, alias));
		}

		public Select distinct() {
			distinct = true;
			return this;
		}

		/**
		 * Non-recursive CTE. Available in MySQL 8+, MariaDB 10.2+, SQL Server, H2 and
		 * SQLite 3.8.3+.
		 */
		public Select with(String name, Select query) {
			if (commonTables.containsKey(name))
				throw new IllegalArgumentException("Duplicate CTE name");
			commonTables.put(Objects.requireNonNull(name), Objects.requireNonNull(query));
			return this;
		}

		public Select where(Condition condition) {
			Objects.requireNonNull(condition);
			where = where == null ? condition : where.and(condition);
			return this;
		}

		public Select where(String column, Object value) {
			return where(column(column).eq(value));
		}

		public Select join(JoinType type, Table table, Condition on) {
			if (type != JoinType.CROSS)
				Objects.requireNonNull(on, "Join condition");
			joins.add(new Join(type, table, on));
			return this;
		}

		public Select join(Table table, Condition on) {
			return join(JoinType.INNER, table, on);
		}

		public Select leftJoin(Table table, Condition on) {
			return join(JoinType.LEFT, table, on);
		}

		public Select rightJoin(Table table, Condition on) {
			return join(JoinType.RIGHT, table, on);
		}

		public Select fullJoin(Table table, Condition on) {
			return join(JoinType.FULL, table, on);
		}

		public Select crossJoin(Table table) {
			return join(JoinType.CROSS, table, null);
		}

		public Select groupBy(Expression... fields) {
			groups.addAll(Arrays.asList(fields));
			return this;
		}

		public Select groupBy(String... fields) {
			for (String field : fields)
				groups.add(column(field));
			return this;
		}

		public Select having(Condition condition) {
			having = having == null ? condition : having.and(condition);
			return this;
		}

		public Select orderBy(Order... order) {
			orders.addAll(Arrays.asList(order));
			return this;
		}

		public Select limit(int count) {
			if (count < 0)
				throw new IllegalArgumentException("Negative limit");
			limit = count;
			return this;
		}

		public Select offset(int count) {
			if (count < 0)
				throw new IllegalArgumentException("Negative offset");
			offset = count;
			return this;
		}

		public Select union(Select query) {
			unions.add(query);
			unionAll.add(false);
			return this;
		}

		public Select unionAll(Select query) {
			unions.add(query);
			unionAll.add(true);
			return this;
		}

		public Expression scalar() {
			return Sql.scalar(this);
		}

		public Expression as(String alias) {
			return scalar().as(alias);
		}

		@Override
		public void render(Context c) {
			if (!commonTables.isEmpty()) {
				c.add("WITH ");
				boolean first = true;
				for (Map.Entry<String, Select> entry : commonTables.entrySet()) {
					if (!first)
						c.add(", ");
					first = false;
					c.name(entry.getKey());
					c.add(" AS (");
					c.query(entry.getValue());
					c.add(")");
				}
				c.add(" ");
			}
			c.add("SELECT ");
			if (distinct)
				c.add("DISTINCT ");
			boolean top = c.type == DatabaseType.SQLSERVER && limit != null && offset == 0 && unions.isEmpty();
			if (top)
				c.add("TOP (").add(limit.toString()).add(") ");
			c.expressions(fields);
			if (from != null) {
				c.add(" FROM ");
				from.render(c);
			}
			if (!joins.isEmpty() && from == null)
				throw new IllegalArgumentException("JOIN requires FROM");
			for (Join join : joins) {
				if (join.type == JoinType.FULL && (c.type == DatabaseType.MYSQL || c.type == DatabaseType.MARIADB)
						|| (join.type == JoinType.FULL || join.type == JoinType.RIGHT) && c.type == DatabaseType.SQLITE)
					throw new UnsupportedOperationException(join.type + " JOIN is not portable on " + c.type);
				c.add(" ").add(join.type.name()).add(" JOIN ");
				join.table.render(c);
				if (join.on != null) {
					c.add(" ON ");
					join.on.render(c);
				}
			}
			c.where(where);
			if (!groups.isEmpty()) {
				c.add(" GROUP BY ");
				c.expressions(groups);
			}
			if (having != null) {
				c.add(" HAVING ");
				having.render(c);
			}
			for (int i = 0; i < unions.size(); i++) {
				Select part = unions.get(i);
				if (part.limit != null || part.offset != 0 || !part.orders.isEmpty() || !part.unions.isEmpty()
						|| !part.commonTables.isEmpty())
					throw new IllegalArgumentException(
							"Move CTEs to the outer query and wrap ordered/limited UNION operands in a derived table");
				c.add(unionAll.get(i) ? " UNION ALL " : " UNION ");
				c.query(part);
			}
			if (!orders.isEmpty()) {
				c.add(" ");
				c.orderBy(orders);
			}
			if (!top)
				c.pagination(limit, offset, !orders.isEmpty());
		}
	}

	public static Insert insertInto(String table, String... columns) {
		return new Insert(table, columns);
	}

	public static final class Insert implements Query {
		final String table;
		final String[] columns;
		final List<Object[]> rows = new ArrayList<>();
		Select source;

		Insert(String table, String[] columns) {
			this.table = table;
			this.columns = columns.clone();
		}

		public Insert values(Object... values) {
			if (source != null)
				throw new IllegalStateException("INSERT already has SELECT");
			if (columns.length > 0 && values.length != columns.length
					|| !rows.isEmpty() && rows.get(0).length != values.length)
				throw new IllegalArgumentException("Row width mismatch");
			rows.add(values.clone());
			return this;
		}

		public Insert select(Select query) {
			if (!rows.isEmpty())
				throw new IllegalStateException("INSERT already has VALUES");
			source = query;
			return this;
		}

		@Override
		public void render(Context c) {
			c.add("INSERT INTO ");
			c.identifier(table);
			if (columns.length > 0) {
				c.add(" (");
				c.identifiers(columns);
				c.add(")");
			}
			if (source != null) {
				c.add(" ");
				c.query(source);
				return;
			}
			if (rows.isEmpty())
				throw new IllegalArgumentException("INSERT requires rows or SELECT");
			c.add(" VALUES ");
			for (int r = 0; r < rows.size(); r++) {
				if (r != 0)
					c.add(", ");
				c.add("(");
				Object[] row = rows.get(r);
				if (row.length == 0)
					throw new IllegalArgumentException("Empty INSERT row");
				for (int i = 0; i < row.length; i++) {
					if (i != 0)
						c.add(", ");
					expression(row[i]).render(c);
				}
				c.add(")");
			}
		}
	}

	public static Update update(String table) {
		return new Update(table);
	}

	public static Delete deleteFrom(String table) {
		return new Delete(table);
	}

	public abstract static class Mutation<T extends Mutation<T>> implements Query {
		final String table;
		Condition where;
		Integer limit;
		int offset;
		String[] keys;

		Mutation(String table) {
			this.table = table;
		}

		@SuppressWarnings("unchecked")
		public T where(Condition condition) {
			Objects.requireNonNull(condition);
			where = where == null ? condition : where.and(condition);
			return (T) this;
		}

		public T where(String field, Object value) {
			return where(column(field).eq(value));
		}

		@SuppressWarnings("unchecked")
		public T limit(int limit) {
			if (limit < 0)
				throw new IllegalArgumentException("Negative limit");
			this.limit = limit;
			return (T) this;
		}

		@SuppressWarnings("unchecked")
		public T offset(int offset) {
			if (offset < 0)
				throw new IllegalArgumentException("Negative offset");
			this.offset = offset;
			return (T) this;
		}

		/** Unique, non-null row identity used for portable limited mutations. */
		@SuppressWarnings("unchecked")
		public T keyColumns(String... keys) {
			if (keys.length == 0)
				throw new IllegalArgumentException("Empty key");
			this.keys = keys.clone();
			return (T) this;
		}

		void predicate(Context c) {
			if (limit != null && c.type == DatabaseType.SQLITE) {
				String[] keys = this.keys == null ? c.mutationKeys : this.keys;
				if (keys == null)
					throw new IllegalStateException(
							"SQLite limited mutations require keyColumns; handler resolves them from metadata");
				c.add(" WHERE ");
				if (keys.length > 1)
					c.add("(");
				c.identifiers(keys);
				if (keys.length > 1)
					c.add(")");
				c.add(" IN (SELECT ");
				c.identifiers(keys);
				c.add(" FROM ");
				c.identifier(table);
				c.where(where);
				c.pagination(limit, offset, false);
				c.add(")");
				return;
			}
			if (offset != 0)
				throw new UnsupportedOperationException(
						"Mutation offsets require SQLite key selection; use a key subquery on other databases");
			c.where(where);
			if (limit != null && c.type != DatabaseType.SQLSERVER)
				if (c.type == DatabaseType.H2)
					c.add(" FETCH FIRST ").add(limit.toString()).add(" ROWS ONLY");
				else
					c.add(" LIMIT ").add(limit.toString());
		}
	}

	public static final class Update extends Mutation<Update> {
		final LinkedHashMap<String, Expression> values = new LinkedHashMap<>();

		Update(String table) {
			super(table);
		}

		public Update set(String column, Object value) {
			values.put(column, expression(value));
			return this;
		}

		@Override
		public void render(Context c) {
			if (values.isEmpty())
				throw new IllegalArgumentException("UPDATE has no assignments");
			c.add("UPDATE ");
			if (limit != null && c.type == DatabaseType.SQLSERVER)
				c.add("TOP (").add(limit.toString()).add(") ");
			c.identifier(table);
			c.add(" SET ");
			boolean first = true;
			for (Map.Entry<String, Expression> value : values.entrySet()) {
				if (!first)
					c.add(", ");
				first = false;
				c.identifier(value.getKey());
				c.add(" = ");
				value.getValue().render(c);
			}
			predicate(c);
		}
	}

	public static final class Delete extends Mutation<Delete> {
		Delete(String table) {
			super(table);
		}

		@Override
		public void render(Context c) {
			c.add("DELETE ");
			if (limit != null && c.type == DatabaseType.SQLSERVER)
				c.add("TOP (").add(limit.toString()).add(") ");
			c.add("FROM ");
			c.identifier(table);
			predicate(c);
		}
	}

	public static SqlStatement compile(Query query, DatabaseType type) {
		Context context = new Context(type);
		context.query(query);
		return new SqlStatement(context.sql.toString(), context.parameters);
	}

	public static final class Context {
		String[] mutationKeys;
		final DatabaseType type;
		final StringContainer sql = new StringContainer();
		final List<Object> parameters = new ArrayList<>();
		final Set<Object> active = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());

		Context(DatabaseType type) {
			this.type = Objects.requireNonNull(type);
		}

		public Context add(String text) {
			sql.append(text);
			return this;
		}

		public void parameter(Object value) {
			sql.append('?');
			parameters.add(value);
		}

		public DatabaseType dialect() {
			return type;
		}

		void enter(Object node) {
			if (active.size() >= 128 || !active.add(Objects.requireNonNull(node)))
				throw new IllegalArgumentException("Cyclic or excessively nested SQL query");
		}

		void leave(Object node) {
			active.remove(node);
		}

		void query(Query query) {
			if (type == DatabaseType.SQLSERVER && !active.isEmpty() && query instanceof Select
					&& !((Select) query).commonTables.isEmpty())
				throw new UnsupportedOperationException("SQL Server CTEs must be declared on the outer SELECT");
			enter(query);
			try {
				query.render(this);
			} finally {
				leave(query);
			}
		}

		void orderBy(List<Order> orders) {
			add("ORDER BY ");
			for (int i = 0; i < orders.size(); i++) {
				if (i != 0)
					add(", ");
				Order order = orders.get(i);
				order.expression.render(this);
				add(order.descending ? " DESC" : " ASC");
			}
		}

		public void identifier(String name) {
			Objects.requireNonNull(name);
			String[] parts = name.split("\\.", -1);
			for (int i = 0; i < parts.length; i++) {
				if (i != 0)
					sql.append('.');
				String part = parts[i];
				if (part.isEmpty() || part.indexOf('\0') >= 0)
					throw new IllegalArgumentException("Empty/invalid identifier");
				if ("*".equals(part)) {
					sql.append('*');
					continue;
				}
				name(part);
			}
		}

		/**
		 * Quotes one name, including dots in aliases; identifier() quotes qualified
		 * paths.
		 */
		public void name(String name) {
			if (Objects.requireNonNull(name).isEmpty() || name.indexOf('\0') >= 0)
				throw new IllegalArgumentException("Empty/invalid name");
			String open = type == DatabaseType.MYSQL || type == DatabaseType.MARIADB ? "`"
					: type == DatabaseType.SQLSERVER ? "[" : "\"";
			String close = type == DatabaseType.SQLSERVER ? "]" : open;
			sql.append(open).append(name.replace(close, close + close)).append(close);
		}

		void identifiers(String[] names) {
			for (int i = 0; i < names.length; i++) {
				if (i != 0)
					add(", ");
				identifier(names[i]);
			}
		}

		void expressions(List<Expression> fields) {
			for (int i = 0; i < fields.size(); i++) {
				if (i != 0)
					add(", ");
				fields.get(i).render(this);
			}
		}

		void where(Condition condition) {
			if (condition != null) {
				add(" WHERE ");
				condition.render(this);
			}
		}

		void pagination(Integer limit, int offset, boolean ordered) {
			if (limit == null && offset == 0)
				return;
			if (type == DatabaseType.SQLSERVER) {
				if (!ordered)
					throw new IllegalArgumentException("SQL Server OFFSET/FETCH requires ORDER BY");
				if (limit != null && limit == 0)
					throw new IllegalArgumentException("SQL Server FETCH requires a positive limit");
				add(" OFFSET ").add(Integer.toString(offset)).add(" ROWS");
				if (limit != null)
					add(" FETCH NEXT ").add(limit.toString()).add(" ROWS ONLY");
			} else if (type == DatabaseType.H2) {
				if (offset > 0)
					add(" OFFSET ").add(Integer.toString(offset)).add(" ROWS");
				if (limit != null)
					add(" FETCH FIRST ").add(limit.toString()).add(" ROWS ONLY");
			} else {
				add(" LIMIT ").add(
						limit != null ? limit.toString() : type == DatabaseType.SQLITE ? "-1" : "18446744073709551615");
				if (offset > 0)
					add(" OFFSET ").add(Integer.toString(offset));
			}
		}
	}
}
