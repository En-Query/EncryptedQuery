package org.enquery.encryptedquery.xml.transformation;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.concurrent.ExecutorService;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResultXMLExtractor implements Closeable {

	private static final Logger log = LoggerFactory.getLogger(ResultXMLExtractor.class);

	private static final String RESULT_NS = "http://enquery.net/encryptedquery/result";
	private static final String RESOURCE_NS = "http://enquery.net/encryptedquery/resource";
	private static final String RESPONSE_NS = "http://enquery.net/encryptedquery/response";
	private static final QName ID = new QName(RESOURCE_NS, "id");
	private static final QName URI = new QName(RESOURCE_NS, "selfUri");
	private static final QName RESULT_RESOURCE = new QName(RESULT_NS, "resultResource");
	private static final QName CREATED_ON = new QName(RESULT_NS, "createdOn");
	private static final QName PAYLOAD = new QName(RESULT_NS, "payload");
	private static final QName EXECUTION = new QName(RESULT_NS, "execution");
	private static final QName RESPONSE = new QName(RESPONSE_NS, "response");
	private static final String SCHEMA_VERSION_ATTRIB = "schemaVersion";

	// needs to match the version attribute in the response.xsd
	private static final String CURRENT_RESPONSE_VERSION = "2.0";


	private ExecutorService threadPool;
	private int resultId;
	private String resultUri;
	private Date creationDate;
	private InputStream payloadInputStream;
	private int executionId;
	private String executionUri;

	public ResultXMLExtractor(ExecutorService threadPool) {
		Validate.notNull(threadPool);
		this.threadPool = threadPool;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public InputStream getResponseInputStream() {
		return payloadInputStream;
	}


	public int getResultId() {
		return resultId;
	}

	public String getResultUri() {
		return resultUri;
	}

	public int getExecutionId() {
		return executionId;
	}

	public String getExecutionUri() {
		return executionUri;
	}

	public void parse(InputStream inputStream) throws IOException {
		Validate.notNull(inputStream);
		try {
			XMLEventReader reader = XMLFactories.xmlInputFactory.createXMLEventReader(inputStream);
			// the order in which we parse these elements is the
			// order they are defined in the corresponding XML Schema
			// for a forward-only processing
			seekToRootNode(reader);
			resultId = parseId(reader);
			resultUri = parseUri(reader);
			creationDate = parseScheduleDate(reader);
			skipStartElement(reader, EXECUTION);
			executionId = parseId(reader);
			executionUri = parseUri(reader);
			skipEndElement(reader, EXECUTION);
			payloadInputStream = parsePayload(reader);
		} catch (XMLStreamException e) {
			log.error("Error parsing XML", e);
			throw new IOException("Error parsing xml", e);
		}
	}

	private void skipEndElement(XMLEventReader reader, QName qName) throws XMLStreamException {
		skipCharacters(reader);
		Validate.isTrue(reader.hasNext());
		XMLEvent event = reader.nextEvent();
		Validate.isTrue(event.isEndElement());
		EndElement endElement = event.asEndElement();
		Validate.isTrue(qName.equals(endElement.getName()));
	}

	private String parseUri(XMLEventReader reader) throws XMLStreamException {
		return extractElementText(reader, URI);
	}

	private int parseId(XMLEventReader reader) throws XMLStreamException {
		return Integer.parseInt(extractElementText(reader, ID));
	}

	private String extractElementText(XMLEventReader reader, QName qName) throws XMLStreamException {
		skipStartElement(reader, qName);
		return reader.getElementText();
	}

	private void seekToRootNode(XMLEventReader reader) throws XMLStreamException {
		skipStartElement(reader, RESULT_RESOURCE);
	}

	private void skipStartElement(XMLEventReader reader, QName qName) throws XMLStreamException {
		StartElement element = nextStartElement(reader);
		Validate.isTrue(qName.equals(element.getName()));
	}

	private Date parseScheduleDate(XMLEventReader reader) throws XMLStreamException {
		return XMLFactories.toLocalTime(
				XMLFactories.dtf.newXMLGregorianCalendar(
						extractElementText(reader, CREATED_ON)));
	}

	private InputStream parsePayload(XMLEventReader reader) throws IOException, XMLStreamException {
		skipCharacters(reader);

		// test if the next element is the Payload element
		// Payload is optional.
		XMLEvent peek = reader.peek();
		if (peek == null) return null;
		if (!peek.isStartElement()) return null;
		if (!PAYLOAD.equals(peek.asStartElement().getName())) return null;

		PipedOutputStream out = new PipedOutputStream();
		PipedInputStream in = new PipedInputStream(out);
		threadPool.submit(() -> writePayload(reader, out));
		return in;
	}

	private void skipCharacters(XMLEventReader reader) throws XMLStreamException {
		while (reader.hasNext()) {
			XMLEvent event = reader.peek();
			if (!event.isCharacters()) break;
			reader.nextEvent();
		}
	}

	private void writePayload(XMLEventReader reader, OutputStream outputStream) {
		final boolean debugging = log.isDebugEnabled();

		if (debugging) log.debug("begin parsing payload");

		try (OutputStreamWriter osw = new OutputStreamWriter(outputStream);
				BufferedWriter bw = new BufferedWriter(osw)) {

			final XMLEventWriter writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(bw);
			try {
				StartElement start = nextStartElement(reader);
				Validate.notNull(start);
				Validate.isTrue(PAYLOAD.equals(start.getName()));

				writer.add(XMLFactories.eventFactory.createStartDocument());
				writer.add(XMLFactories.eventFactory.createIgnorableSpace("\n"));
				writer.add(XMLFactories.eventFactory.createStartElement(RESPONSE, null, null));
				writer.add(XMLFactories.eventFactory.createAttribute(SCHEMA_VERSION_ATTRIB, CURRENT_RESPONSE_VERSION));
				while (reader.hasNext()) {
					XMLEvent event = reader.nextEvent();
					if (event.isEndElement() &&
							PAYLOAD.equals(event.asEndElement().getName())) {
						break;
					}
					writer.add(event);
				}
				writer.add(XMLFactories.eventFactory.createIgnorableSpace("\n"));
				writer.add(XMLFactories.eventFactory.createEndElement(RESPONSE, null));
				writer.add(XMLFactories.eventFactory.createEndDocument());

			} finally {
				writer.close();
			}
			reader.close();

			if (debugging) log.debug("end parsing payload");
		} catch (IOException | XMLStreamException e) {
			e.printStackTrace();
			throw new RuntimeException("Error writing Execution to stream.", e);
		}
	}

	@Override
	public void close() throws IOException {
		if (payloadInputStream != null) payloadInputStream.close();
		// if (streamerFuture != null) {
		// streamerFuture.cancel(true);
		// }
	}

	private StartElement nextStartElement(XMLEventReader reader) throws XMLStreamException {
		final boolean isDebugging = log.isDebugEnabled();

		while (reader.hasNext()) {
			XMLEvent event = reader.nextEvent();
			if (event.isStartElement()) {
				StartElement startElement = event.asStartElement();
				if (isDebugging) log.debug("Found start element '{}'.", startElement.getName().getLocalPart());

				return startElement;
			}
		}
		return null;
	}

}
