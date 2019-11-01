package org.enquery.encryptedquery.xml.transformation;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.encryption.CipherText;

/**
 * Incrementally writing of response file
 */
public class ResponseWriter implements Closeable {

	private static final String RESPONSE_NS = "http://enquery.net/encryptedquery/response";
	private static final QName RESPONSE = new QName(RESPONSE_NS, "response");
	private static final QName QUERY_INFO = new QName(RESPONSE_NS, "queryInfo");
	private static final QName RESULT = new QName(RESPONSE_NS, "result");
	private static final String COLUMN = "column";
	private static final String VALUE = "value";
	private static final QName RESULT_SET = new QName(RESPONSE_NS, "resultSet");
	private static final String SCHEMA_VERSION_ATTRIB = "schemaVersion";
	// needs to match version attribute in the XSD resource (most current version)
	private static final String RESPONSE_CURRENT_XSD_VERSION = "2.0";

	private final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

	private XMLEventWriter writer;
	private boolean closeStream = true;

	public ResponseWriter(OutputStream out, boolean closeStream) throws XMLStreamException {
		this(out);
		this.closeStream = closeStream;
	}


	public ResponseWriter(OutputStream out) throws XMLStreamException {
		Validate.notNull(out);

		writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(
				new BufferedWriter(
						new OutputStreamWriter(out, StandardCharsets.UTF_8)));

		writer.setPrefix("resp", RESPONSE_NS);
	}

	public ResponseWriter(XMLEventWriter writer) throws XMLStreamException {
		Validate.notNull(writer);
		this.writer = writer;
		closeStream = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		if (writer != null) try {
			writer.flush();
			if (closeStream) {
				writer.close();
			}
			writer = null;
		} catch (XMLStreamException e) {
			throw new IOException("Error during close.", e);
		}
	}

	public void writeBeginResponse() throws XMLStreamException, IOException {
		writer.add(eventFactory.createStartElement(RESPONSE, null, null));
		writer.add(eventFactory.createAttribute(SCHEMA_VERSION_ATTRIB, RESPONSE_CURRENT_XSD_VERSION));
	}

