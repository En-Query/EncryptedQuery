package org.enquery.encryptedquery.xml.transformation;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.xml.schema.Configuration;

/**
 * Parsing of XML Result Export
 * 
 */
public class ResultExportReader extends BaseReader {

	private static final String NAMESPACE = "http://enquery.net/encryptedquery/result-export";
	private static final QName RESULT_EXPORT = new QName(NAMESPACE, "resultExport");
	private static final QName ITEM = new QName(NAMESPACE, "item");
	private static final QName RESULTS = new QName(NAMESPACE, "results");

	private static final String RESULT_NAMESPACE = "http://enquery.net/encryptedquery/result";
	private static final QName RESULT_RESOURCE = new QName(RESULT_NAMESPACE, "resultResource");

	private ExecutorService threadPool;

	private int executionId;
	private String dataSourceUri;
	private String resultsUri;
	private String executionSelfUri;
	private XMLGregorianCalendar executionCompletedOn;
	private String executionErrorMsg;
	private XMLGregorianCalendar executionScheduledFor;
	private XMLGregorianCalendar executionStartedOn;
	private XMLGregorianCalendar executionSubmittedOn;
	private String executionUUID;
	private Configuration executionConfig;
	private boolean executionCancelled;


	public ResultExportReader(ExecutorService threadPool) {
		Validate.notNull(threadPool);
		this.threadPool = threadPool;
	}

	public Configuration getExecutionConfig() {
		return executionConfig;
	}

	public String getResultsUri() {
		return resultsUri;
	}

	public int getExecutionId() {
		return executionId;
	}

	public String getDataSourceUri() {
		return dataSourceUri;
	}

	public String getExecutionSelfUri() {
		return executionSelfUri;
	}

	public XMLGregorianCalendar getExecutionCompletedOn() {
		return executionCompletedOn;
	}

	public String getExecutionErrorMsg() {
		return executionErrorMsg;
	}

	public XMLGregorianCalendar getExecutionScheduledFor() {
		return executionScheduledFor;
	}

	public XMLGregorianCalendar getExecutionStartedOn() {
		return executionStartedOn;
	}

	public XMLGregorianCalendar getExecutionSubmittedOn() {
		return executionSubmittedOn;
	}

	public String getExecutionUUID() {
		return executionUUID;
	}

	public boolean isExecutionCancelled() {
		return executionCancelled;
	}

	public void parse(InputStream inputStream) throws IOException {
		Validate.notNull(inputStream);
		try {
			reader = XMLFactories.xmlInputFactory.createXMLEventReader(inputStream);
			ownsReader = true;
			skipStartElement(RESULT_EXPORT);
		} catch (XMLStreamException e) {
			throw new IOException("Error parsing xml", e);
		}
	}

	public void parse(XMLEventReader reader) throws IOException {
		Validate.notNull(reader);
		try {
			this.reader = reader;
			ownsReader = false;
			skipStartElement(RESULT_EXPORT);
		} catch (XMLStreamException e) {
			throw new IOException("Error parsing xml", e);
		}
	}

	public boolean hasNextItem() throws IOException, XMLStreamException {
		if (nextEndElementIs(RESULTS)) {
			// we processing non first item
			skipEndElement(RESULTS);
			skipEndElement(ITEM);
		}
		return nextStartElementIs(ITEM);
	}

	public void nextItem() throws XMLStreamException, IOException {
		skipStartElement(ITEM);
		parseExecution();

		if (nextStartElementIs(RESULTS)) {
			skipStartElement(RESULTS);
		}
	}

	public boolean hasNextResult() throws IOException, XMLStreamException {
		return nextStartElementIs(RESULT_RESOURCE);
	}

	public ResultReader nextResult() throws IOException, XMLStreamException {
		ResultReader resultReader = new ResultReader(threadPool);
		resultReader.parse(reader);
		return resultReader;
	}

	private void parseExecution() throws XMLStreamException, IOException {
		try (ExecutionResourceReader exReader = new ExecutionResourceReader(threadPool);) {
			exReader.parse(reader);
			executionId = exReader.getId();
			dataSourceUri = exReader.getDataSourceUri();
			resultsUri = exReader.getResultsUri();
			executionSelfUri = exReader.getSelfUri();
			executionCompletedOn = exReader.getExecution().getCompletedOn();
			executionConfig = exReader.getExecution().getConfiguration();
			executionErrorMsg = exReader.getExecution().getErrorMessage();
			executionScheduledFor = exReader.getExecution().getScheduledFor();
			executionStartedOn = exReader.getExecution().getStartedOn();
			executionSubmittedOn = exReader.getExecution().getSubmittedOn();
			executionUUID = exReader.getExecution().getUuid();
			executionCancelled = (exReader.getExecution().isCancelled() == null) ? false : exReader.getExecution().isCancelled();
		}
	}
}
