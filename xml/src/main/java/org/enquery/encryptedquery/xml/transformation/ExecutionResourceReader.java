package org.enquery.encryptedquery.xml.transformation;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.Execution;

/**
 * Parsing of an Execution in stream mode for memory efficiency
 */
public class ExecutionResourceReader extends BaseReader {

	private static final String RESOURCE_NS = "http://enquery.net/encryptedquery/resource";
	private static final QName ID = new QName(RESOURCE_NS, "id");
	private static final QName SELF_URI = new QName(RESOURCE_NS, "selfUri");

	private static final String EXECUTION_NS = "http://enquery.net/encryptedquery/execution";
	private static final QName EXECUTION_RESOURCE = new QName(EXECUTION_NS, "executionResource");
	private static final QName DATA_SOURCE_URI = new QName(EXECUTION_NS, "dataSourceUri");
	private static final QName RESULTS_URI = new QName(EXECUTION_NS, "resultsUri");

	private String dataSourceUri;
	private String resultsUri;

	private ExecutorService threadPool;
	private int id;
	private String selfUri;

	private Execution execution;

	public ExecutionResourceReader(ExecutorService threadPool) {
		Validate.notNull(threadPool);
		this.threadPool = threadPool;
	}

	public String getDataSourceUri() {
		return dataSourceUri;
	}

	public String getResultsUri() {
		return resultsUri;
	}

	public int getId() {
		return id;
	}

	public String getSelfUri() {
		return selfUri;
	}

	public Execution getExecution() {
		return execution;
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

	private void parse() throws XMLStreamException, IOException {
		// the order in which we parse these elements is the order they appear in the XML
		// for a forward-only processing

		StartElement element = skipStartElement(EXECUTION_RESOURCE);
		validateVersion(element, Versions.EXECUTION_RESOURCE);

		String isStr = parseString(ID, true);
		id = Integer.parseInt(isStr);

		selfUri = parseString(SELF_URI, true);
		dataSourceUri = parseString(DATA_SOURCE_URI, true);
		resultsUri = parseString(RESULTS_URI, true);

		parseExecution();

		skipEndElement(EXECUTION_RESOURCE);
	}

	private void parseExecution() throws XMLStreamException, IOException {
		try (ExecutionReader exReader = new ExecutionReader(threadPool);) {
			exReader.parse(reader);
			execution = new Execution();
			execution.setSchemaVersion(Versions.EXECUTION_BI);
			execution.setCompletedOn(XMLFactories.toUTCXMLTime(exReader.getCompletedOn()));
			execution.setErrorMessage(exReader.getErrorMessage());
			execution.setScheduledFor(XMLFactories.toUTCXMLTime(exReader.getScheduleDate()));
			execution.setCompletedOn(XMLFactories.toUTCXMLTime(exReader.getCompletedOn()));
			execution.setStartedOn(XMLFactories.toUTCXMLTime(exReader.getStartedOn()));
			execution.setSubmittedOn(XMLFactories.toUTCXMLTime(exReader.getSubmittedOn()));
			execution.setUuid(exReader.getUUId());

			// no query expected
			InputStream is = exReader.getQueryInputStream();
			Validate.isTrue(is == null, "No Query is expected when Execution is embeded in an ExecutionResource.");
		}
	}
}
