package org.enquery.encryptedquery.xml.transformation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.enquery.encryptedquery.xml.schema.DataSchema;
import org.enquery.encryptedquery.xml.schema.QueryInfo;
import org.enquery.encryptedquery.xml.schema.QuerySchema;
import org.enquery.encryptedquery.xml.schema.QuerySchema.Field;
import org.junit.Test;

public class QueryReaderTest {

	@Test
	public void parseQuery() throws FileNotFoundException, IOException, Exception {

		try (InputStream fis = new FileInputStream("src/test/resources/query.xml");
				QueryReader reader = new QueryReader();) {

			reader.parse(fis);

			QueryInfo queryInfo = reader.getQueryInfo();
			assertNotNull(queryInfo);
			assertEquals("ad547ad7-cf18-4a94-8d02-09ce95ba86d7", queryInfo.getQueryId());
			assertEquals("Books", queryInfo.getQueryName());
			assertEquals("Paillier", queryInfo.getCryptoSchemeId());
			assertEquals("eyJu", Base64.encodeBase64String(queryInfo.getPublicKey()));
			assertEquals(1, (int) queryInfo.getNumSelectors());
			assertEquals(9, queryInfo.getHashBitSize());
			assertEquals("f47dae006b08cf4ebd0c", queryInfo.getHashKey());
			assertEquals(1, queryInfo.getDataChunkSize());
			assertEquals("price > 10", queryInfo.getFilterExpression());

			QuerySchema querySchema = queryInfo.getQuerySchema();
			assertNotNull(querySchema);
			assertEquals("Books", querySchema.getName());
			assertEquals("title", querySchema.getSelectorField());
			assertEquals(2, querySchema.getField().size());

			Field field1 = querySchema.getField().get(0);
			assertEquals("price", field1.getName());
			assertNull(field1.getSize());
			assertNull(field1.getMaxArrayElements());

			Field field2 = querySchema.getField().get(1);
			assertEquals("title", field2.getName());
			assertEquals(12, (int) field2.getSize());
			assertEquals(2, (int) field2.getMaxArrayElements());

			DataSchema dataSchema = querySchema.getDataSchema();
			assertNotNull(dataSchema);
			assertEquals("Books", dataSchema.getName());
			assertEquals(5, dataSchema.getField().size());

			validateFields(dataSchema.getField());
		}


	}

	/**
	 * @param field
	 */
	private void validateFields(List<org.enquery.encryptedquery.xml.schema.DataSchema.Field> fields) {
		org.enquery.encryptedquery.xml.schema.DataSchema.Field field = fields.get(0);
		assertEquals("id", field.getName());
		assertEquals("int", field.getDataType());
		assertEquals(0, field.getPosition());

		field = fields.get(1);
		assertEquals("title", field.getName());
		assertEquals("string", field.getDataType());
		assertEquals(1, field.getPosition());

		field = fields.get(2);
		assertEquals("author", field.getName());
		assertEquals("string", field.getDataType());
		assertEquals(2, field.getPosition());

		field = fields.get(3);
		assertEquals("price", field.getName());
		assertEquals("double", field.getDataType());
		assertEquals(3, field.getPosition());

		field = fields.get(4);
		assertEquals("qty", field.getName());
		assertEquals("int", field.getDataType());
		assertEquals(4, field.getPosition());
	}

}
