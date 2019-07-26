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
import java.io.InputStream;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.quartz.JobExecutionException;


public interface ExecutionUpdater {

	/**
	 * Creates new persistent JPA Execution along with its query from the Execution XML stream. Also
	 * schedules the execution with the Scheduler.
	 * 
	 * @param dataSchema Parent data schema. Not null.
	 * @param dataSource Parent data source. Not null.
	 * @param inputStream Execution XML input stream.
	 * @return Newly created Execution JPA entity. Not null.
	 * 
	 * @throws IOException Something failed.
	 * @throws JobExecutionException
	 */
	Execution create(DataSchema dataSchema, DataSource dataSource, InputStream inputStream) throws IOException, JobExecutionException;


	/**
	 * Creates new persistent JPA Execution along with its query from the Execution XML stream. Also
	 * schedules the execution with the Scheduler.
	 * 
	 * @param dataSchema Parent data schema. Not null.
	 * @param dataSource Parent data source. Not null.
	 * @param reader XML reader.
	 * @return Newly created Execution JPA entity. Not null.
	 * 
	 * @throws IOException Something failed.
	 * @throws JobExecutionException
	 */
	// Execution createFromXMLReader(DataSchema dataSchema, DataSource dataSource, XMLEventReader
	// reader) throws IOException, JobExecutionException;

	/**
	 * Stores/Schedules multiple executions with the Scheduler at once.
	 * 
	 * @param inputStream Execution Import XML input stream.
	 * @return List of newly created Execution JPA entities. Not null.
	 * 
	 * @throws IOException Something failed.
	 * @throws JobExecutionException
	 * @throws XMLStreamException
	 */
	List<Execution> createFromImportXML(InputStream inputStream) throws IOException, JobExecutionException, XMLStreamException;

}
