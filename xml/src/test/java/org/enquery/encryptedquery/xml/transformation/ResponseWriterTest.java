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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.encryption.nullcipher.NullCipherCryptoScheme;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class ResponseWriterTest {

	// private final Logger log = LoggerFactory.getLogger(ResponseWriterTest.class);

	private QueryTypeConverter queryConverter;
	private ResponseTypeConverter converter;

	private CryptoScheme crypto;
	private PublicKey publicKey;


	@Before
	public void init() throws Exception {

		Map<String, String> config = new HashMap<>();
		crypto = new NullCipherCryptoScheme();
		crypto.initialize(config);

		publicKey = crypto.generateKeyPair().getPublic();

		CryptoSchemeRegistry cryptoRegistry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId.equals(crypto.name())) {
					return crypto;
				}
				return null;
			}
		};

		queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(cryptoRegistry);
		queryConverter.initialize();

		converter = new ResponseTypeConverter();
		converter.setQueryConverter(queryConverter);
		converter.setSchemeRegistry(cryptoRegistry);
		converter.initialize();
	}

	@Test
	public void test() throws FileNotFoundException, IOException, XMLStreamException, JAXBException {

		Path outputFile = Paths.get("target/response.xml");
		Path responseFile = Paths.get("src/test/resources/response.xml");
		org.enquery.encryptedquery.data.Response sample = makeSampleResponse();
		try (InputStream is = new FileInputStream(responseFile.toFile())) {
			try (OutputStream out = Files.newOutputStream(outputFile);
					InputStream payloadInputStream = Files.newInputStream(responseFile);
					ResponseWriter rw = new ResponseWriter(out);) {

				rw.writeBeginDocument();
				rw.writeBeginResponse();
				rw.write(sample.getQueryInfo());
				rw.writeBeginResultSet();
				for (Map<Integer, CipherText> group : sample.getResponseElements()) {
					for (Map.Entry<Integer, CipherText> entry : group.entrySet()) {
						rw.writeResponseItem(entry.getKey(), entry.getValue());
					}
				}
				rw.writeEndResultSet();
				rw.writeEndResponse();
				rw.writeEndDocument();
			}
		}

		validate(outputFile, sample);
	}

	private org.enquery.encryptedquery.data.Response makeSampleResponse() {
		QueryInfo queryInfo = new QueryInfo();
		queryInfo.setIdentifier("query id");
		queryInfo.setQueryName("Test query");
		queryInfo.setPublicKey(publicKey);
		queryInfo.setHashBitSize(8);
		queryInfo.setDataChunkSize(3);
		queryInfo.setCryptoSchemeId(crypto.name());
		queryInfo.setHashKey("hash key");
		queryInfo.setNumBitsPerDataElement(10);
		queryInfo.setNumPartitionsPerDataElement(12);
		queryInfo.setNumSelectors(1);
		queryInfo.setQuerySchema(makeSampleQuerySchema());

		org.enquery.encryptedquery.data.Response result = new org.enquery.encryptedquery.data.Response(queryInfo);
		result.addResponseElements(makeSampleResponseElments());
		return result;
	}

	/**
	 * @return
	 */
	private Map<Integer, CipherText> makeSampleResponseElments() {
		Map<Integer, CipherText> result = new HashMap<>();
		CipherText value = crypto.encryptionOfZero(publicKey);
		result.put(1, value);
		result.put(2, value);
		return result;
	}

	/**
	 * @return
	 */
	private QuerySchema makeSampleQuerySchema() {
		QuerySchema qSchema;
		QuerySchemaElement qseOne;
		QuerySchemaElement qse2;
		QuerySchemaElement qse3;
		QuerySchemaElement qse4;
		QuerySchemaElement qse5;
		QuerySchemaElement qse6;
		QuerySchemaElement qse7;
		QuerySchemaElement qse8;

		DataSchema dSchema;
		DataSchemaElement dseOne;
		DataSchemaElement dse2;
		DataSchemaElement dse3;
		DataSchemaElement dse4;
		DataSchemaElement dse5;
		DataSchemaElement dse6;
		DataSchemaElement dse7;
		DataSchemaElement dse8;

		dseOne = new DataSchemaElement();
		dseOne.setName("intField");
		dseOne.setDataType(FieldType.INT);
		dseOne.setPosition(1);

		dse2 = new DataSchemaElement();
		dse2.setName("longField");
		dse2.setDataType(FieldType.LONG);
		dse2.setPosition(2);

		dse3 = new DataSchemaElement();
		dse3.setName("shortField");
		dse3.setDataType(FieldType.SHORT);
		dse3.setPosition(3);

		dse4 = new DataSchemaElement();
		dse4.setName("fixedStringField");
		dse4.setDataType(FieldType.STRING);
		dse4.setPosition(4);

		dse5 = new DataSchemaElement();
		dse5.setName("variableStringField");
		dse5.setDataType(FieldType.STRING);
		dse5.setPosition(5);

		dse6 = new DataSchemaElement();
		dse6.setName("doubleField");
		dse6.setDataType(FieldType.DOUBLE);
		dse6.setPosition(6);

		dse7 = new DataSchemaElement();
		dse7.setName("variableArrayStringField");
		dse7.setDataType(FieldType.STRING);
		dse7.setPosition(7);

		dse8 = new DataSchemaElement();
		dse8.setName("largeVariableStringField");
		dse8.setDataType(FieldType.STRING);
		dse8.setPosition(8);

		qseOne = new QuerySchemaElement();
		qseOne.setName("intField");
		// qseOne.setLengthType("fixed");
		// qseOne.setSize(4);
		// qseOne.setMaxArrayElements(1);

		qse2 = new QuerySchemaElement();
		qse2.setName("longField");
		// qse2.setLengthType("fixed");
		// qse2.setSize(8);
		// qse2.setMaxArrayElements(1);

		qse3 = new QuerySchemaElement();
		qse3.setName("shortField");
		// qse3.setLengthType("fixed");
		// qse3.setSize(2);
		// qse3.setMaxArrayElements(1);

		qse4 = new QuerySchemaElement();
		qse4.setName("fixedStringField");
		// qse4.setLengthType("fixed");
		// qse4.setSize(16);
		// qse4.setMaxArrayElements(1);

		qse5 = new QuerySchemaElement();
		qse5.setName("variableStringField");
		// qse5.setLengthType("variable");
		// qse5.setSize(128);
		// qse5.setMaxArrayElements(1);

		qse6 = new QuerySchemaElement();
		qse6.setName("doubleField");
		// qse6.setLengthType("fixed");
		// qse6.setSize(8);
		// qse6.setMaxArrayElements(1);

		qse7 = new QuerySchemaElement();
		qse7.setName("variableArrayStringField");
		// qse7.setLengthType("variable");
		// qse7.setSize(15);
		// qse7.setMaxArrayElements(3);

		qse8 = new QuerySchemaElement();
		qse8.setName("largeVariableStringField");
		// qse8.setLengthType("variable");
		// qse8.setSize(54000);
		// qse8.setMaxArrayElements(1);

		dSchema = new DataSchema();
		dSchema.setName("TestDataSchema");
		dSchema.addElement(dseOne);
		dSchema.addElement(dse2);
		dSchema.addElement(dse3);
		dSchema.addElement(dse4);
		dSchema.addElement(dse5);
		dSchema.addElement(dse6);
		dSchema.addElement(dse7);
		dSchema.addElement(dse8);

		qSchema = new QuerySchema();
		qSchema.setName("TestQuerySchema");
		qSchema.setDataSchema(dSchema);
		qSchema.setSelectorField("intField");
		qSchema.getElementList().add(qseOne);
		qSchema.getElementList().add(qse2);
		qSchema.getElementList().add(qse3);
		qSchema.getElementList().add(qse4);
		qSchema.getElementList().add(qse5);
		qSchema.getElementList().add(qse6);
		qSchema.getElementList().add(qse7);
		qSchema.getElementList().add(qse8);

		return qSchema;
	}

	/**
	 * @param original
	 * @param resultF
	 * @throws IOException
	 * @throws JAXBException
	 */
	private void validate(Path file, org.enquery.encryptedquery.data.Response original) throws IOException, JAXBException {
		try (InputStream inputStream = Files.newInputStream(file)) {
			org.enquery.encryptedquery.data.Response result = converter.toCore(converter.unmarshal(inputStream));
			result.getQueryInfo().printQueryInfo();

			assertEquals(original.getQueryInfo(), result.getQueryInfo());
			validate(original.getResponseElements(), result.getResponseElements());
		}
	}

	/**
	 * @param expected
	 * @param actual
	 */
	private void validate(List<Map<Integer, CipherText>> expected, List<Map<Integer, CipherText>> actual) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); ++i) {
			Map<Integer, CipherText> expectedMap = expected.get(i);
			Map<Integer, CipherText> actualMap = actual.get(i);
			validate(expectedMap, actualMap);
		}
	}

	/**
	 * @param expectedMap
	 * @param actualMap
	 */
	private void validate(Map<Integer, CipherText> expectedMap, Map<Integer, CipherText> actualMap) {
		assertEquals(expectedMap.size(), actualMap.size());
		for (Entry<Integer, CipherText> entry : expectedMap.entrySet()) {
			CipherText expected = entry.getValue();
			CipherText actual = actualMap.get(entry.getKey());
			assertArrayEquals(expected.toBytes(), actual.toBytes());
		}
	}
}
