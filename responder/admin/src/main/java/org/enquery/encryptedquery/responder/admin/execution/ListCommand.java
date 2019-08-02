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
package org.enquery.encryptedquery.responder.admin.execution;

import java.util.Collection;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.enquery.encryptedquery.responder.admin.common.BaseCommand;
import org.enquery.encryptedquery.responder.admin.common.DataSchemaCompleter;
import org.enquery.encryptedquery.responder.admin.common.DataSourceCompleter;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;

/**
 *
 */
@Service
@Command(scope = "execution", name = "list", description = "List executions .")
public class ListCommand extends BaseCommand implements Action {

	private static final int MAX_ERROR_MSG_LEN = 32;
	@Reference
	private ExecutionRepository executionRepo;
	@Reference
	private DataSchemaService dataSchemaRepo;
	@Reference
	private DataSourceRegistry dataSourceRepo;

	@Option(
			name = "--dataschema",
			required = false,
			aliases = {"-dsch", "Data Schema"},
			multiValued = false,
			description = "Data schema name. If provided, only executions belonging to this Data Schema will be displayed.")
	@Completion(DataSchemaCompleter.class)
	private String dataSchemaName;


	@Option(
			name = "--datasource",
			required = false,
			aliases = {"-dsrc", "Data Source."},
			multiValued = false,
			description = "The data source name. If provided, only executions belonging to this Data Schema will be displayed.")
	@Completion(DataSourceCompleter.class)
	private String dataSourceName;

	@Option(
			name = "--incomplete",
			required = false,
			aliases = {"-i", "Incomplete only."},
			multiValued = false,
			description = "Display incomplete executions only.")
	private boolean incomplete;

	private DataSchema dataSchema;
	private DataSource dataSource;

	ExecutionRepository getExecutionRepo() {
		return executionRepo;
	}

	void setExecutionRepo(ExecutionRepository executionRepo) {
		this.executionRepo = executionRepo;
	}

	DataSchemaService getDataSchemaRepo() {
		return dataSchemaRepo;
	}

	void setDataSchemaRepo(DataSchemaService dataSchemaRepo) {
		this.dataSchemaRepo = dataSchemaRepo;
	}

	DataSourceRegistry getDataSourceRepo() {
		return dataSourceRepo;
	}

	void setDataSourceRepo(DataSourceRegistry dataSourceRepo) {
		this.dataSourceRepo = dataSourceRepo;
	}

	String getDataSchemaName() {
		return dataSchemaName;
	}

	void setDataSchemaName(String dataSchemaName) {
		this.dataSchemaName = dataSchemaName;
	}

	String getDataSourceName() {
		return dataSourceName;
	}

	void setDataSourceName(String dataSourceName) {
		this.dataSourceName = dataSourceName;
	}

	boolean isIncomplete() {
		return incomplete;
	}

	void setIncomplete(boolean incomplete) {
		this.incomplete = incomplete;
	}

	DataSchema getDataSchema() {
		return dataSchema;
	}

	void setDataSchema(DataSchema dataSchema) {
		this.dataSchema = dataSchema;
	}

	DataSource getDataSource() {
		return dataSource;
	}

	void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.karaf.shell.api.action.Action#execute()
	 */
	@Override
	public Object execute() throws Exception {
		if (dataSchemaName != null) {
			dataSchema = dataSchemaRepo.findByName(dataSchemaName);
			if (dataSchema == null) {
				printError("Data Schema not found.");
				return null;
			}
		}
		if (dataSourceName != null) {
			dataSource = dataSourceRepo.find(dataSourceName);
			if (dataSource == null) {
				printError("Data Source not found.");
				return null;
			}
		}

		Collection<Execution> list = null;

		list = executionRepo.list(dataSchema, dataSource, incomplete);

		ShellTable table = new ShellTable();
		table.column("Id").alignRight();
		table.column("UUID").maxSize(32);
		table.column("Data Source").alignCenter();
		table.column("Scheduled");
		table.column("Started");
		table.column("Completed");
		table.column("Canceled");
		table.column("Error").maxSize(MAX_ERROR_MSG_LEN);

		for (Execution e : list) {
			table.addRow()
					.addContent(
							e.getId(),
							e.getUuid(),
							e.getDataSourceName(),
							toString(e.getScheduleTime()),
							toString(e.getStartTime()),
							toString(e.getEndTime()),
							e.isCanceled(),
							e.getErrorMsg());
		}
		table.print(System.out);


		return null;
	}


}
