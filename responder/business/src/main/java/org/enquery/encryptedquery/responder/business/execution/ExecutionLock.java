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

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.concurrency.ValueLock;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.osgi.service.component.annotations.Component;

/**
 * Encapsulates lock for synchronized access to an Execution result files. A lock is active while an
 * execution is running, and released once the execution is complete. A thread that collects the
 * execution results also locks on the execution id to prevent collecting results while the
 * execution is running. Lock is the integer id of the execution.
 */
@Component(service = ExecutionLock.class)
public class ExecutionLock {
	private ValueLock<Integer> executionIdLock = new ValueLock<>();

	public void lock(Execution e) throws InterruptedException {
		Validate.notNull(e);
		Validate.notNull(e.getId());
		executionIdLock.lock(e.getId());
	}

	public void unlock(Execution e) {
		Validate.notNull(e);
		Validate.notNull(e.getId());
		executionIdLock.unlock(e.getId());
	}
}
