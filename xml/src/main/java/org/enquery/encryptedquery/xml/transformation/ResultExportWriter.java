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
import org.enquery.encryptedquery.xml.schema.ResultResource;

public class ResultExportWriter implements Closeable {

	private static final String NAME_SPACE = "http://enquery.net/encryptedquery/result-export";
	private static final QName RESULT_EXPORT = new QName(NAME_SPACE, "resultExport");
	private static final QName ITEM = new QName(NAME_SPACE, "item");
	private static final QName RESULTS = new QName(NAME_SPACE, "results");

	private final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

	private BufferedWriter bufferedWriter;
	private OutputStreamWriter streamWriter;
	private XMLEventWriter writer;
	private boolean isRoot;
	private boolean ownWriter;


	public ResultExportWriter(XMLEventWriter writer) throws XMLStreamException {
		Validate.notNull(writer);
		this.writer = writer;
		ownWriter = false;
	}

	public ResultExportWriter(OutputStream out) throws XMLStreamException {
		Validate.notNull(out);
		streamWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
		bufferedWriter = new BufferedWriter(streamWriter);
		writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(bufferedWriter);
		ownWriter = true;
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
		writer.add(eventFactory.createStartElement(RESULT_EXPORT, null, null));
	}

	public void beginItem() throws XMLStreamException {
		writer.add(eventFactory.createStartElement(ITEM, null, null));
	}

	public void endItem() throws XMLStreamException {
		writer.add(eventFactory.createEndElement(ITEM, null));
		writer.add(eventFactory.createIgnorableSpace("\n"));
	}

	public void writeExecutionResource(ExecutionResource executionResource) throws IOException, XMLStreamException {
		try (ExecutionResourceWriter rw = new ExecutionResourceWriter(writer)) {
			rw.writeExecutionResource(false, executionResource, null);
		}
	}

	public void beginResults() throws XMLStreamException {
		writer.add(eventFactory.createStartElement(RESULTS, null, null));
	}

	public void endResults() throws XMLStreamException {
		writer.add(eventFactory.createEndElement(RESULTS, null));
		writer.add(eventFactory.createIgnorableSpace("\n"));
	}

	public void writeResultResource(ResultResource result, InputStream payloadInputStream) throws IOException, XMLStreamException {
		try (ResultWriter rw = new ResultWriter(writer)) {
			rw.setEmitExecution(false);
			rw.writeResult(result, payloadInputStream, false);
		}
	}

	public void end() throws XMLStreamException {
		// writer.add(eventFactory.createEndElement(ITEM, null));
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(RESULT_EXPORT, null));
		writer.add(eventFactory.createIgnorableSpace("\n"));
		if (isRoot) emitEndDocument();
	}

	private void emitBeginDocument() throws XMLStreamException {
		writer.add(eventFactory.createStartDocument("UTF-8"));
		writer.setDefaultNamespace(NAME_SPACE);
		writer.add(eventFactory.createIgnorableSpace("\n"));
	}

	private void emitEndDocument() throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndDocument());
	}
}
