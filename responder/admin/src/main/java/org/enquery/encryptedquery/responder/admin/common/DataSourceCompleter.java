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
package org.enquery.encryptedquery.responder.admin.common;

import java.util.List;

import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;

/**
 *
 */
@Service
public class DataSourceCompleter implements Completer {

	@Reference
	private DataSourceRegistry dataSourceRepo;

	@Override
	public int complete(Session session, CommandLine commandLine, List<String> candidates) {
		StringsCompleter delegate = new StringsCompleter();
		for (DataSource ds : dataSourceRepo.list()) {
			delegate.getStrings().add(ds.getName());
		}
		return delegate.complete(session, commandLine, candidates);
	}

}
