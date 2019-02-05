/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.responder.data.transformation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.RestServiceRegistry;
import org.enquery.encryptedquery.responder.data.service.ResultRepository;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Resource;
import org.enquery.encryptedquery.xml.schema.ResultResource;
import org.enquery.encryptedquery.xml.schema.ResultResources;
import org.enquery.encryptedquery.xml.transformation.XMLFactories;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = ResultTypeConverter.class)
public class ResultTypeConverter {

	private static final Logger log = LoggerFactory.getLogger(ResultTypeConverter.class);

	private static final String RESULT_NS = "http://enquery.net/encryptedquery/result";
	private static final String RESOURCE_NS = "http://enquery.net/encryptedquery/resource";
	private static final String RESPONSE_NS = "http://enquery.net/encryptedquery/response";
	private static final QName RESULT_RESOURCE = new QName(RESULT_NS, "resultResource");
	private static final QName ID = new QName(RESOURCE_NS, "id");
	private static final QName SELF_URI = new QName(RESOURCE_NS, "selfUri");
	private static final QName CREATED_ON = new QName(RESULT_NS, "createdOn");
	private static final QName PAYLOAD = new QName(RESULT_NS, "payload");
	private static final QName EXECUTION = new QName(RESULT_NS, "execution");
	private static final QName RESPONSE = new QName(RESPONSE_NS, "response");
	private static final String SCHEMA_VERSION_ATTRIB = "schemaVersion";
	// needs to match version attribute in the XSD resource (most current version)
	private static final String RESPONSE_CURRENT_XSD_VERSION = "2.0";

	@Reference
	private ResultRepository resultRepo;
	@Reference
	private ExecutorService threadPool;

	private ObjectFactory objectFactory;

	private final XMLEventFactory eventFactory = XMLEventFactory.newInstance();


	@Activate
	void activate() throws DatatypeConfigurationException {
		objectFactory = new ObjectFactory();
	}

	/**
	 * Convert a collection of Result JPA entity belonging to the same data schema, data source, and
	 * execution to XML ResultResources.
	 * 
	 * @param jpaResults
	 * @param dataSchema
	 * @param dataSource
	 * @param execution
	 * @param registry
	 * @return
	 */
	public JAXBElement<ResultResources> toXMLResults(
			Collection<org.enquery.encryptedquery.responder.data.entity.Result> jpaResults,
			DataSchema dataSchema,
			DataSource dataSource,
			Execution execution,
			RestServiceRegistry registry) {

		Validate.notNull(jpaResults);
		Validate.notNull(dataSchema);
		Validate.notNull(dataSource);
		Validate.notNull(execution);
		Validate.notNull(registry);

		if (log.isDebugEnabled()) {
			log.debug("Converting {} to XML ResultResources.", jpaResults);
		}

		ResultResources resultResource = new ResultResources();
		resultResource.getResultResource().addAll(jpaResults.stream().map(
				jpaResult -> {
					return makeResultResource(jpaResult, dataSchema, dataSource, execution, registry);
				})
				.collect(Collectors.toList()));

		return objectFactory.createResultResources(resultResource);
	}

	private ResultResource makeResultResource(org.enquery.encryptedquery.responder.data.entity.Result jpaResult,
			DataSchema dataSchema,
			DataSource dataSource,
			Execution execution,
			RestServiceRegistry registry) {


		final Integer executionId = execution.getId();
		final Integer dataSchemaId = dataSchema.getId();
		final Integer dataSourceId = dataSource.getId();

		final ResultResource resource = new ResultResource();

		resource.setId(jpaResult.getId());
		resource.setCreatedOn(XMLFactories.toXMLTime(jpaResult.getCreationTime()));
		resource.setSelfUri(registry.resultUri(dataSchemaId,
				dataSourceId,
				executionId,
				jpaResult.getId()));

		// TODO: possibly move this to Execution Converter
		Resource executionResource = new Resource();
		executionResource.setId(executionId);

		executionResource.setSelfUri(
				registry.executionUri(
						dataSchemaId, dataSourceId, executionId));

		resource.setExecution(executionResource);
		return resource;
	}

