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
package org.enquery.encryptedquery.responder.business.execution.impl;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.json.JSONStringConverter;
import org.enquery.encryptedquery.responder.business.execution.ExecutionUpdater;
import org.enquery.encryptedquery.responder.business.execution.QueryExecutionScheduler;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.enquery.encryptedquery.xml.transformation.ExecutionExportReader;
import org.enquery.encryptedquery.xml.transformation.ExecutionReader;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ExecutionUpdaterImpl implements ExecutionUpdater {

	private static final Logger log = LoggerFactory.getLogger(ExecutionUpdaterImpl.class);

	@Reference
	private ExecutionRepository executionRepo;
	@Reference
	private ExecutorService threadPool;
	@Reference
	private QueryExecutionScheduler scheduler;
	@Reference
	private DataSchemaService dataSchemaRepo;
	@Reference
	private DataSourceRegistry dataSourceRepo;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.business.execution.ExecutionUpdater#create(org.enquery.
	 * encryptedquery.responder.data.entity.DataSchema,
	 * org.enquery.encryptedquery.responder.data.entity.DataSource, java.io.InputStream)
	 */
	@Override
	public Execution create(DataSchema dataSchema, DataSource dataSource, InputStream inputStream) throws IOException, JobExecutionException {
		try (ExecutionReader extractor = new ExecutionReader(threadPool);) {
			extractor.parse(inputStream);
			return scheduleOne(dataSchema, dataSource, extractor);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.business.execution.ExecutionUpdater#createMultiple(org.
	 * enquery.encryptedquery.responder.data.entity.DataSchema,
	 * org.enquery.encryptedquery.responder.data.entity.DataSource, java.io.InputStream)
	 */
	@Override
	public List<Execution> createFromImportXML(InputStream inputStream) throws IOException, JobExecutionException, XMLStreamException {
		List<Execution> result = new ArrayList<>();

		try (ExecutionExportReader reader = new ExecutionExportReader(threadPool)) {
			reader.parse(inputStream);
			while (reader.hasNextItem()) {
				try (ExecutionReader executionReader = reader.next();) {
					DataSchema dsch = dataSchemaRepo.findByName(reader.getDataSchemaName());
					Validate.notNull(dsch);
					DataSource dsrc = dataSourceRepo.find(reader.getDataSourceName());
					Validate.notNull(dsrc);
					result.add(scheduleOne(dsch, dsrc, executionReader));
				}
			}
		}
		log.info("Processed {} executions.", result.size());
		return result;
	}

	private Execution scheduleOne(DataSchema dataSchema, DataSource dataSource, ExecutionReader reader) throws IOException, JobExecutionException {
		Execution ex = new Execution();
		ex.setUuid(reader.getUUId());
		ex.setDataSchema(dataSchema);
		ex.setDataSourceName(dataSource.getName());
		ex.setReceivedTime(Date.from(Instant.now()));
		ex.setScheduleTime(reader.getScheduleDate());
		ex.setParameters(JSONStringConverter.toString(reader.getConfig()));

		log.info("Scheduling execution: {}", ex);

		return executionRepo.addIfNotPresent(ex,
				reader.getQueryInputStream(),
				e -> scheduler.add(e));
	}

}
