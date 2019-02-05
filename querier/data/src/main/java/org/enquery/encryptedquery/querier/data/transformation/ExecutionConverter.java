package org.enquery.encryptedquery.querier.data.transformation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.xml.transformation.XMLFactories;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;


@Component(service = ExecutionConverter.class)
public class ExecutionConverter {

	private static final String EXECUTION_NS = "http://enquery.net/encryptedquery/execution";
	private static final String QUERY_NS = "http://enquery.net/encryptedquery/query";
	private static final QName EXECUTION = new QName(EXECUTION_NS, "execution");
	private static final QName SCHEDULED_FOR = new QName(EXECUTION_NS, "scheduledFor");
	private static final QName IN_QUERY_QNAME = new QName(QUERY_NS, "query");
	private static final QName OUT_QUERY_QNAME = new QName(EXECUTION_NS, "query");
	private static final QName CONFIGURATION = new QName(EXECUTION_NS, "configuration");
	private static final QName CONFIG_ENTRY = new QName(EXECUTION_NS, "entry");
	private static final String QUERY_SCHEMA_VERSION_ATTRIB = "schemaVersion";

	// needs to match version attribute in the XSD resource (most current version)
	private static final String OUT_QUERY_CURRENT_XSD_VERSION = "2.0";

	@Reference
	private QueryRepository queryRepo;
	@Reference
	private ExecutorService threadPool;

	private final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

	QueryRepository getQueryRepo() {
		return queryRepo;
	}

	void setQueryRepo(QueryRepository queryRepo) {
		this.queryRepo = queryRepo;
	}

	public ExecutorService getThreadPool() {
		return threadPool;
	}

	public void setThreadPool(ExecutorService threadPool) {
		this.threadPool = threadPool;
	}

	public InputStream toExecutionXMLStream(Schedule jpaSchedule) {
		Validate.notNull(jpaSchedule);
		Validate.notNull(jpaSchedule.getQuery());
		Validate.notNull(jpaSchedule.getQuery().getId());

		try {
			PipedOutputStream out = new PipedOutputStream();
			PipedInputStream in = new PipedInputStream(out);

			threadPool.submit(() -> {
				writeExecution(jpaSchedule, out);
			});

			return in;

		} catch (IOException e) {
			throw new RuntimeException("Error converting Schedule to Execution.", e);
		}
	}


	private void writeExecution(Schedule schedule, OutputStream out) {
		try (OutputStreamWriter osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				InputStream queryBytes = queryRepo.loadQueryBytes(schedule.getQuery().getId())) {

			Validate.notNull(queryBytes, "Query bytes blob not found.");

			final XMLEventWriter writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(bw);
			try {
				emitBeginDocument(writer);
				emitScheduleDate(writer, schedule.getStartTime());
				emitConfiguration(writer, schedule.getParameters());
				emitQuery(writer, queryBytes);
				emitEndDocument(writer);
			} finally {
				writer.flush();
				writer.close();
			}

		} catch (IOException | XMLStreamException e) {
			e.printStackTrace();
			throw new RuntimeException("Error writing Execution to stream.", e);
		}
	}

	private void emitQuery(XMLEventWriter writer, InputStream queryBytes) throws XMLStreamException, IOException {
		writer.add(eventFactory.createIgnorableSpace("\n    "));

		XMLEventReader reader = null;
		try (InputStreamReader isr = new InputStreamReader(queryBytes);
				BufferedReader br = new BufferedReader(isr);) {

			reader = XMLFactories.xmlInputFactory.createXMLEventReader(br);
			StartElement queryStart = nextStartElement(reader);
			Validate.isTrue(IN_QUERY_QNAME.equals(queryStart.getName()));

			writer.add(eventFactory.createStartElement(OUT_QUERY_QNAME, null, null));
			writer.add(eventFactory.createAttribute(QUERY_SCHEMA_VERSION_ATTRIB, OUT_QUERY_CURRENT_XSD_VERSION));
			while (reader.hasNext()) {
				XMLEvent event = reader.nextEvent();
				if (event.isEndElement() && IN_QUERY_QNAME.equals(event.asEndElement().getName())) {
					break;
				}
				writer.add(event);
			}
			writer.add(eventFactory.createEndElement(OUT_QUERY_QNAME, null));

		} finally {
			if (reader != null) reader.close();
		}
	}

	private void emitScheduleDate(XMLEventWriter writer, Date startTime) throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(SCHEDULED_FOR, null, null));
		writer.add(eventFactory.createCharacters(XMLFactories.toXMLTime(startTime).toXMLFormat()));
		writer.add(eventFactory.createEndElement(SCHEDULED_FOR, null));
	}

	private void emitConfiguration(XMLEventWriter writer, String parameters) throws XMLStreamException {
		Map<String, String> map = JSONConverter.toMapStringString(parameters);

		if (map == null) return;
		if (map.isEmpty()) return;

		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(CONFIGURATION, null, null));
		for (java.util.Map.Entry<String, String> entry : map.entrySet()) {
			writer.add(eventFactory.createIgnorableSpace("\n"));
			writer.add(eventFactory.createStartElement(CONFIG_ENTRY, null, null));
			writer.add(eventFactory.createAttribute("key", entry.getKey()));
			writer.add(eventFactory.createAttribute("value", entry.getValue()));
			writer.add(eventFactory.createEndElement(CONFIG_ENTRY, null));
		}
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(CONFIGURATION, null));
	}


	private void emitBeginDocument(final XMLEventWriter writer) throws XMLStreamException {
		writer.add(eventFactory.createStartDocument("UTF-8"));
		writer.setDefaultNamespace(EXECUTION_NS);
		writer.add(eventFactory.createIgnorableSpace("\n"));

		writer.add(eventFactory.createStartElement(EXECUTION, null, null));
	}

	private void emitEndDocument(XMLEventWriter writer) throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(EXECUTION, null));
		writer.add(eventFactory.createEndDocument());
	}

	private StartElement nextStartElement(XMLEventReader reader) throws XMLStreamException {
		while (reader.hasNext()) {
			XMLEvent event = reader.nextEvent();
			if (event.isStartElement()) {
				StartElement startElement = event.asStartElement();
				return startElement;
			}
		}
		return null;
	}

}
