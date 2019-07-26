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
package org.enquery.encryptedquery.responder.business.execution;

import java.io.IOException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.business.results.ResultCollector;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.entity.ExecutionStatus;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates the status of an execution: finished, running, etc. For long running processes, the
 * QueryRunner interface offers a way to check if the execution is complete. This component will
 * call it, and update the database.
 * 
 * This is called from Camel route in the integration module when Querier retrieves an Execution
 */
@Component(service = ExecutionStatusUpdater.class)
public class ExecutionStatusUpdater {

	private static final Logger log = LoggerFactory.getLogger(ExecutionStatusUpdater.class);

	@Reference
	private ResultCollector resultCollector;
	@Reference
	private ExecutionRepository exRepo;
	@Reference
	private DataSourceRegistry dataSrcRepo;
	@Reference
	private ExecutionLock executionLock;

	/**
	 * Update the status of any pending execution, and collect its results if complete
	 */
	public void update() {
		exRepo.listIncomplete()
				.stream()
				.forEach(e -> updateExecution(e));
	}

	/**
	 * @param e
	 * @return
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private void updateExecution(Execution e) {
		Validate.notNull(e);
		if (e.getHandle() == null) return;
		Validate.notNull(e.getId());

		// synchronize on the execution id
		try {
			executionLock.lock(e);
		} catch (InterruptedException e2) {
			// normal case, if the application is shutown, just return without doing anything
			log.warn("Interrupted while waiting for a lock on execution {}", e);
			return;
		}
		log.warn("Acquired a lock on execution {}", e);
		try {
			log.info("Updating status of execution {}", e.getId());
			DataSource dataSource = dataSrcRepo.find(e.getDataSourceName());
			if (dataSource == null) {
				log.info("Datasource {} not found for execution {}.", e.getDataSourceName(), e.getId());
				return;
			}

			log.info("Execution {} data source is {}.", e.getId(), dataSource.getName());

			ExecutionStatus status = dataSource.getRunner().status(e.getHandle());
			log.info("Execution {} completion status is {}.", e.getId(), status);
			if (status == null) return;

			if (status.getEndTime() != null) {
				e.setCanceled(status.isCanceled());
				e.setErrorMsg(status.getError());
				e.setEndTime(status.getEndTime());
				exRepo.update(e);
			}
			try {
				resultCollector.collect(e);
			} catch (IOException e1) {
				log.error("Error collecting results for execution: " + e.getId(), e1);
			}
		} finally {
			executionLock.unlock(e);
			log.warn("Released a lock on execution {}", e);
		}
	}
}
