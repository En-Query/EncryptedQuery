package org.enquery.encryptedquery.xml.transformation;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.xml.schema.ExecutionExport;

public class ExecutionExportWriter implements Closeable {

	private static final String NAMESPACE = "http://enquery.net/encryptedquery/execution-export";
	private static final QName EXECUTION_EXPORT = new QName(NAMESPACE, "executionExport");
	private static final QName ITEM = new QName(NAMESPACE, "item");
	private static final QName DATA_SCHEMA_NAME = new QName(NAMESPACE, "dataSchemaName");
	private static final QName DATA_SOURCE_NAME = new QName(NAMESPACE, "dataSourceName");



	private final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

	private BufferedWriter bufferedWriter;
	private OutputStreamWriter streamWriter;
	private XMLEventWriter writer;
	private boolean ownWriter;
	private boolean isRoot;

	public ExecutionExportWriter(OutputStream out) throws XMLStreamException {
		Validate.notNull(out);

		streamWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
		bufferedWriter = new BufferedWriter(streamWriter);
		writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(bufferedWriter);
		ownWriter = true;
	}

	public ExecutionExportWriter(XMLEventWriter writer) throws XMLStreamException {
		Validate.notNull(writer);
		this.writer = writer;
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

	public void begin(boolean isRoot) throws XMLStreamException {
		this.isRoot = isRoot;
		if (isRoot) emitBeginDocument();
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(EXECUTION_EXPORT, null, null));
	}

	public void end() throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(EXECUTION_EXPORT, null));
		if (isRoot) emitEndDocument();
	}

	public void writeExecution(ExecutionExport.Item item, InputStream queryBytes) throws XMLStreamException, IOException {

		emitBeginItem();
		emitStringElement(DATA_SCHEMA_NAME, item.getDataSchemaName());
		emitStringElement(DATA_SOURCE_NAME, item.getDataSourceName());
		try (ExecutionWriter executionWriter = new ExecutionWriter(writer)) {
			executionWriter.writeExecution(false, item.getExecution(), queryBytes);
		}
		emitEndItem();
	}

	/**
	 * @param writer
	 * @param uuid2
	 * @throws XMLStreamException
	 */
	private void emitStringElement(QName elementName, String elementValue) throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(elementName, null, null));
		writer.add(eventFactory.createCharacters(elementValue));
		writer.add(eventFactory.createEndElement(elementName, null));
	}

	private void emitBeginDocument() throws XMLStreamException {
		writer.add(eventFactory.createStartDocument("UTF-8"));
		writer.setDefaultNamespace(NAMESPACE);
	}

	private void emitEndDocument() throws XMLStreamException {
		writer.add(eventFactory.createEndDocument());
	}

	/**
	 * @param writer
	 * @throws XMLStreamException
	 */
	private void emitEndItem() throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(ITEM, null));
	}

	/**
	 * @param writer
	 * @throws XMLStreamException
	 */
	private void emitBeginItem() throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(ITEM, null, null));
	}

}
