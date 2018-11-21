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
	private static final QName ENTRY = new QName(EXECUTION_NAMESPACE, "entry");
	private static final QName KEY_ATTR = new QName(null, "key");
	private static final QName VALUE_ATTR = new QName(null, "value");

	private Date scheduleDate;
	private InputStream queryInputStream;
	private ExecutorService threadPool;
	private Future<?> queryStreamerFuture;
	private Map<String, String> config;

	public ExecutionXMLExtractor(ExecutorService threadPool) {
		Validate.notNull(threadPool);
		this.threadPool = threadPool;
	}

	public void parse(InputStream inputStream) throws IOException {
		Validate.notNull(inputStream);
		try {
			XMLEventReader reader = XMLFactories.xmlInputFactory.createXMLEventReader(inputStream);
			// the order in which we parse these elements is the order they appear in the XML
			// for a forward-only processing
			seekToRootNode(reader);
			scheduleDate = parseScheduleDate(reader);
			config = parseConfig(reader);
			queryInputStream = parseQuery(reader);
		} catch (Exception e) {
			throw new IOException("Error parsing xml", e);
		}
	}

	private void seekToRootNode(XMLEventReader reader) throws XMLStreamException {
		StartElement startElement = nextElement(reader);
		Validate.isTrue(EXECUTION.equals(startElement.getName()));
	}

	private Map<String, String> parseConfig(XMLEventReader reader) throws XMLStreamException {
		skipCharacters(reader);

		XMLEvent peek = reader.peek();
		// configuration node is optional
		if (!isConfigNode(peek)) return null;

		Map<String, String> result = new HashMap<>();
		StartElement element = nextElement(reader);
		Validate.isTrue(isConfigNode(element));
		parseConfigEntries(reader, result);

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

	private boolean isConfigNode(XMLEvent event) {
		if (!event.isStartElement()) return false;
		return CONFIGURATION.equals(event.asStartElement().getName());
	}

	private boolean isScheduledDate(StartElement startElement) {
		return SCHEDULED_FOR.equals(startElement.getName());
	}

	private Date parseScheduleDate(XMLEventReader reader) throws XMLStreamException {
		Date result = null;
		StartElement startElement = nextElement(reader);

		if (isScheduledDate(startElement)) {
			XMLGregorianCalendar cal = XMLFactories.dtf.newXMLGregorianCalendar(reader.getElementText());
			result = toLocalTime(cal);
		}

		return result;
	}


	private InputStream parseQuery(XMLEventReader inputStream) throws IOException {
		PipedOutputStream out = new PipedOutputStream();
		PipedInputStream in = new PipedInputStream(out);
		QueryXMLStreamer qs = new QueryXMLStreamer(inputStream, out);
		queryStreamerFuture = threadPool.submit(qs);
		return in;
	}

	private static Date toLocalTime(XMLGregorianCalendar c) {
		if (c == null) return null;
		return c.toGregorianCalendar().getTime();
	}

	public Date getScheduleDate() {
		return scheduleDate;
	}

	public InputStream getQueryInputStream() {
		return queryInputStream;
	}

	public Map<String, String> getConfig() {
		return config;
	}

	@Override
	public void close() throws IOException {
		if (queryInputStream != null) queryInputStream.close();
		if (queryStreamerFuture != null) {
			queryStreamerFuture.cancel(true);
		}
	}

	private StartElement nextElement(XMLEventReader reader) throws XMLStreamException {
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

}
