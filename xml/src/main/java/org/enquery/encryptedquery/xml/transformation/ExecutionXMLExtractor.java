package org.enquery.encryptedquery.xml.transformation;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionXMLExtractor implements Closeable {

	private static final Logger log = LoggerFactory.getLogger(ExecutionXMLExtractor.class);

	private static final String EXECUTION_NAMESPACE = "http://enquery.net/encryptedquery/execution";
	private static final QName EXECUTION = new QName(EXECUTION_NAMESPACE, "execution");
	private static final QName SCHEDULED_FOR = new QName(EXECUTION_NAMESPACE, "scheduledFor");
	private static final QName CONFIGURATION = new QName(EXECUTION_NAMESPACE, "configuration");
	private static final QName SUBMITTED_ON = new QName(EXECUTION_NAMESPACE, "submittedOn");
	private static final QName STARTED_ON = new QName(EXECUTION_NAMESPACE, "startedOn");
	private static final QName COMPLETED_ON = new QName(EXECUTION_NAMESPACE, "completedOn");
	private static final QName ERROR_MSG = new QName(EXECUTION_NAMESPACE, "errorMessage");
	private static final QName CANCELLED = new QName(EXECUTION_NAMESPACE, "cancelled");

	private static final QName ENTRY = new QName(EXECUTION_NAMESPACE, "entry");
	private static final QName KEY_ATTR = new QName(null, "key");
	private static final QName VALUE_ATTR = new QName(null, "value");

	private Date scheduleDate;
	private Date submittedOn;
	private Date startedOn;
	private Date completedOn;
	private String errorMessage;
	private boolean cancelled;

	private InputStream queryInputStream;
	private ExecutorService threadPool;
	private Future<?> queryStreamerFuture;
	private Map<String, String> config;

	public ExecutionXMLExtractor(ExecutorService threadPool) {
		Validate.notNull(threadPool);
		this.threadPool = threadPool;
	}

	public Date getScheduleDate() {
		return scheduleDate;
	}


	public Map<String, String> getConfig() {
		return config;
	}

	public Date getSubmittedOn() {
		return submittedOn;
	}

	public Date getStartedOn() {
		return startedOn;
	}

	public Date getCompletedOn() {
		return completedOn;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public InputStream getQueryInputStream() {
		return queryInputStream;
	}

	public void parse(InputStream inputStream) throws IOException {
		Validate.notNull(inputStream);
		try {
			XMLEventReader reader = XMLFactories.xmlInputFactory.createXMLEventReader(inputStream);
			// the order in which we parse these elements is the order they appear in the XML
			// for a forward-only processing
			seekToRootNode(reader);
			scheduleDate = parseDate(reader, SCHEDULED_FOR);
			config = parseConfig(reader);
			submittedOn = parseDate(reader, SUBMITTED_ON);
			startedOn = parseDate(reader, STARTED_ON);
			completedOn = parseDate(reader, COMPLETED_ON);
			errorMessage = parseString(reader, ERROR_MSG);
			cancelled = parseBoolean(reader, CANCELLED);

			queryInputStream = parseQuery(reader);
		} catch (Exception e) {
			throw new IOException("Error parsing xml", e);
		}
	}

	/**
	 * @param reader
	 * @param cancelled2
	 * @return
	 * @throws XMLStreamException
	 */
	private boolean parseBoolean(XMLEventReader reader, QName qName) throws XMLStreamException {
		String s = parseString(reader, qName);
		if (s == null) return false;
		return Boolean.parseBoolean(s);
	}

	private void seekToRootNode(XMLEventReader reader) throws XMLStreamException {
		StartElement startElement = nextStartElement(reader);
		Validate.isTrue(EXECUTION.equals(startElement.getName()));
	}

	private Map<String, String> parseConfig(XMLEventReader reader) throws XMLStreamException {
		skipCharacters(reader);

		XMLEvent peek = reader.peek();
		// configuration node is optional
		if (!isStartNode(peek, CONFIGURATION)) return null;

		Map<String, String> result = new HashMap<>();
		nextStartElement(reader);
		parseConfigEntries(reader, result);

		skipCharacters(reader);
		peek = reader.peek();
		if (isEndNode(peek, CONFIGURATION)) skipEndElement(reader);

		return result;
	}

	private void parseConfigEntries(XMLEventReader reader, Map<String, String> configMap) throws XMLStreamException {
		skipCharacters(reader);
		XMLEvent peek = reader.peek();
		while (isEntryNode(peek)) {
			parseConfigEntry(reader, configMap);
			skipCharacters(reader);
			peek = reader.peek();
		}
	}

	private void parseConfigEntry(XMLEventReader reader, Map<String, String> configMap) throws XMLStreamException {
		XMLEvent event = reader.nextEvent();
		Validate.isTrue(isEntryNode(event));
		StartElement element = event.asStartElement();
		Attribute key = element.getAttributeByName(KEY_ATTR);
		Attribute value = element.getAttributeByName(VALUE_ATTR);
		configMap.put(key.getValue(), value.getValue());
		// consume the node
		reader.getElementText();
	}

	private boolean isEntryNode(XMLEvent event) {
		if (!event.isStartElement()) return false;
		return ENTRY.equals(event.asStartElement().getName());
	}

	private Date parseDate(XMLEventReader reader, QName qName) throws XMLStreamException {
		skipCharacters(reader);
		XMLEvent peek = reader.peek();

		// handle optional nodes
		if (!isStartNode(peek, qName)) return null;
		nextStartElement(reader);
		XMLGregorianCalendar cal = XMLFactories.dtf.newXMLGregorianCalendar(reader.getElementText());
		Date result = XMLFactories.toUTCDate(cal);
		log.info("XML Date: {}, in UTC: {}", cal, result);
		return result;
	}

	private boolean isStartNode(XMLEvent event, QName qName) {
		if (!event.isStartElement()) return false;
		return qName.equals(event.asStartElement().getName());
	}

	private boolean isEndNode(XMLEvent event, QName qName) {
		if (!event.isEndElement()) return false;
		return qName.equals(event.asEndElement().getName());
	}

	private String parseString(XMLEventReader reader, QName qName) throws XMLStreamException {
		skipCharacters(reader);
		XMLEvent peek = reader.peek();

		// handle optional nodes
		if (!isStartNode(peek, qName)) return null;
		nextStartElement(reader);
		return reader.getElementText();
	}

	private InputStream parseQuery(XMLEventReader inputStream) throws IOException {
		PipedOutputStream out = new PipedOutputStream();
		PipedInputStream in = new PipedInputStream(out);
		QueryXMLStreamer qs = new QueryXMLStreamer(inputStream, out);
		queryStreamerFuture = threadPool.submit(qs);
		return in;
	}

	@Override
	public void close() throws IOException {
		if (queryInputStream != null) queryInputStream.close();
		if (queryStreamerFuture != null) {
			queryStreamerFuture.cancel(true);
		}
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

	private void skipCharacters(XMLEventReader reader) throws XMLStreamException {
		while (reader.hasNext()) {
			XMLEvent event = reader.peek();
			if (!event.isCharacters()) break;
			reader.nextEvent();
		}
	}

	private void skipEndElement(XMLEventReader reader) throws XMLStreamException {
		while (reader.hasNext()) {
			XMLEvent event = reader.peek();
			if (!event.isEndElement()) break;
			reader.nextEvent();
		}
	}

}
