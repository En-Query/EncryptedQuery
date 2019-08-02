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
package org.enquery.encryptedquery.querier.admin.datasource;

import java.util.Collection;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.enquery.encryptedquery.querier.admin.common.BaseCommand;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.service.DataSourceRepository;

/**
 *
 */
@Service
@Command(scope = "datasource", name = "list", description = "Displays available Data Sources.")
public class ListCommand extends BaseCommand implements Action {


	@Reference
	private DataSourceRepository dataSourceRepo;

	DataSourceRepository getDataSchemaRepo() {
		return dataSourceRepo;
	}

	void setDataSchemaRepo(DataSourceRepository dataSchemaRepo) {
		this.dataSourceRepo = dataSchemaRepo;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.karaf.shell.api.action.Action#execute()
	 */
	@Override
	public Object execute() throws Exception {
		Collection<DataSource> list = dataSourceRepo.list();

		ShellTable table = new ShellTable();
		table.column("Id").alignRight();
		table.column("Name").maxSize(32);
		table.column("Type");
		table.column("Description").maxSize(32);
		table.column("Data Schema").maxSize(32);
		table.column("Responder URI");
		table.column("Executions URI");

		for (DataSource ds : list) {
			table.addRow()
					.addContent(
							ds.getId(),
							ds.getName(),
							ds.getType(),
							ds.getDescription(),
							ds.getDataSchema().getName(),
							ds.getResponderUri(),
							ds.getExecutionsUri());
		}
		table.print(System.out);

		return null;
	}

}
