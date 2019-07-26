package org.enquery.encryptedquery.xml.transformation;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.Validate;

/**
 * Parsing of XML ExecutionImport
 * 
 */
public class ExecutionExportReader extends BaseReader {

	private static final String NAMESPACE = "http://enquery.net/encryptedquery/execution-export";
	private static final QName EXECUTION_EXPORT = new QName(NAMESPACE, "executionExport");
	private static final QName ITEM = new QName(NAMESPACE, "item");
	private static final QName DATA_SCHEMA_NAME = new QName(NAMESPACE, "dataSchemaName");
	private static final QName DATA_SOURCE_NAME = new QName(NAMESPACE, "dataSourceName");

	private String dataSchemaName;
	private String dataSourceName;
	private ExecutorService threadPool;

	public ExecutionExportReader(ExecutorService threadPool) {
		Validate.notNull(threadPool);
		this.threadPool = threadPool;
	}

	public String getDataSchemaName() {
		return dataSchemaName;
	}

	public String getDataSourceName() {
		return dataSourceName;
	}


	public void parse(InputStream inputStream) throws IOException, XMLStreamException {
		Validate.notNull(inputStream);
		reader = XMLFactories.xmlInputFactory.createXMLEventReader(inputStream);
		ownsReader = true;
		skipStartElement(EXECUTION_EXPORT);
	}

	public void parse(XMLEventReader reader) throws IOException, XMLStreamException {
		Validate.notNull(reader);
		this.reader = reader;
		ownsReader = false;
		skipStartElement(EXECUTION_EXPORT);
	}

	public boolean hasNextItem() throws IOException, XMLStreamException {
		if (nextEndElementIs(ITEM)) {
			skipEndElement(ITEM);
		}
		return nextStartElementIs(ITEM);
	}

	public ExecutionReader next() throws IOException, XMLStreamException {
		// skip the item start element
		skipStartElement(ITEM);
		dataSchemaName = parseString(DATA_SCHEMA_NAME, true);
		dataSourceName = parseString(DATA_SOURCE_NAME, true);
		// delegate to the execution extractor from this point
		ExecutionReader executionReader = new ExecutionReader(threadPool);
		executionReader.parse(reader);
		return executionReader;
	}


}
