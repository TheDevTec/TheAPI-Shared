package me.devtec.shared.database;

/**
 * @apiNote Some Sql field types are not supported by all SQLs and you should
 *          have to use another alternative.
 * @author Straikerinos
 *
 */
public enum SqlFieldType {
	// String Data Types
	CHAR, VARCHAR, BINARY, VARBINARY, TINYBLOB, TINYTEXT, TEXT, BLOB, MEDIUMTEXT, MEDIUMBLOB, LONGTEXT, LONGBLOB, ENUM, SET,
	// Numeric Data Types
	BIT, TINYINT, BOOL, BOOLEAN, SMALLINT, MEDIUMINT, INT, BIGINT, LONG, FLOAT, DOUBLE, DECIMAL,
	// Date and Time Data Types
	DATE, DATETIME, TIMESTAMP, TIME, YEAR;

}
