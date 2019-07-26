package org.enquery.encryptedquery.xml.transformation;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.DataSchema;
import org.enquery.encryptedquery.xml.schema.DataSchema.Field;
import org.enquery.encryptedquery.xml.schema.QueryInfo;
import org.enquery.encryptedquery.xml.schema.QuerySchema;

public class QueryReader extends BaseReader implements QueryNames {


	private QueryInfo queryInfo;


	public QueryReader() {}

	public QueryInfo getQueryInfo() {
		return queryInfo;
	}

	public void parse(InputStream inputStream) throws IOException, XMLStreamException {
		Validate.notNull(inputStream);
		makeReader(inputStream);
		parse();
	}

	public void parse(XMLEventReader reader) throws IOException, XMLStreamException {
		useReader(reader);
		parse();
	}

	/**
	 * @throws IOException
	 * 
	 */
	private void parse() throws IOException, XMLStreamException {
		StartElement query = skipStartElement(QUERY);
		validateVersion(query, Versions.QUERY);

		skipStartElement(QUERY_INFO);
		queryInfo = new QueryInfo();

		queryInfo.setQueryId(parseString(QUERY_ID, true));
		queryInfo.setQueryName(parseString(QUERY_NAME, true));
		queryInfo.setCryptoSchemeId(parseString(CRYPTO_SCHEME_ID, true));
		queryInfo.setPublicKey(parseBase64(PUBLIC_KEY, true));
		queryInfo.setNumSelectors(parseInteger(NUM_SELECTORS, true));
		queryInfo.setHashBitSize(parseInteger(HASH_BIT_SIZE, true));
		queryInfo.setHashKey(parseString(HASH_KEY, true));
		queryInfo.setNumBitsPerDataElement(parseInteger(NUM_BITS_PER_DATA_ELEMENT, true));
		queryInfo.setDataChunkSize(parseInteger(DATA_CHUNK_SIZE, true));
		queryInfo.setNumPartitionsPerDataElement(parseInteger(NUM_PARTITIONS_PER_DATA_ELEMENT, true));
		// queryInfo.setEmbedSelector(parseBoolean(EMBED_SELECTOR, true));
		queryInfo.setQuerySchema(parseQuerySchema());
	}

	/**
	 * @return
	 * @throws XMLStreamException
	 */
	private QuerySchema parseQuerySchema() throws XMLStreamException {
		QuerySchema result = new QuerySchema();

		skipStartElement(QuerySchemaNames.QUERY_SCHEMA);

		result.setName(parseString(QuerySchemaNames.NAME, true));
		result.setSelectorField(parseString(QuerySchemaNames.SELECTOR_FIELD, true));

		while (nextStartElementIs(QuerySchemaNames.FIELD)) {
			skipStartElement(QuerySchemaNames.FIELD);

			org.enquery.encryptedquery.xml.schema.QuerySchema.Field f = //
					new org.enquery.encryptedquery.xml.schema.QuerySchema.Field();

			f.setName(parseString(QuerySchemaNames.NAME, true));
			f.setSize(parseInteger(QuerySchemaNames.SIZE, false));
			f.setMaxArrayElements(parseInteger(QuerySchemaNames.MAX_ARRAY_ELEMENTS, false));

			result.getField().add(f);
			skipEndElement(QuerySchemaNames.FIELD);
		}

		result.setDataSchema(parseDataSchema());

		return result;
	}

	/**
	 * @return
	 * @throws XMLStreamException
	 */
	private DataSchema parseDataSchema() throws XMLStreamException {
		skipStartElement(DataSchemaNames.DATA_SCHEMA);

		DataSchema result = new DataSchema();
		result.setName(parseString(DataSchemaNames.NAME, true));
		while (nextStartElementIs(DataSchemaNames.FIELD)) {
			skipStartElement(DataSchemaNames.FIELD);

			Field f = new Field();
			f.setName(parseString(DataSchemaNames.NAME, true));
			f.setDataType(parseString(DataSchemaNames.DATA_TYPE, true));
			f.setPosition(parseInteger(DataSchemaNames.POSITION, true));
			result.getField().add(f);

			skipEndElement(DataSchemaNames.FIELD);
		}

		skipEndElement(DataSchemaNames.DATA_SCHEMA);

		return result;
	}

}
