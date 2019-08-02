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
package org.enquery.encryptedquery.xml.transformation;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import javax.xml.bind.JAXBException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import org.enquery.encryptedquery.xml.schema.DataSchema;
import org.enquery.encryptedquery.xml.schema.DataSchema.Field;
import org.enquery.encryptedquery.xml.schema.DataSchemaResource;
import org.enquery.encryptedquery.xml.schema.DataSchemaResources;
import org.enquery.encryptedquery.xml.schema.DataSource;
import org.enquery.encryptedquery.xml.schema.DataSourceExport;
import org.enquery.encryptedquery.xml.schema.DataSourceResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResources;
import org.junit.Test;

/**
 *
 */
public class DataSourceExportConverterTest {

	@Test
	public void test() throws UnsupportedEncodingException, JAXBException, IOException, XMLStreamException, FactoryConfigurationError {
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

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		DataSourceExportConverter converter = new DataSourceExportConverter();
		converter.marshal(dse, os);

		InputStream inputStream = new ByteArrayInputStream(os.toByteArray());

		DataSourceExport loaded = converter.unmarshal(inputStream);
		assertEquals(1, loaded.getDataSchemas().getDataSchemaResource().size());
		assertEquals(1, loaded.getDataSources().getDataSourceResource().size());
	}

}