	public void writeEndResponse() throws XMLStreamException, IOException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(RESPONSE, null));
	}

	public void write(QueryInfo queryInfo) throws XMLStreamException, IOException {

		writer.add(eventFactory.createStartElement(QUERY_INFO, null, null));

		writeElement(QueryNames.QUERY_ID, queryInfo.getIdentifier());
		writeElement(QueryNames.QUERY_NAME, queryInfo.getQueryName());
		writeElement(QueryNames.CRYPTO_SCHEME_ID, queryInfo.getCryptoSchemeId());
		writeElement(QueryNames.PUBLIC_KEY, queryInfo.getPublicKey().getEncoded());
		writeElement(QueryNames.NUM_SELECTORS, queryInfo.getNumSelectors());
		writeElement(QueryNames.HASH_BIT_SIZE, queryInfo.getHashBitSize());
		writeElement(QueryNames.HASH_KEY, queryInfo.getHashKey());
		writeElement(QueryNames.DATA_CHUNK_SIZE, queryInfo.getDataChunkSize());

		write(queryInfo.getQuerySchema());

		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(QUERY_INFO, null));
	}


	/**
	 * @param publicKey
	 * @param encoded
	 * @throws XMLStreamException
	 */
	private void writeElement(QName element, byte[] value) throws XMLStreamException {
		if (value == null) return;
		writeElement(element, Base64.encodeBase64String(value));
	}

	/**
	 * @param querySchema
	 * @throws XMLStreamException
	 */
	private void write(QuerySchema querySchema) throws XMLStreamException {
		if (querySchema == null) return;

		writer.add(eventFactory.createStartElement(QuerySchemaNames.QUERY_SCHEMA, null, null));

		writeElement(QuerySchemaNames.NAME, querySchema.getName());
		writeElement(QuerySchemaNames.SELECTOR_FIELD, querySchema.getSelectorField());
		writeQuerySchemaElements(querySchema.getElementList());

		write(querySchema.getDataSchema());

		writer.add(eventFactory.createEndElement(QuerySchemaNames.QUERY_SCHEMA, null));
	}

	/**
	 * @param dataSchema
	 * @throws XMLStreamException
	 */
	private void write(DataSchema dataSchema) throws XMLStreamException {
		if (dataSchema == null) return;
		writer.add(eventFactory.createStartElement(DataSchemaNames.DATA_SCHEMA, null, null));

		writeElement(DataSchemaNames.NAME, dataSchema.getName());
		write(dataSchema.elements());
		writer.add(eventFactory.createEndElement(DataSchemaNames.DATA_SCHEMA, null));
	}

	/**
	 * @param elements
	 * @throws XMLStreamException
	 */
	private void write(List<DataSchemaElement> elements) throws XMLStreamException {
		for (DataSchemaElement el : elements) {
			writeDataSchemaField(el);
		}
	}

	/**
	 * @param el
	 * @throws XMLStreamException
	 */
	private void writeDataSchemaField(DataSchemaElement el) throws XMLStreamException {
		writer.add(eventFactory.createStartElement(DataSchemaNames.FIELD, null, null));
		writeElement(DataSchemaNames.NAME, el.getName());
		writeElement(DataSchemaNames.DATA_TYPE, el.getDataType().toString());
		// writeElement(DataSchemaNames.IS_ARRAY, el.getIsArray());
		writeElement(DataSchemaNames.POSITION, el.getPosition());
		writer.add(eventFactory.createEndElement(DataSchemaNames.FIELD, null));
	}

	/**
	 * @param k
	 * @param v
	 * @throws XMLStreamException
	 */
	public void writeResponseItem(Integer column, CipherText value) throws XMLStreamException {
		final String encoded = Base64.encodeBase64String(value.toBytes());
		writeResponseItem(column, encoded);
	}

	public void writeResponseItem(Integer column, String encoded) throws XMLStreamException {
		writer.add(eventFactory.createStartElement(RESULT, null, null));
		writer.add(eventFactory.createAttribute(COLUMN, Integer.toString(column)));
		writer.add(eventFactory.createAttribute(VALUE, encoded));
		writer.add(eventFactory.createEndElement(RESULT, null));
		writer.add(eventFactory.createIgnorableSpace("\n"));
	}

	/**
	 * @throws XMLStreamException
	 * 
	 */
	public void writeBeginResultSet() throws XMLStreamException {
		writer.add(eventFactory.createStartElement(RESULT_SET, null, null));
	}

	/**
	 * @throws XMLStreamException
	 * 
	 */
	public void writeEndResultSet() throws XMLStreamException {
		writer.add(eventFactory.createEndElement(RESULT_SET, null));
	}

	private void writeQuerySchemaElements(List<QuerySchemaElement> elements) throws XMLStreamException {
		for (QuerySchemaElement el : elements) {
			writeQuerySchemaElement(el);
		}
	}

	/**
	 * @param el
	 * @throws XMLStreamException
	 */
	private void writeQuerySchemaElement(QuerySchemaElement el) throws XMLStreamException {
		writer.add(eventFactory.createStartElement(QuerySchemaNames.FIELD, null, null));
		writeElement(QuerySchemaNames.NAME, el.getName());
		// writeElement(QuerySchemaNames.LENGTH_TYPE, el.getLengthType());
		writeElement(QuerySchemaNames.SIZE, el.getSize());
		writeElement(QuerySchemaNames.MAX_ARRAY_ELEMENTS, el.getMaxArrayElements());
		writer.add(eventFactory.createEndElement(QuerySchemaNames.FIELD, null));
	}

	/**
	 * @param size
	 * @param size2
	 * @throws XMLStreamException
	 */
	private void writeElement(QName element, Integer value) throws XMLStreamException {
		if (value == null) return;
		writeElement(element, value.toString());
	}

	private void writeElement(QName element, String value) throws XMLStreamException {
		if (value == null) return;
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(element, null, null));
		writer.add(eventFactory.createCharacters(value));
		writer.add(eventFactory.createEndElement(element, null));
	}

	public void writeBeginDocument() throws XMLStreamException {
		writer.add(eventFactory.createStartDocument("UTF-8"));
		writer.setDefaultNamespace(RESPONSE_NS);
		writer.add(eventFactory.createIgnorableSpace("\n"));
	}

	public void writeEndDocument() throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndDocument());
	}
}
