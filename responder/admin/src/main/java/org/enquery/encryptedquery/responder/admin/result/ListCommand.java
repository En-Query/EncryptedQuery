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
package org.enquery.encryptedquery.responder.admin.result;

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
import org.enquery.encryptedquery.responder.admin.common.ExecutionCompleter;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.entity.Result;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.enquery.encryptedquery.responder.data.service.ResultRepository;

/**
 *
 */
@Service
@Command(scope = "result", name = "list", description = "Displays available results for a given execution.")
public class ListCommand extends BaseCommand implements Action {

	@Reference
	private ExecutionRepository executionRepo;
	@Reference
	private DataSchemaService dataSchemaRepo;
	@Reference
	private DataSourceRegistry dataSourceRepo;
	@Reference
	private ResultRepository resultRepo;

	@Option(
			name = "--dataschema",
			required = false,
			aliases = {"-dsch", "Data Schema"},
			multiValued = false,
			description = "Data schema name. Only results belonging to this Data Schema will be displayed.")
	@Completion(DataSchemaCompleter.class)
	private String dataSchemaName;


	@Option(
			name = "--datasource",
			required = false,
			aliases = {"-dsrc", "Data Source."},
			multiValued = false,
			description = "The data source name. If provided, only results belonging to this Data Schema will be displayed.")
	@Completion(DataSourceCompleter.class)
	private String dataSourceName;

	@Option(
			name = "--execution",
			required = false,
			aliases = {"-e", "Execution id."},
			multiValued = false,
			description = "The execution id, only results belonging to this Execution will be displayed.")
	@Completion(ExecutionCompleter.class)
	private String executionId;

	private DataSchema dataSchema;
	private DataSource dataSource;
	private Execution execution;


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
				printError(String.format("Data Schema '%s' not found.", dataSchemaName));
				return null;
			}
		}

		if (dataSourceName != null) {
			if (dataSchema != null) {
				dataSource = dataSourceRepo.findForDataSchema(dataSchema, dataSourceName);
			} else {
				dataSource = dataSourceRepo.find(dataSourceName);
			}
			if (dataSource == null) {
				printError(String.format("Data Source '%s' not found for data schema '%s'.", dataSourceName, dataSchemaName));
				return null;
			}
		}


		if (executionId != null) {
			if (executionId.matches("[a-fA-F0-9]{32}")) {
				// must be guid
				execution = executionRepo.findByUUID(executionId);
			} else {
				execution = executionRepo.find(Integer.valueOf(executionId));
			}
			if (execution == null) {
				printError("Execution not found.");
				return null;
			}
		}
		Collection<Result> list = resultRepo.listFilteredByDataSchemaDataSourceAndExecution(dataSchema, dataSource, execution);


		ShellTable table = new ShellTable();
		table.column("Id").alignRight();
		table.column("Execution Id");
		table.column("Created");
		table.column("Window Start");
		table.column("Window End");
		table.column("Payload URL");

		for (Result ds : list) {
			table.addRow()
					.addContent(
							ds.getId(),
							ds.getExecution().getId(),
							toString(ds.getCreationTime()),
							toString(ds.getWindowStartTime()),
							toString(ds.getWindowEndTime()),
							ds.getPayloadUrl());
		}
		table.print(System.out);
		return null;
	}

}
