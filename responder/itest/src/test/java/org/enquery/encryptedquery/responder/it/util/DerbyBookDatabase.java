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
package org.enquery.encryptedquery.responder.it.util;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DerbyBookDatabase {

	public static final Logger log = LoggerFactory.getLogger(DerbyBookDatabase.class);

	static class TestEntry {
		protected final Integer id;
		protected final String title;
		protected final String author;
		protected final Double price;
		protected final Integer qty;

		private TestEntry(Integer id, String title, String author, Double price, Integer qty) {
			this.id = id;
			this.title = title;
			this.author = author;
			this.price = price;
			this.qty = qty;
		}
	}

	public static final TestEntry[] TEST_DATA = {
			new TestEntry(1001, ("Java public for dummies"), ("Tan Ah Teck"), 11.11, 11),
			new TestEntry(1002, ("More Java for dummies"), ("Tan Ah Teck"), 22.22, 22),
			new TestEntry(1003, ("More Java for more dummies"), ("Mohammad Ali"), 33.33, 33),
			new TestEntry(1004, ("A Cup of Java"), ("Kumar"), 44.44, 44),
			new TestEntry(1005, ("A Teaspoon of Java"), ("Kevin Jones"), 55.55, 55),
			new TestEntry(1006, ("A Teaspoon of Java 1.4"), ("Kevin Jones"), 66.66, 66),
			new TestEntry(1007, ("A Teaspoon of Java 1.5"), ("Kevin Jones"), 77.77, 77),
			new TestEntry(1008, ("A Teaspoon of Java 1.6"), ("Kevin Jones"), 88.88, 88),
			new TestEntry(1009, ("A Teaspoon of Java 1.7"), ("Kevin Jones"), 99.99, 99),
			new TestEntry(1010, ("A Teaspoon of Java 1.8"), ("Kevin Jones"), null, 1010)
	};

	// @Configuration
	public Option[] configuration() {
		return CoreOptions.options(
				systemProperty("derby.language.logStatementText")
						.value("true"),
				mavenBundle()
						.groupId("org.apache.derby")
						.artifactId("derbyclient")
						.versionAsInProject());
	}

	public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
		// this is needed to avoid importing Derby packages from derby.jar,
		// we need classes from derbyclient.jar to connect to the Derby database
		// started from the POM which is not the same as the main Database
		probe.setHeader(Constants.DYNAMICIMPORT_PACKAGE,
				"org.apache.derby.jdbc;bundle-symbolic-name=derbyclient,"
						+ "org.apache.derby.loc;bundle-symbolic-name=derbyclient,"
						+ "org.apache.felix.service.*;status=provisional,*");
		return probe;
	}

	public void init() throws Exception {
		Class.forName("org.apache.derby.jdbc.ClientDriver");
		try (Connection conn =
				DriverManager.getConnection("jdbc:derby://localhost/data/derby-data/books;create=true");
				Statement s = conn.createStatement();) {

			DatabaseMetaData databaseMetadata = conn.getMetaData();
			ResultSet resultSet = databaseMetadata.getTables(null, null, "BOOKS", null);
			if (resultSet.next()) {
				s.executeUpdate("DROP TABLE BOOKS");
			}
			s.executeUpdate(createTableSQL());
			s.executeUpdate(getInsertQuery());
		}
	}

	private String createTableSQL() {
		StringBuilder sqlQueryBuilder = new StringBuilder("CREATE TABLE BOOKS ");
		sqlQueryBuilder.append(" (");
		sqlQueryBuilder.append("id INT NOT NULL DEFAULT 0,");
		sqlQueryBuilder.append("title VARCHAR(50) DEFAULT NULL,");
		sqlQueryBuilder.append("author VARCHAR(50) DEFAULT NULL,");
		sqlQueryBuilder.append("price FLOAT DEFAULT NULL,");
		sqlQueryBuilder.append("qty INT DEFAULT NULL,");
		sqlQueryBuilder.append("PRIMARY KEY (id))");
		return sqlQueryBuilder.toString();
	}

	private static String getInsertQuery() {
		StringBuilder sqlQueryBuilder = new StringBuilder("INSERT INTO books (id, title, author, price, qty) VALUES ");
		for (int i = 0; i < TEST_DATA.length; i++) {
			sqlQueryBuilder.append("(")
					.append(TEST_DATA[i].id).append(",'")
					.append(TEST_DATA[i].title).append("','")
					.append(TEST_DATA[i].author).append("',")
					.append(TEST_DATA[i].price).append(",")
					.append(TEST_DATA[i].qty).append(")");
			if (i < TEST_DATA.length - 1) {
				sqlQueryBuilder.append(",");
			}
		}
		String insertQuery = sqlQueryBuilder.toString();
		return insertQuery;
	}


}