	public InputStream toXMLResultWithPayload(org.enquery.encryptedquery.responder.data.entity.Result jpaResult,
			DataSchema dataSchema,
			DataSource dataSource,
			Execution execution,
			RestServiceRegistry registry) throws JAXBException, IOException {

		Validate.notNull(jpaResult);
		Validate.notNull(dataSchema);
		Validate.notNull(dataSource);
		Validate.notNull(execution);
		Validate.notNull(registry);

		ResultResource result = makeResultResource(jpaResult, dataSchema, dataSource, execution, registry);

		try {
			PipedOutputStream out = new PipedOutputStream();
			PipedInputStream in = new PipedInputStream(out);

			threadPool.submit(() -> writeResult(result, out));

			return in;
		} catch (IOException e) {
			throw new RuntimeException("Error converting Result to XML stream.", e);
		}
	}

	private void writeResult(ResultResource result, PipedOutputStream out) {
		try (OutputStreamWriter osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				InputStream payloadInputStream = resultRepo.payloadInputStream(result.getId())) {

			Validate.notNull(payloadInputStream, "Result payload blob not found.");

			final XMLEventWriter writer = XMLFactories.xmlOutputFactory.createXMLEventWriter(bw);
			try {
				emitBeginDocument(writer);
				emitElement(writer, ID, Integer.toString(result.getId()));
				emitElement(writer, SELF_URI, result.getSelfUri());
				emitElement(writer, CREATED_ON, result.getCreatedOn().toXMLFormat());
				emitExecution(writer, result.getExecution());
				emitPayload(writer, payloadInputStream);
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



	private void emitExecution(XMLEventWriter writer, Resource execution) throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(EXECUTION, null, null));

		emitElement(writer, ID, Integer.toString(execution.getId()));
		emitElement(writer, SELF_URI, execution.getSelfUri());

		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(EXECUTION, null));
	}

	private void emitPayload(XMLEventWriter writer, InputStream payloadInputStream) throws XMLStreamException, IOException {
		XMLEventReader reader = null;
		try (InputStreamReader isr = new InputStreamReader(payloadInputStream);
				BufferedReader br = new BufferedReader(isr);) {

			reader = XMLFactories.xmlInputFactory.createXMLEventReader(br);
			StartElement start = nextStartElement(reader);
			Validate.notNull(start);
			Validate.isTrue(RESPONSE.equals(start.getName()));

			writer.add(eventFactory.createIgnorableSpace("\n"));
			writer.add(eventFactory.createStartElement(PAYLOAD, null, null));
			writer.add(eventFactory.createAttribute(SCHEMA_VERSION_ATTRIB, RESPONSE_CURRENT_XSD_VERSION));
			while (reader.hasNext()) {
				XMLEvent event = reader.nextEvent();
				if (event.isEndElement() &&
						RESPONSE.equals(event.asEndElement().getName())) {
					break;
				}
				writer.add(event);
			}

			writer.add(eventFactory.createIgnorableSpace("\n"));
			writer.add(eventFactory.createEndElement(PAYLOAD, null));
		} finally {
			if (reader != null) reader.close();
		}
	}


	private void emitElement(XMLEventWriter writer, QName element, String value) throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createStartElement(element, null, null));
		writer.add(eventFactory.createCharacters(value));
		writer.add(eventFactory.createEndElement(element, null));
	}

	private void emitBeginDocument(final XMLEventWriter writer) throws XMLStreamException {
		writer.add(eventFactory.createStartDocument("UTF-8"));
		writer.setDefaultNamespace(RESULT_NS);
		writer.add(eventFactory.createIgnorableSpace("\n"));

		writer.add(eventFactory.createStartElement(RESULT_RESOURCE, null, null));
	}

	private void emitEndDocument(XMLEventWriter writer) throws XMLStreamException {
		writer.add(eventFactory.createIgnorableSpace("\n"));
		writer.add(eventFactory.createEndElement(RESULT_RESOURCE, null));
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
