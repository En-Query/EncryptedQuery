package org.enquery.encryptedquery.xml.transformation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.xml.Versions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResultReader extends BaseReader {

	private static final Logger log = LoggerFactory.getLogger(ResultReader.class);

	private static final String RESULT_NS = "http://enquery.net/encryptedquery/result";
	private static final String RESOURCE_NS = "http://enquery.net/encryptedquery/resource";
	private static final String RESPONSE_NS = "http://enquery.net/encryptedquery/response";
	private static final QName ID = new QName(RESOURCE_NS, "id");
	private static final QName URI = new QName(RESOURCE_NS, "selfUri");
	private static final QName RESULT_RESOURCE = new QName(RESULT_NS, "resultResource");
	private static final QName CREATED_ON = new QName(RESULT_NS, "createdOn");
	private static final QName WINDOW_START = new QName(RESULT_NS, "windowStart");
	private static final QName WINDOW_END = new QName(RESULT_NS, "windowEnd");
	private static final QName PAYLOAD = new QName(RESULT_NS, "payload");
	private static final QName EXECUTION = new QName(RESULT_NS, "execution");
	private static final QName RESPONSE = new QName(RESPONSE_NS, "response");

	// needs to match the version attribute in the response.xsd
	// private static final String CURRENT_RESPONSE_VERSION = "2.0";


	private ExecutorService threadPool;
	private int resultId;
	private String resultUri;
	private Date creationDate;
	private Date windowStart;
	private Date windowEnd;
	private InputStream responseInputStream;
	private Integer executionId;
	private String executionUri;

	private Future<?> payloadFuture;

	public ResultReader(ExecutorService threadPool) {
		Validate.notNull(threadPool);
		this.threadPool = threadPool;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public InputStream getResponseInputStream() {
		return responseInputStream;
	}

	public int getResultId() {
		return resultId;
	}

	public String getResultUri() {
		return resultUri;
	}

	public Date getWindowStart() {
		return windowStart;
	}

	public Date getWindowEnd() {
		return windowEnd;
	}

	public int getExecutionId() {
		return executionId;
	}

	public String getExecutionUri() {
		return executionUri;
	}


	public void parse(InputStream inputStream) throws IOException, XMLStreamException {
		Validate.notNull(inputStream);
		reader = XMLFactories.xmlInputFactory.createXMLEventReader(inputStream);
		ownsReader = true;
		parse();
	}


	public void parse(XMLEventReader reader) throws IOException, XMLStreamException {
		this.reader = reader;
		ownsReader = false;
		parse();
	}

	/**
	 * @throws IOException
	 * 
	 */
	private void parse() throws IOException, XMLStreamException {
		// the order in which we parse these elements is the
		// order they are defined in the corresponding XML Schema
		// for a forward-only processing
		skipStartElement(RESULT_RESOURCE);
		resultId = parseInteger(ID, true);
		resultUri = parseString(URI, true);
		creationDate = parseDate(CREATED_ON);
		windowStart = parseDate(WINDOW_START);
		windowEnd = parseDate(WINDOW_END);

		parseExecution();

		responseInputStream = makeResponseInputStream();
	}


	/**
	 * @throws XMLStreamException
	 * 
	 */
	private void parseExecution() throws XMLStreamException {
		if (!nextStartElementIs(EXECUTION)) return;
		skipStartElement(EXECUTION);
		executionId = parseInteger(ID, true);
		executionUri = parseString(URI, true);
		// consume the end tag
		skipEndElement(EXECUTION);
	}

	private InputStream makeResponseInputStream() throws IOException, XMLStreamException {
		if (!nextStartElementIs(PAYLOAD)) return null;

		PipedOutputStream out = new PipedOutputStream();
		PipedInputStream in = new PipedInputStream(out);
		payloadFuture = threadPool.submit(() -> writePayload(out));
		return in;
	}

	private void writePayload(OutputStream outputStream) {
		final boolean debugging = log.isDebugEnabled();

		if (debugging) log.debug("begin parsing payload");

		try (OutputStreamWriter osw = new OutputStreamWriter(outputStream);
				BufferedWriter bw = new BufferedWriter(osw)) {

			final XMLEventWriter writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(bw);
			try {
				Validate.isTrue(nextStartElementIs(PAYLOAD));
				StartElement payload = skipStartElement(PAYLOAD);
				validateVersion(payload, Versions.RESPONSE);

				writer.add(XMLFactories.eventFactory.createStartDocument());
				writer.add(XMLFactories.eventFactory.createIgnorableSpace("\n"));
				writer.add(XMLFactories.eventFactory.createStartElement(RESPONSE, null, null));
				writeVersionAttribute(writer, Versions.RESPONSE);
				while (reader.hasNext()) {
					XMLEvent event = reader.nextEvent();
					if (isEndNode(event, PAYLOAD)) break;
					writer.add(event);
				}
				writer.add(XMLFactories.eventFactory.createIgnorableSpace("\n"));
				writer.add(XMLFactories.eventFactory.createEndElement(RESPONSE, null));
				writer.add(XMLFactories.eventFactory.createEndDocument());

			} finally {
				writer.close();
			}
			if (debugging) log.debug("end parsing payload");
		} catch (IOException | XMLStreamException e) {
			e.printStackTrace();
			throw new RuntimeException("Error writing Execution to stream.", e);
		}
	}


	@Override
	public void close() throws IOException {
		if (payloadFuture != null) {
			try {
				payloadFuture.get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}

		try {
			if (reader != null) {
				// consume the end tag
				skipEndElement(RESULT_RESOURCE);

				if (ownsReader) {
					reader.close();
				}
				reader = null;
			}
		} catch (XMLStreamException e) {
			throw new IOException("Error closing reader.", e);
		}
	}
}
