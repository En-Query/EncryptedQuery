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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.karaf.shell.api.console.SessionFactory;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSourceCollectionResponse;
import org.enquery.encryptedquery.querier.it.util.KarafController;
import org.enquery.encryptedquery.xml.schema.DataSchema;
import org.enquery.encryptedquery.xml.schema.DataSchema.Field;
import org.enquery.encryptedquery.xml.schema.DataSchemaResource;
import org.enquery.encryptedquery.xml.schema.DataSchemaResources;
import org.enquery.encryptedquery.xml.schema.DataSource;
import org.enquery.encryptedquery.xml.schema.DataSourceExport;
import org.enquery.encryptedquery.xml.schema.DataSourceResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResources;
import org.enquery.encryptedquery.xml.transformation.DataSourceExportConverter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class OfflineDataSourceRestServiceIT extends BaseRestServiceItest {

	@Inject
	private DataSourceExportConverter converter;
	@Inject
	protected SessionFactory sessionFactory;

	@Configuration
	public Option[] configuration() {
		return CoreOptions.options(baseOptions());//
	}


	@Override
	@Before
	public void init() throws Exception {
		super.init();
		// emulate Responder not online
		configureOfflineMode(true);
	}

	@After
	public void cleanup() throws IOException, InterruptedException {
		configureOfflineMode(false);
	}

	@Test
	public void uploadMultipart() throws Exception {

		Path outPath = createDataSourceXML();

		// Encode the file as a multipart entity…
		MultipartEntityBuilder entity =
				MultipartEntityBuilder.create().setMode(HttpMultipartMode.STRICT);
		entity.addBinaryBody("file", outPath.toFile(), ContentType.create("application/xml"),
				outPath.getFileName().toString());

		upload("/querier/api/rest/offline/datasources", entity.build());

		validate();
	}


	@Test
	public void uploadXMLDirect() throws Exception {

		Path outPath = createDataSourceXML();

		upload("/querier/api/rest/offline/datasources", outPath.toFile());

		validate();
	}


	@Test
	public void uploadViaCommand() throws Exception {

		Path outPath = createDataSourceXML();

		KarafController karaf = new KarafController(sessionFactory);

		karaf.executeCommand("datasource:import -i " + outPath.toAbsolutePath());

		validate();
	}


	private void validate() {
		DataSchemaCollectionResponse retrieveDataSchemas;
		retrieveDataSchemas = retrieveDataSchemas();
		assertEquals(1, retrieveDataSchemas.getData().size());
		assertEquals("Schema 1", retrieveDataSchemas.getData().iterator().next().getName());

		DataSchemaResponse dataSchema = retrieveDataSchema(retrieveDataSchemas.getData().iterator().next().getSelfUri());

		DataSourceCollectionResponse dataSources = retrieveDataSources(dataSchema);
		assertEquals(1, dataSources.getData().size());
		assertEquals("data source 1", dataSources.getData().iterator().next().getName());
	}


	private Path createDataSourceXML() throws JAXBException, UnsupportedEncodingException, IOException, XMLStreamException, FactoryConfigurationError {
		DataSchemaCollectionResponse retrieveDataSchemas = retrieveDataSchemas();
		assertEquals(0, retrieveDataSchemas.getData().size());

		DataSourceExport dse = new DataSourceExport();
		DataSchemaResource schemaRes1 = new DataSchemaResource();
		schemaRes1.setId(1);
		schemaRes1.setSelfUri("schema 1 uri");
		schemaRes1.setDataSourcesUri("schema 1 data sources");
		DataSchema schema1 = new DataSchema();
		schema1.setName("Schema 1");
		Field field1 = new Field();
		field1.setDataType("string");
		field1.setName("field 1");
		field1.setPosition(0);
		schema1.getField().add(field1);
		schemaRes1.setDataSchema(schema1);
		DataSchemaResources dataSchemaResources = new DataSchemaResources();
		dataSchemaResources.getDataSchemaResource().add(schemaRes1);
		dse.setDataSchemas(dataSchemaResources);

		DataSourceResource dataSourceRes = new DataSourceResource();
		dataSourceRes.setDataSchemaUri("schema 1 uri");
		dataSourceRes.setId(2);
		dataSourceRes.setSelfUri("data source uri");
		dataSourceRes.setExecutionsUri("executions uri");
		DataSource dataSource1 = new DataSource();
		dataSource1.setName("data source 1");
		dataSource1.setDataSchemaName("data source 1");
		dataSource1.setDescription("description of data source 1");
		dataSource1.setDataSchemaName("Schema 1");
		dataSource1.setType("Batch");
		dataSourceRes.setDataSource(dataSource1);
		DataSourceResources dataSourceResources = new DataSourceResources();
		dataSourceResources.getDataSourceResource().add(dataSourceRes);
		dse.setDataSources(dataSourceResources);

		Path outPath = Paths.get("data/data-sourc-export.xml");
		converter.marshal(dse, Files.newOutputStream(outPath));
		return outPath;
	}
}
