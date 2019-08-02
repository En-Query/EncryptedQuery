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
package org.enquery.encryptedquery.responder.admin.dataschema;

import java.util.Collection;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.enquery.encryptedquery.responder.admin.common.BaseCommand;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;

/**
 *
 */
@Service
@Command(scope = "dataschema", name = "list", description = "Displays available Data Schemas.")
public class ListCommand extends BaseCommand implements Action {

	@Reference
	private DataSchemaService dataSchemaRepo;

	DataSchemaService getDataSchemaRepo() {
		return dataSchemaRepo;
	}

	void setDataSchemaRepo(DataSchemaService dataSchemaRepo) {
		this.dataSchemaRepo = dataSchemaRepo;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.karaf.shell.api.action.Action#execute()
	 */
	@Override
	public Object execute() throws Exception {
		Collection<DataSchema> list = getDataSchemaRepo().list();

		ShellTable table = new ShellTable();
		table.column("Id").alignRight();
		table.column("Name").maxSize(32);
		table.column("Number of fields");

		for (DataSchema e : list) {
			table.addRow()
					.addContent(
							e.getId(),
							e.getName(),
							e.getFields().size());
		}
		table.print(System.out);
		return null;
	}

}
