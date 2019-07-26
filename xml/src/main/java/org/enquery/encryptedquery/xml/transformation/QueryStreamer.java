package org.enquery.encryptedquery.xml.transformation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.xml.Versions;

/**
 * Write a Query to a XMLEventWriter from an InputStream or a XMLEventReader. This is used when
 * embeding a query in other XML envelopes
 */
public class QueryStreamer extends BaseReader implements QueryNames {

	private InputStreamReader inputStreamReader;
	private BufferedReader bufferedReader;

	/**
	 * @param reader
	 */
	public QueryStreamer(XMLEventReader reader) {
		Validate.notNull(reader);
		this.reader = reader;
		this.ownsReader = false;
	}

	public QueryStreamer(InputStream inputStream) throws XMLStreamException {
		inputStreamReader = new InputStreamReader(inputStream);
		bufferedReader = new BufferedReader(inputStreamReader);
		this.reader = XMLFactories.xmlInputFactory.createXMLEventReader(bufferedReader);
		this.ownsReader = true;
	}


	@Override
	public void close() throws IOException {
		super.close();
		if (bufferedReader != null) {
			bufferedReader.close();
		}
		if (inputStreamReader != null) {
			inputStreamReader.close();
		}
	}

	/**
	 * @param out
	 * @return
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	public void writeQuery(OutputStream outputStream) throws XMLStreamException, IOException {
		try (OutputStreamWriter osw = new OutputStreamWriter(outputStream);
				BufferedWriter bw = new BufferedWriter(osw);) {

			XMLEventWriter writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(bw);
			try {
				writeQuery(writer);
			} finally {
				writer.close();
			}
		}
	}

	public void writeQuery(final XMLEventWriter writer) throws XMLStreamException {
		StartElement query = skipStartElement(QUERY);
		validateVersion(query, Versions.QUERY);
		writer.add(query);

		while (reader.hasNext()) {
			XMLEvent event = reader.nextEvent();
			writer.add(event);
			if (isEndNode(event, QUERY)) break;
		}
		writer.add(XMLFactories.eventFactory.createIgnorableSpace("\n"));
	}
}
