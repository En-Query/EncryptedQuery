package org.enquery.encryptedquery.xml.transformation;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.Configuration;
import org.enquery.encryptedquery.xml.schema.Execution;

public class ExecutionWriter implements Closeable {

	private static final String EXECUTION_NS = "http://enquery.net/encryptedquery/execution";
	private static final QName EXECUTION = new QName(EXECUTION_NS, "execution");
	private static final QName UUID = new QName(EXECUTION_NS, "uuid");
	private static final QName SCHEDULED_FOR = new QName(EXECUTION_NS, "scheduledFor");
	private static final QName SUBMITTED_ON = new QName(EXECUTION_NS, "submittedOn");
	private static final QName STARTED_ON = new QName(EXECUTION_NS, "startedOn");
	private static final QName COMPLETED_ON = new QName(EXECUTION_NS, "completedOn");
	private static final QName ERROR_MSG = new QName(EXECUTION_NS, "errorMessage");
	private static final QName CANCELLED = new QName(EXECUTION_NS, "cancelled");

	private static final QName CONFIGURATION = new QName(EXECUTION_NS, "configuration");
	private static final QName CONFIG_ENTRY = new QName(EXECUTION_NS, "entry");

	private final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

	private BufferedWriter bufferedWriter;
	private OutputStreamWriter streamWriter;
	private XMLEventWriter writer;
	private boolean ownsWriter;

	public ExecutionWriter(OutputStream out) throws XMLStreamException {
		Validate.notNull(out);

		streamWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
		bufferedWriter = new BufferedWriter(streamWriter);
		writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(bufferedWriter);
		ownsWriter = true;
	}

	public ExecutionWriter(XMLEventWriter writer) throws XMLStreamException {
		Validate.notNull(writer);
		this.writer = writer;
		ownsWriter = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		if (!ownsWriter) return;

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

	public void writeExecution(boolean isRoot, Execution execution, InputStream queryBytes) throws XMLStreamException, IOException {
		if (isRoot) emitBeginDocument();
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(EXECUTION, null, null));
		writer.add(eventFactory.createAttribute(Versions.SCHEMA_VERSION_ATTRIB, Versions.EXECUTION));

		writeUuid(execution.getUuid());
		writeTime(SCHEDULED_FOR, execution.getScheduledFor());

		emitConfiguration(execution.getConfiguration());

		Validate.notNull(execution.getScheduledFor());
		writeTime(SUBMITTED_ON, execution.getScheduledFor());

		writeTime(STARTED_ON, execution.getStartedOn());
		writeTime(COMPLETED_ON, execution.getCompletedOn());

		if (execution.getErrorMessage() != null) {
			writeString(ERROR_MSG, execution.getErrorMessage());
		}
		if (execution.isCancelled() != null) {
			writeString(CANCELLED, Boolean.toString(execution.isCancelled()));
		}

		streamQuery(queryBytes);

		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(EXECUTION, null));
		if (isRoot) emitEndDocument();
	}

	/**
	 * @param writer
	 * @param uuid
	 * @throws XMLStreamException
	 */
	private void writeUuid(String uuid) throws XMLStreamException {
		writeString(UUID, uuid);
	}

	private void writeString(QName name, String value) throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(name, null, null));
		writer.add(eventFactory.createCharacters(value));
		writer.add(eventFactory.createEndElement(name, null));
	}

	private void streamQuery(InputStream queryBytes) throws XMLStreamException, IOException {
		if (queryBytes == null) return;

		try (QueryStreamer queryStreamer = new QueryStreamer(queryBytes)) {
			queryStreamer.writeQuery(writer);
		} catch (IOException | XMLStreamException e) {
			throw new RuntimeException("Error streaming Query.", e);
		}
	}

	private void writeTime(QName name, XMLGregorianCalendar time) throws XMLStreamException {
		Validate.notNull(writer);
		if (time == null) return;

		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(name, null, null));
		writer.add(eventFactory.createCharacters(time.toXMLFormat())); // XMLFactories.toUTCXMLTime(startTime).toXMLFormat()));
		writer.add(eventFactory.createEndElement(name, null));
	}

	private void emitConfiguration(Configuration config) throws XMLStreamException {
		if (config == null) return;
		if (config.getEntry().isEmpty()) return;

		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(CONFIGURATION, null, null));
		for (Configuration.Entry entry : config.getEntry()) {
			writer.add(eventFactory.createIgnorableSpace("\n"));
			writer.add(eventFactory.createStartElement(CONFIG_ENTRY, null, null));
			writer.add(eventFactory.createAttribute("key", entry.getKey()));
			writer.add(eventFactory.createAttribute("value", entry.getValue()));
			writer.add(eventFactory.createEndElement(CONFIG_ENTRY, null));
		}
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(CONFIGURATION, null));
	}

	private void emitBeginDocument() throws XMLStreamException {
		writer.add(eventFactory.createStartDocument("UTF-8"));
		writer.setDefaultNamespace(EXECUTION_NS);
		writer.add(eventFactory.createIgnorableSpace("\n"));
	}

	private void emitEndDocument() throws XMLStreamException {
		writer.add(eventFactory.createEndDocument());
	}
}
