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
package org.enquery.encryptedquery.responder.data.service;

import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;

import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.entity.Result;


public interface ResultRepository {

	Result find(int id);

	Result findForExecution(Execution execution, int id);

	Collection<Result> listForExecution(Execution execution);

	/**
	 * Add a new result including its payload to an Execution.
	 * 
	 * @param execution Execution this result belongs to.
	 * @param inputStream The Response XML of this execution.
	 * @param startTime Start time for this result. May be null.
	 * @param endTime End time of this result. May be null.
	 * @return Created Result. Not null
	 */
	/**
	 * @param execution
	 * @param inputStream
	 * @return
	 */
	Result add(Execution execution, InputStream inputStream, Instant startTime, Instant endTime);

	Result add(Result r);

	Result update(Result r);

	void delete(Result r);

	void deleteAll();

	InputStream payloadInputStream(int resultId);
}
