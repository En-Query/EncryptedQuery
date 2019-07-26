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
import org.enquery.encryptedquery.xml.schema.ExecutionResource;

public class ExecutionResourceWriter implements Closeable {

	private static final String RESOURCE_NS = "http://enquery.net/encryptedquery/resource";
	private static final QName ID = new QName(RESOURCE_NS, "id");
	private static final QName SELF_URI = new QName(RESOURCE_NS, "selfUri");

	private static final String NAMESPACE = "http://enquery.net/encryptedquery/execution";
	private static final QName EXECUTION_RESOURCE = new QName(NAMESPACE, "executionResource");
	private static final QName DATA_SOURCE_URI = new QName(NAMESPACE, "dataSourceUri");
	private static final QName RESULTS_URI = new QName(NAMESPACE, "resultsUri");

	private static final String QUERY_SCHEMA_VERSION_ATTRIB = "schemaVersion";
	// needs to match version attribute in the XSD resource (most current version)
	private static final String OUT_QUERY_CURRENT_XSD_VERSION = "2.0";

	private final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

	private BufferedWriter bufferedWriter;
	private OutputStreamWriter streamWriter;
	private XMLEventWriter writer;
	private boolean ownsWriter;
	private boolean isRoot;

	public ExecutionResourceWriter(OutputStream out) throws XMLStreamException {
		Validate.notNull(out);

		streamWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
		bufferedWriter = new BufferedWriter(streamWriter);
		writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(bufferedWriter);
		ownsWriter = true;
	}

	public ExecutionResourceWriter(XMLEventWriter writer) throws XMLStreamException {
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
		Exception error = null;

		if (writer != null) try {
			emitEnd();
			if (isRoot) emitEndDocument();
			if (ownsWriter) {
				writer.flush();
				writer.close();
			}
			writer = null;
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

	public void writeExecutionResource(
			boolean isRoot,
			ExecutionResource executionResource,
			InputStream queryBytes) throws XMLStreamException, IOException {

		this.isRoot = isRoot;
		if (isRoot) emitBeginDocument();
		emitBegin();

		emitStringElement(ID, Integer.toString(executionResource.getId()));
		emitStringElement(SELF_URI, executionResource.getSelfUri());
		emitStringElement(DATA_SOURCE_URI, executionResource.getDataSourceUri());
		emitStringElement(RESULTS_URI, executionResource.getResultsUri());
		try (ExecutionWriter executionWriter = new ExecutionWriter(writer)) {
			executionWriter.writeExecution(false, executionResource.getExecution(), queryBytes);
		}
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
	private void emitBegin() throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(EXECUTION_RESOURCE, null, null));
		writer.add(eventFactory.createAttribute(QUERY_SCHEMA_VERSION_ATTRIB, OUT_QUERY_CURRENT_XSD_VERSION));

	}

	/**
	 * @param writer
	 * @throws XMLStreamException
	 */
	private void emitEnd() throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(EXECUTION_RESOURCE, null));
	}


}
