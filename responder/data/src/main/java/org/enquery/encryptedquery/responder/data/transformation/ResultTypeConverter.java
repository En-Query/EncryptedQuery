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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.ResourceUriRegistry;
import org.enquery.encryptedquery.responder.data.service.ResultRepository;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Resource;
import org.enquery.encryptedquery.xml.schema.ResultResource;
import org.enquery.encryptedquery.xml.schema.ResultResources;
import org.enquery.encryptedquery.xml.transformation.ResultWriter;
import org.enquery.encryptedquery.xml.transformation.XMLFactories;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = ResultTypeConverter.class)
public class ResultTypeConverter {

	private static final Logger log = LoggerFactory.getLogger(ResultTypeConverter.class);


	@Reference
	private ResultRepository resultRepo;
	@Reference
	private ExecutorService threadPool;
	@Reference(target = "(type=rest)")
	private ResourceUriRegistry registry;

	private ObjectFactory objectFactory;

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
			Execution execution) {

		Validate.notNull(jpaResults);
		Validate.notNull(dataSchema);
		Validate.notNull(dataSource);
		Validate.notNull(execution);
		Validate.notNull(registry);

		if (log.isDebugEnabled()) {
			log.debug("Converting {} to XML ResultResources.", jpaResults);
		}

		ResultResources resultResource = new ResultResources();
		resultResource
				.getResultResource()
				.addAll(jpaResults
						.stream()
						.map(jpaResult -> toXMLResultResource(jpaResult, dataSchema, dataSource, execution))
						.collect(Collectors.toList()));

		return objectFactory.createResultResources(resultResource);
	}

	public ResultResource toXMLResultResource(//
			org.enquery.encryptedquery.responder.data.entity.Result jpaResult,
			DataSchema dataSchema,
			DataSource dataSource,
			Execution execution) {

		final Integer executionId = execution.getId();
		final Integer dataSchemaId = dataSchema.getId();
		final Integer dataSourceId = dataSource.getId();

		final ResultResource resource = new ResultResource();

		resource.setId(jpaResult.getId());
		resource.setCreatedOn(XMLFactories.toUTCXMLTime(jpaResult.getCreationTime()));
		resource.setSelfUri(registry.resultUri(dataSchemaId,
				dataSourceId,
				executionId,
				jpaResult.getId()));


		if (jpaResult.getWindowStartTime() != null) {
			resource.setWindowStart(XMLFactories.toUTCXMLTime(jpaResult.getWindowStartTime()));
		}

		if (jpaResult.getWindowEndTime() != null) {
			resource.setWindowEnd(XMLFactories.toUTCXMLTime(jpaResult.getWindowEndTime()));
		}

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
			Execution execution) throws JAXBException, IOException {

		Validate.notNull(jpaResult);
		Validate.notNull(dataSchema);
		Validate.notNull(dataSource);
		Validate.notNull(execution);
		Validate.notNull(registry);

		ResultResource result = toXMLResultResource(jpaResult, dataSchema, dataSource, execution);

		try {
			PipedOutputStream out = new PipedOutputStream();
			PipedInputStream in = new PipedInputStream(out);

			threadPool.submit(() -> writeResult(result, out));

			return in;
		} catch (IOException e) {
			throw new RuntimeException("Error converting Result to XML stream.", e);
		}
	}

	/**
	 * @param result
	 * @param out
	 * @return
	 */
	private void writeResult(ResultResource result, OutputStream out) {
		try (ResultWriter rw = new ResultWriter(out);
				InputStream payloadInputStream = resultRepo.payloadInputStream(result.getId())) {

			Validate.notNull(payloadInputStream, "Result payload blob not found.");

			rw.writeResult(result, payloadInputStream, true);

		} catch (IOException | XMLStreamException e) {
			e.printStackTrace();
			throw new RuntimeException("Error writing Execution to stream.", e);
		}
	}

}
