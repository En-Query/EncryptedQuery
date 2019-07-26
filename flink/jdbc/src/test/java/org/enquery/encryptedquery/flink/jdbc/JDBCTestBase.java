/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.flink.jdbc;

import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.enquery.encryptedquery.core.FieldType;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base test class for JDBC Input and Output formats.
 */
public class JDBCTestBase {

	private final static Logger log = LoggerFactory.getLogger(JDBCTestBase.class);

	public static final String DRIVER_CLASS = "org.apache.derby.jdbc.EmbeddedDriver";
	public static final String DB_URL = "jdbc:derby:memory:ebookshop";
	public static final String INPUT_TABLE = "books";
	public static final String OUTPUT_TABLE = "newbooks";
	public static final String OUTPUT_TABLE_2 = "newbooks2";
	public static final String SELECT_ALL_BOOKS = "select * from " + INPUT_TABLE;
	public static final String SELECT_ALL_NEWBOOKS = "select * from " + OUTPUT_TABLE;
	public static final String SELECT_ALL_NEWBOOKS_2 = "select * from " + OUTPUT_TABLE_2;
	public static final String SELECT_EMPTY = "select * from books WHERE QTY < 0";
	public static final String INSERT_TEMPLATE = "insert into %s (id, title, author, price, qty, release_dt) values (?,?,?,?,?,?)";
	public static final String SELECT_ALL_BOOKS_SPLIT_BY_ID = SELECT_ALL_BOOKS + " WHERE id BETWEEN ? AND ?";
	public static final String SELECT_ALL_BOOKS_SPLIT_BY_AUTHOR = SELECT_ALL_BOOKS + " WHERE author = ?";


	public static final List<String> FIELD_NAMES_LIST = Arrays.asList(new String[] {
			"id", "title", "author", "price", "qty", "release_dt"});
	public static final Set<String> FIELD_NAMES_SET = new HashSet<>(FIELD_NAMES_LIST);

	public static final Map<String, FieldType> fieldType = new HashMap<>();
	{
		fieldType.put("id", FieldType.INT);
		fieldType.put("title", FieldType.STRING);
		fieldType.put("author", FieldType.STRING);
		fieldType.put("price", FieldType.DOUBLE);
		fieldType.put("qty", FieldType.INT);
		fieldType.put("release_dt", FieldType.ISO8601DATE);
	}

	public static final TestEntry[] TEST_DATA = {
			new TestEntry(1001, ("Java public for dummies"), ("Tan Ah Teck"), 11.11, 11, "2001-01-03T10:15:30.000Z"),
			new TestEntry(1002, ("More Java for dummies"), ("Tan Ah Teck"), 22.22, 22, "2008-06-11T07:15:30.000Z"),
			new TestEntry(1003, ("More Java for more dummies"), ("Mohammad Ali"), 33.33, 33, "2012-01-03T10:15:30.000Z"),
			new TestEntry(1004, ("A Cup of Java"), ("Kumar"), 44.44, 44, "2001-01-03T11:18:00.000Z"),
			new TestEntry(1005, ("A Teaspoon of Java"), ("Kevin Jones"), 55.55, 55, "2016-07-04T16:00:00.000Z"),
			new TestEntry(1006, ("A Teaspoon of Java 1.4"), ("Kevin Jones"), 66.66, 66, "2016-08-08T10:15:30.000Z"),
			new TestEntry(1007, ("A Teaspoon of Java 1.5"), ("Kevin Jones"), 77.77, 77, "2001-01-03T10:15:30.000Z"),
			new TestEntry(1008, ("A Teaspoon of Java 1.6"), ("Kevin Jones"), 88.88, 88, "2001-01-03T10:15:30.000Z"),
			new TestEntry(1009, ("A Teaspoon of Java 1.7"), ("Kevin Jones"), 99.99, 99, "2001-01-03T10:15:30.000Z"),
			new TestEntry(1010, ("A Teaspoon of Java 1.8"), ("Kevin Jones"), null, 1010, "2001-01-03T10:15:30.000Z")
	};

	static class TestEntry {
		protected final Integer id;
		protected final String title;
		protected final String author;
		protected final Double price;
		protected final Integer qty;
		protected final String releaseDate;

