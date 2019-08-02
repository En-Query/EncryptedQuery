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
package org.enquery.encryptedquery.querier.admin.schedule;

import java.util.Collection;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.enquery.encryptedquery.querier.admin.common.BaseCommand;
import org.enquery.encryptedquery.querier.admin.common.DataSchemaCompleter;
import org.enquery.encryptedquery.querier.admin.common.DataSourceCompleter;
import org.enquery.encryptedquery.querier.admin.common.QueryCompleter;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.enquery.encryptedquery.querier.data.service.DataSourceRepository;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;

/**
 *
 */
@Service
@Command(scope = "schedule", name = "list", description = "Displays existing schedules.")
public class ListCommand extends BaseCommand implements Action {

	private static final int MAX_ERROR_MSG_LEN = 32;
	@Reference
	private DataSchemaRepository dataSchemaRepo;
	@Reference
	private DataSourceRepository dataSourceRepo;
	@Reference
	private QueryRepository queryRepo;
	@Reference
	private ScheduleRepository scheduleRepo;

	@Option(
			name = "--dataschema",
			required = false,
			aliases = {"-dsch", "Data Schema"},
			multiValued = false,
			description = "Data schema name. Only schedules belonging to this Data Schema will be displayed.")
	@Completion(DataSchemaCompleter.class)
	private String dataSchemaName;

	@Option(
			name = "--datasource",
			required = false,
			aliases = {"-dsrc", "Data Source."},
			multiValued = false,
			description = "The data source name. If provided, only schedules belonging to this Data Schema will be displayed.")
	@Completion(DataSourceCompleter.class)
	private String dataSourceName;

	@Option(
			name = "--query",
			required = false,
			aliases = {"-q", "Query."},
			multiValued = false,
			description = "Id of query. If provided, only schedules belonging to this Query will be displayed.")
	@Completion(QueryCompleter.class)
	private Integer queryId;

	private DataSchema dataSchema;
	private DataSource dataSource;
	private Query query;

	DataSchemaRepository getDataSchemaRepo() {
		return dataSchemaRepo;
	}


	void setDataSchemaRepo(DataSchemaRepository dataSchemaRepo) {
		this.dataSchemaRepo = dataSchemaRepo;
	}


	DataSourceRepository getDataSourceRepo() {
		return dataSourceRepo;
	}


	void setDataSourceRepo(DataSourceRepository dataSourceRepo) {
		this.dataSourceRepo = dataSourceRepo;
	}


	QueryRepository getQueryRepo() {
		return queryRepo;
	}


	void setQueryRepo(QueryRepository queryRepo) {
		this.queryRepo = queryRepo;
	}


	ScheduleRepository getScheduleRepo() {
		return scheduleRepo;
	}


	void setScheduleRepo(ScheduleRepository scheduleRepo) {
		this.scheduleRepo = scheduleRepo;
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


	Integer getQueryId() {
		return queryId;
	}


	void setQueryId(Integer queryId) {
		this.queryId = queryId;
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
			dataSource = dataSourceRepo.findForDataSchema(dataSchema, dataSourceName);
			if (dataSource == null) {
				printError(String.format("Data Source '%s' not found for data schema '%s'.", dataSourceName, dataSchemaName));
				return null;
			}
		}

		if (queryId != null) {
			query = queryRepo.find(queryId);
			if (query == null) {
				printError(String.format("Query '%s' not found.", queryId));
				return null;
			}
		}

		Collection<Schedule> list = scheduleRepo.list(dataSource, query);

		ShellTable table = new ShellTable();
		table.column("Id").alignRight();
		table.column("UUID").maxSize(32);
		table.column("Status");
		table.column("Data Source").alignCenter();
		table.column("Start time");
		table.column("Query");
		table.column("Error").maxSize(MAX_ERROR_MSG_LEN);

		for (Schedule sch : list) {
			table.addRow()
					.addContent(
							sch.getId(),
							sch.getUuid(),
							sch.getStatus(),
							(sch.getDataSource() == null) ? null : sch.getDataSource().getName(),
							toString(sch.getStartTime()),
							sch.getQuery().getName(),
							sch.getErrorMessage());
		}
		table.print(System.out);
		return null;
	}

}
