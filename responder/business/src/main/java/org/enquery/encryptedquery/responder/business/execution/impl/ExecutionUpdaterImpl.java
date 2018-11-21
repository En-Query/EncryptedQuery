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
import java.util.Date;
import java.util.concurrent.ExecutorService;

import org.enquery.encryptedquery.json.JSONStringConverter;
import org.enquery.encryptedquery.responder.business.execution.ExecutionUpdater;
import org.enquery.encryptedquery.responder.business.execution.QueryExecutionScheduler;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.enquery.encryptedquery.xml.transformation.ExecutionXMLExtractor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.quartz.JobExecutionException;

@Component
public class ExecutionUpdaterImpl implements ExecutionUpdater {

	@Reference
	private ExecutionRepository executionRepo;
	@Reference
	private ExecutorService threadPool;
	@Reference
	private QueryExecutionScheduler scheduler;

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

		Execution ex = new Execution();
		ex.setDataSchema(dataSchema);
		ex.setDataSourceName(dataSource.getName());
		ex.setReceivedTime(Date.from(Instant.now()));

		try (ExecutionXMLExtractor extractor = new ExecutionXMLExtractor(threadPool);) {
			extractor.parse(inputStream);
			ex.setScheduleTime(extractor.getScheduleDate());
			ex.setParameters(JSONStringConverter.toString(extractor.getConfig()));
			ex = executionRepo.add(ex, extractor.getQueryInputStream());
		}

		scheduler.add(ex);
		return ex;
	}

}
