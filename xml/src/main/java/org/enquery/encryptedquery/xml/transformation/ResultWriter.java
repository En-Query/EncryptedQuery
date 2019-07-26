package org.enquery.encryptedquery.xml.transformation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.xml.schema.Resource;
import org.enquery.encryptedquery.xml.schema.ResultResource;

public class ResultWriter implements Closeable {

	private static final String RESULT_NS = "http://enquery.net/encryptedquery/result";
	private static final String RESOURCE_NS = "http://enquery.net/encryptedquery/resource";
	private static final String RESPONSE_NS = "http://enquery.net/encryptedquery/response";
	private static final QName RESULT_RESOURCE = new QName(RESULT_NS, "resultResource");
	private static final QName ID = new QName(RESOURCE_NS, "id");
	private static final QName SELF_URI = new QName(RESOURCE_NS, "selfUri");
	private static final QName CREATED_ON = new QName(RESULT_NS, "createdOn");

	private static final QName WINDOW_START = new QName(RESULT_NS, "windowStart");
	private static final QName WINDOW_END = new QName(RESULT_NS, "windowEnd");
	private static final QName PAYLOAD = new QName(RESULT_NS, "payload");

	private static final QName EXECUTION_RESOURCE = new QName(RESULT_NS, "execution");

	private static final QName RESPONSE = new QName(RESPONSE_NS, "response");
	private static final String SCHEMA_VERSION_ATTRIB = "schemaVersion";
	// needs to match version attribute in the XSD resource (most current version)
	private static final String RESPONSE_CURRENT_XSD_VERSION = "2.0";


	private final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

	private BufferedWriter bufferedWriter;
	private OutputStreamWriter streamWriter;
	private XMLEventWriter writer;
	private boolean ownWriter;
	private boolean emitExecution = true;

	public ResultWriter(OutputStream out) throws XMLStreamException {
		Validate.notNull(out);

		streamWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
		bufferedWriter = new BufferedWriter(streamWriter);
		writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(bufferedWriter);
		ownWriter = true;
	}

	public ResultWriter(XMLEventWriter writer) throws XMLStreamException {
		Validate.notNull(writer);
		this.writer = writer;
	}

	public boolean isEmitExecution() {
		return emitExecution;
	}

	public void setEmitExecution(boolean emitExecution) {
		this.emitExecution = emitExecution;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		if (!ownWriter) return;

		Exception error = null;

		if (writer != null) try {
			writer.flush();
			writer.close();
		} catch (XMLStreamException e) {
			error = e;
		}
		try {
			if (bufferedWriter != null) bufferedWriter.close();
		} catch (Exception e) {
			if (error == null) error = e;
		}
		try {
			if (streamWriter != null) streamWriter.close();
		} catch (Exception e) {
			if (error == null) error = e;
		}

		if (error != null) throw new IOException("Error during close", error);
	}

	public void writeResult(ResultResource result, InputStream payloadInputStream, boolean isRoot) throws XMLStreamException, IOException {
		if (isRoot) emitBeginDocument();

		writer.add(eventFactory.createStartElement(RESULT_RESOURCE, null, null));

		emitElement(ID, Integer.toString(result.getId()));
		emitElement(SELF_URI, result.getSelfUri());
		emitElement(CREATED_ON, result.getCreatedOn().toXMLFormat());

		if (result.getWindowStart() != null) {
			emitElement(WINDOW_START, result.getWindowStart().toXMLFormat());
		}

		if (result.getWindowEnd() != null) {
			emitElement(WINDOW_END, result.getWindowEnd().toXMLFormat());
		}

		if (emitExecution) {
			emitExecution(result.getExecution());
		}

		emitPayload(payloadInputStream);

		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(RESULT_RESOURCE, null));

		if (isRoot) emitEndDocument();
	}

	private void emitExecution(Resource execution) throws XMLStreamException, IOException {
		// execution is optional
		if (execution == null) return;

		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(EXECUTION_RESOURCE, null, null));
		emitElement(ID, Integer.toString(execution.getId()));
		emitElement(SELF_URI, execution.getSelfUri());

		writer.add(eventFactory.createEndElement(EXECUTION_RESOURCE, null));
	}

	private void emitPayload(InputStream payloadInputStream) throws XMLStreamException, IOException {
		if (payloadInputStream == null) return;

		XMLEventReader reader = null;
		try (InputStreamReader isr = new InputStreamReader(payloadInputStream);
				BufferedReader br = new BufferedReader(isr);) {

			reader = XMLFactories.xmlInputFactory.createXMLEventReader(br);
			StartElement start = nextStartElement(reader);
			Validate.notNull(start);
			Validate.isTrue(RESPONSE.equals(start.getName()));

			writer.add(eventFactory.createIgnorableSpace("\n"));
			writer.add(eventFactory.createStartElement(PAYLOAD, null, null));
			writer.add(eventFactory.createAttribute(SCHEMA_VERSION_ATTRIB, RESPONSE_CURRENT_XSD_VERSION));
			while (reader.hasNext()) {
				XMLEvent event = reader.nextEvent();
				if (event.isEndElement() &&
						RESPONSE.equals(event.asEndElement().getName())) {
					break;
				}
				writer.add(event);
			}

			writer.add(eventFactory.createIgnorableSpace("\n"));
			writer.add(eventFactory.createEndElement(PAYLOAD, null));
		} finally {
			if (reader != null) reader.close();
		}
	}

	private void emitElement(QName element, String value) throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(element, null, null));
		writer.add(eventFactory.createCharacters(value));
		writer.add(eventFactory.createEndElement(element, null));
	}

	private void emitBeginDocument() throws XMLStreamException {
		writer.add(eventFactory.createStartDocument("UTF-8"));
		writer.setDefaultNamespace(RESULT_NS);
		writer.add(eventFactory.createIgnorableSpace("\n"));
	}

	private void emitEndDocument() throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndDocument());
	}

	private StartElement nextStartElement(XMLEventReader reader) throws XMLStreamException {
		while (reader.hasNext()) {
			XMLEvent event = reader.nextEvent();
			if (event.isStartElement()) {
				StartElement startElement = event.asStartElement();
				return startElement;
			}
		}
		return null;
	}
}
