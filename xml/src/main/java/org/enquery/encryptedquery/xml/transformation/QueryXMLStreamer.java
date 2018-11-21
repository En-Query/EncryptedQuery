package org.enquery.encryptedquery.xml.transformation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.lang3.Validate;

public class QueryXMLStreamer implements Runnable {

	private static final String OUT_QUERY_NS = "http://enquery.net/encryptedquery/query";
	private static final QName OUT_QUERY_QNAME = new QName(OUT_QUERY_NS, "query");
	private static final String IN_QUERY_NS = "http://enquery.net/encryptedquery/execution";
	private static final QName IN_QUERY_QNAME = new QName(IN_QUERY_NS, "query");

	private OutputStream outputStream;
	private XMLEventReader reader;
	private XMLEventFactory eventFactory;

	public QueryXMLStreamer(XMLEventReader reader, OutputStream outputStream) {
		Validate.notNull(reader);
		Validate.notNull(outputStream);
		this.reader = reader;
		this.outputStream = outputStream;
		eventFactory = XMLEventFactory.newInstance();
	}

	@Override
	public void run() {
		try (OutputStreamWriter osw = new OutputStreamWriter(outputStream);
				BufferedWriter bw = new BufferedWriter(osw);) {

			XMLEventWriter writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(bw);
			writer.add(eventFactory.createStartDocument("UTF-8"));
			writer.setDefaultNamespace(OUT_QUERY_NS);
			writer.add(eventFactory.createIgnorableSpace("\n"));
			int inQuery = 0;
			while (reader.hasNext()) {
				XMLEvent event = reader.nextEvent();

				if (isQueryStart(event)) {
					++inQuery;
					writer.add(eventFactory.createStartElement(OUT_QUERY_QNAME, null, null));
				} else if (isQueryEnd(event)) {
					--inQuery;
					writer.add(eventFactory.createEndElement(OUT_QUERY_QNAME, null));
					if (inQuery == 0) break;
				} else if (inQuery > 0) {
					writer.add(event);
				}
			}

			writer.add(eventFactory.createEndDocument());
			writer.flush();
			writer.close();
		} catch (IOException | XMLStreamException e) {
			e.printStackTrace();
		}
	}

	private boolean isQueryEnd(XMLEvent event) {
		if (!event.isEndElement()) return false;
		if (IN_QUERY_QNAME.equals(event.asEndElement().getName())) {
			return true;
		}
		return false;
	}

	private boolean isQueryStart(XMLEvent event) {
		if (!event.isStartElement()) return false;
		if (IN_QUERY_QNAME.equals(event.asStartElement().getName())) {
			return true;
		}
		return false;
	}
}