		private TestEntry(Integer id, String title, String author, Double price, Integer qty, String releaseDate) {
			this.id = id;
			this.title = title;
			this.author = author;
			this.price = price;
			this.qty = qty;
			this.releaseDate = releaseDate;
		}
	}

	public static final RowTypeInfo ROW_TYPE_INFO = new RowTypeInfo(
			BasicTypeInfo.INT_TYPE_INFO,
			BasicTypeInfo.STRING_TYPE_INFO,
			BasicTypeInfo.STRING_TYPE_INFO,
			BasicTypeInfo.DOUBLE_TYPE_INFO,
			BasicTypeInfo.INT_TYPE_INFO);

	public static String getCreateQuery(String tableName) {
		StringBuilder sqlQueryBuilder = new StringBuilder("CREATE TABLE ");
		sqlQueryBuilder.append(tableName).append(" (");
		sqlQueryBuilder.append("id INT NOT NULL DEFAULT 0,");
		sqlQueryBuilder.append("title VARCHAR(50) DEFAULT NULL,");
		sqlQueryBuilder.append("author VARCHAR(50) DEFAULT NULL,");
		sqlQueryBuilder.append("price DOUBLE DEFAULT NULL,");
		sqlQueryBuilder.append("qty INT DEFAULT NULL,");
		sqlQueryBuilder.append("release_dt varchar(50),");
		sqlQueryBuilder.append("PRIMARY KEY (id))");
		return sqlQueryBuilder.toString();
	}

	public static String getInsertQuery() {
		StringBuilder sqlQueryBuilder = new StringBuilder("INSERT INTO books (id, title, author, price, qty, release_dt) VALUES ");
		for (int i = 0; i < TEST_DATA.length; i++) {
			sqlQueryBuilder.append("(")
					.append(TEST_DATA[i].id).append(",'")
					.append(TEST_DATA[i].title).append("','")
					.append(TEST_DATA[i].author).append("',")
					.append(TEST_DATA[i].price).append(",")
					.append(TEST_DATA[i].qty).append(", '")
					.append(TEST_DATA[i].releaseDate)
					// .append(Timestamp.from(Instant.parse(TEST_DATA[i].releaseDate)))
					.append("')");

			if (i < TEST_DATA.length - 1) {
				sqlQueryBuilder.append(",");
			}
		}
		String insertQuery = sqlQueryBuilder.toString();
		return insertQuery;
	}

	public static final OutputStream DEV_NULL = new OutputStream() {
		@Override
		public void write(int b) {}
	};

	@BeforeClass
	public static void prepareDerbyDatabase() throws Exception {
		System.setProperty("derby.stream.error.field", JDBCTestBase.class.getCanonicalName() + ".DEV_NULL");

		Class.forName(DRIVER_CLASS);
		try (Connection conn = DriverManager.getConnection(DB_URL + ";create=true")) {
			createTable(conn, JDBCTestBase.INPUT_TABLE);
			createTable(conn, OUTPUT_TABLE);
			createTable(conn, OUTPUT_TABLE_2);
			insertDataIntoInputTable(conn);
		}
	}

	private static void createTable(Connection conn, String tableName) throws SQLException {
		Statement stat = conn.createStatement();
		stat.executeUpdate(getCreateQuery(tableName));
		stat.close();
	}

	private static void insertDataIntoInputTable(Connection conn) throws SQLException {
		Statement stat = conn.createStatement();
		String sql = getInsertQuery();
		log.info(sql);
		stat.execute(sql);
		stat.close();
	}

	@AfterClass
	public static void cleanUpDerbyDatabases() throws Exception {
		Class.forName(DRIVER_CLASS);
		try (
				Connection conn = DriverManager.getConnection(DB_URL + ";create=true");
				Statement stat = conn.createStatement()) {

			stat.executeUpdate("DROP TABLE " + INPUT_TABLE);
			stat.executeUpdate("DROP TABLE " + OUTPUT_TABLE);
			stat.executeUpdate("DROP TABLE " + OUTPUT_TABLE_2);
		}
	}
}
