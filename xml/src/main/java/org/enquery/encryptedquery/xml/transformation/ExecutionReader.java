package org.enquery.encryptedquery.xml.transformation;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.xml.Versions;

/**
 * Parsing of an Execution in stream mode for memory efficiency
 */
public class ExecutionReader extends BaseReader {

	private static final String EXECUTION_NAMESPACE = "http://enquery.net/encryptedquery/execution";
	private static final QName EXECUTION = new QName(EXECUTION_NAMESPACE, "execution");
	private static final QName SCHEDULED_FOR = new QName(EXECUTION_NAMESPACE, "scheduledFor");
	private static final QName UUID = new QName(EXECUTION_NAMESPACE, "uuid");
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
	private String uuid;

	private InputStream queryInputStream;
	private ExecutorService threadPool;
	private Future<?> queryStreamerFuture;
	private Map<String, String> config;

	public ExecutionReader(ExecutorService threadPool) {
		Validate.notNull(threadPool);
		this.threadPool = threadPool;
	}

	public String getUUId() {
		return uuid;
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
			reader = XMLFactories.xmlInputFactory.createXMLEventReader(inputStream);
			ownsReader = true;
			parse();
		} catch (XMLStreamException e) {
			throw new IOException("Error parsing xml", e);
		}
	}

	public void parse(XMLEventReader reader) throws XMLStreamException, IOException {
		this.reader = reader;
		ownsReader = false;
		parse();
	}

	public void parse() throws XMLStreamException, IOException {
		// the order in which we parse these elements is the order they appear in the XML
		// for a forward-only processing
		StartElement element = skipStartElement(EXECUTION);
		validateVersion(element, Versions.EXECUTION);

		uuid = parseString(UUID, true);

		scheduleDate = parseDate(SCHEDULED_FOR);
		Validate.notNull(scheduleDate, "'%s' is missing", SCHEDULED_FOR);

		config = parseConfig();
		submittedOn = parseDate(SUBMITTED_ON);
		startedOn = parseDate(STARTED_ON);
		completedOn = parseDate(COMPLETED_ON);
		errorMessage = parseString(ERROR_MSG, false);
		cancelled = parseBoolean(CANCELLED, false);

		queryInputStream = streamQuery();
	}

	private Map<String, String> parseConfig() throws XMLStreamException {
		if (!nextStartElementIs(CONFIGURATION)) return null;
		skipStartElement(CONFIGURATION);

		Map<String, String> result = new HashMap<>();
		parseConfigEntries(result);

		skipEndElement(CONFIGURATION);
		return result;
	}

	private void parseConfigEntries(Map<String, String> configMap) throws XMLStreamException {
		while (nextStartElementIs(ENTRY)) {
			parseConfigEntry(configMap);
		}
	}

	private void parseConfigEntry(Map<String, String> configMap) throws XMLStreamException {
		StartElement element = skipStartElement(ENTRY);
		Attribute key = element.getAttributeByName(KEY_ATTR);
		Attribute value = element.getAttributeByName(VALUE_ATTR);
		configMap.put(key.getValue(), value.getValue());
		// consume the node
		reader.getElementText();
	}

	private InputStream streamQuery() throws XMLStreamException, IOException {
		if (!nextStartElementIs(QueryStreamer.QUERY)) return null;


		// if there is Query, then stream it
		PipedOutputStream out = new PipedOutputStream();
		PipedInputStream in = new PipedInputStream(out);
		queryStreamerFuture = threadPool.submit(() -> {
			try (QueryStreamer queryStreamer = new QueryStreamer(reader)) {
				queryStreamer.writeQuery(out);
			} catch (IOException | XMLStreamException e) {
				throw new RuntimeException("Error streaming Query.", e);
			}
		});
		return in;
	}



	@Override
	public void close() throws IOException {
		if (queryStreamerFuture != null) {
			try {
				queryStreamerFuture.get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}

		try {
			if (reader != null) {
				// consume the end Execution tag
				skipEndElement(EXECUTION);

				if (ownsReader) {
					reader.close();
				}

			}

		} catch (XMLStreamException e) {
			throw new IOException("Error closing reader.", e);
		}
	}


}
