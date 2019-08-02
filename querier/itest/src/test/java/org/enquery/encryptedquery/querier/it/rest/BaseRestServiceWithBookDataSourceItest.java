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
package org.enquery.encryptedquery.querier.it.rest;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.enquery.encryptedquery.querier.QuerierProperties;
import org.enquery.encryptedquery.querier.data.entity.RetrievalStatus;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSourceResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Query;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaField;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.entity.json.ResultResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.json.RetrievalResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleResponse;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.Constants;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BaseRestServiceWithBookDataSourceItest extends BaseRestServiceItest {

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
	private int querySchemaCount;

	@Configuration
	public Option[] configuration() {
		return new Option[] {baseOptions(),
				editConfigurationFilePut("etc/system.properties",
						"derby.language.logStatementText",
						"true"),
				mavenBundle()
						.groupId("org.apache.derby")
						.artifactId("derbyclient")
						.versionAsInProject()
		};
	}

	@Override
	@ProbeBuilder
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

	@Override
	@Before
	public void init() throws Exception {
		log.info("Initializing");
		super.init();
		waitForFileConsumed(new File(responderInboxDir, "books-data-schema.xml"));
		initializeQueryDatabase();
		log.info("Finished Initializing");
	}

	private void initializeQueryDatabase() throws Exception {
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

	protected ScheduleResponse postSchedule(String dataSourceName) throws Exception {

		DataSchemaResponse jsonDataSchema = retrieveDataSchemaByName("Books");
		DataSourceResponse jsonDataSource = retrieveDataSourceByName(jsonDataSchema, dataSourceName);

		QuerySchema querySchema = createQuerySchema(jsonDataSchema.getData());
		Query query = createQueryAndWaitForEncryption(querySchema);

		return postSchedule(query.getSchedulesUri(), jsonDataSource.getData().getId(), dataSourceParams());
	}

	protected Query createQueryAndWaitForEncryption(QuerySchema querySchema) throws Exception {
		Query query = createQuery();
		return createQueryAndWaitForEncryption(querySchema.getQueriesUri(), query).getData();
	}

	protected Map<String, String> dataSourceParams() {
		Map<String, String> result = new HashMap<>();
		// result.put(ResponderProperties.MAX_HITS_PER_SELECTOR, "1000");
		return result;
	}

	protected QuerySchema createQuerySchema(DataSchema dataSchema) {
		QuerySchema result = new QuerySchema();

		result.setName("Query Schema " + querySchemaCount++);
		result.setSelectorField("title");

		Resource dsr = new Resource();
		dsr.setId(dataSchema.getId());
		result.setDataSchema(dsr);

		QuerySchemaField field = new QuerySchemaField();
		field.setName("author");
		field.setSize(100);
		field.setMaxArrayElements(1);
		List<QuerySchemaField> fields = new ArrayList<>();
		fields.add(field);
		QuerySchemaField field2 = new QuerySchemaField();
		field2.setName("title");
		field2.setSize(100);
		field2.setMaxArrayElements(1);
		fields.add(field2);
		result.setFields(fields);

		return createQuerySchema(dataSchema.getQuerySchemasUri(), result).getData();
	}

	protected org.enquery.encryptedquery.querier.data.entity.json.Query createQuery() {
		org.enquery.encryptedquery.querier.data.entity.json.Query q = new org.enquery.encryptedquery.querier.data.entity.json.Query();
		q.setName("Test Query " + ++queryCount);

		// TODO: replace params with specific properties
		Map<String, String> params = new HashMap<>();
		params.put(QuerierProperties.DATA_CHUNK_SIZE, "1");
		params.put(QuerierProperties.HASH_BIT_SIZE, "8");
		// params.put(QuerierProperties.CERTAINTY, "128");
		q.setParameters(params);

		List<String> selectorValues = new ArrayList<>();
		selectorValues.add("A Cup of Java");
		q.setSelectorValues(selectorValues);

		return q;
	}

	protected ResultResponse postScheduleAndWaitForResult(String dataSourceName) throws Exception {
		return waitForScheduleResult(postSchedule(dataSourceName));
	}

	protected Retrieval submitQueryAndRetrieveResult(String dataSourceName) throws Exception {
		ResultResponse resultResponse = postScheduleAndWaitForResult(dataSourceName);
		RetrievalResponse r = postRetrieval(resultResponse);
		Resource retrieval = r.getData();

		tryUntilTrue(120,
				2_000,
				"Timed out waiting for retrieval completion",
				uri -> retrieveRetrieval(uri).getData().getStatus() == RetrievalStatus.Complete,
				retrieval.getSelfUri());

		Retrieval fullRetrieval = retrieveRetrieval(retrieval.getSelfUri()).getData();
		return fullRetrieval;
	}
}
