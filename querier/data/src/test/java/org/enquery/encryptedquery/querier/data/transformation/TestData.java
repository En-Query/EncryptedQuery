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
package org.enquery.encryptedquery.querier.data.transformation;

import static org.mockito.Matchers.anyInt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.xml.schema.DataSchema;
import org.enquery.encryptedquery.xml.schema.DataSchema.Field;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Query;
import org.enquery.encryptedquery.xml.schema.Query.QueryElements;
import org.enquery.encryptedquery.xml.schema.QueryInfo;
import org.enquery.encryptedquery.xml.schema.QuerySchema;
import org.xml.sax.SAXException;

/**
 */
public class TestData {

	static final String ENTRY_VALUE_PATTERN = "entry value %d";
	private static final Path QUERY_FILE_NAME = Paths.get("target/query.xml");
	private static final String EXECUTION_SCHEMA_FILE = "target/dependency/org/enquery/encryptedquery/xml/schema/execution.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	/**
	 * @throws Exception
	 * 
	 */
	public TestData() throws Exception {
		Files.deleteIfExists(QUERY_FILE_NAME);
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		try {
			xmlSchema = factory.newSchema(new File(EXECUTION_SCHEMA_FILE));
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (JAXBException | SAXException e) {
			throw new RuntimeException("Error initializing XSD schema.", e);
		}
		objectFactory = new ObjectFactory();

		try (OutputStream os = new FileOutputStream(QUERY_FILE_NAME.toFile())) {
			marshal(makeQuery(), os);
		}
	}


	public void marshal(org.enquery.encryptedquery.xml.schema.Query query, OutputStream os) throws JAXBException {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.marshal(objectFactory.createQuery(query), os);
	}

	public void marshal(org.enquery.encryptedquery.xml.schema.Execution ex, OutputStream os) throws JAXBException {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.marshal(objectFactory.createExecution(ex), os);
	}

	public org.enquery.encryptedquery.xml.schema.Execution unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.Execution> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.Execution.class);
		return element.getValue();
	}


	Query makeQuery() {
		Query result = new Query();
		result.setSchemaVersion(new BigDecimal("2.0"));
		result.setQueryInfo(makeQueryInfo());
		result.setQueryElements(makeQueryElements());
		return result;
	}

	/**
	 * @return
	 */
	QueryElements makeQueryElements() {
		QueryElements result = new QueryElements();

		for (int i = 0; i < 5; ++i) {
			result.getEntry().add(makeQueryEntry(i));
		}

		return result;
	}


	org.enquery.encryptedquery.xml.schema.Query.QueryElements.Entry makeQueryEntry(int index) {
		org.enquery.encryptedquery.xml.schema.Query.QueryElements.Entry entry = //
				new org.enquery.encryptedquery.xml.schema.Query.QueryElements.Entry();
		entry.setKey(index);
		entry.setValue(String.format(ENTRY_VALUE_PATTERN, index).getBytes());
		return entry;
	}


	QueryInfo makeQueryInfo() {
		QueryInfo result = new QueryInfo();
		result.setCryptoSchemeId("Paillier");
		result.setDataChunkSize(8);
		result.setHashBitSize(12);
		result.setHashKey("hash key");
		result.setNumBitsPerDataElement(8);
		result.setNumPartitionsPerDataElement(10);
		result.setNumSelectors(1);
		result.setPublicKey("public key".getBytes());
		result.setQueryId("test query id");
		result.setQueryName("test");
		result.setQuerySchema(makeQuerySchema());
		return result;
	}

	QuerySchema makeQuerySchema() {
		QuerySchema result = new QuerySchema();
		result.setName("Query Schema Test");
		result.setSelectorField("field");
		org.enquery.encryptedquery.xml.schema.QuerySchema.Field field = //
				new org.enquery.encryptedquery.xml.schema.QuerySchema.Field();

		field.setName("field");
		field.setMaxArrayElements(12);
		field.setSize(33);

		result.getField().add(field);
		result.setDataSchema(makeDataSchema());
		return result;
	}

	DataSchema makeDataSchema() {
		DataSchema result = new DataSchema();
		result.setName("Test Data Schema");
		Field field = new Field();
		field.setName("field");
		field.setDataType("string");
		result.getField().add(field);
		return result;
	}

	QueryRepository mockQueryRepo() throws FileNotFoundException, IOException {
		QueryRepository mock = org.mockito.Mockito.mock(QueryRepository.class);
		org.mockito.Mockito.when(mock.loadQueryBytes(anyInt())).thenReturn(
				new FileInputStream(QUERY_FILE_NAME.toFile()));
		return mock;
	}
}
