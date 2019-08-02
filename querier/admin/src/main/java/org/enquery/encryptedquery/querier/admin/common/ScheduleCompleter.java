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
package org.enquery.encryptedquery.querier.admin.common;

import java.util.List;

import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
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
public class ScheduleCompleter implements Completer {

	@Reference
	private ScheduleRepository scheduleRepo;
	@Reference
	private DataSchemaRepository dataSchemaRepo;
	@Reference
	private DataSourceRepository dataSourceRepo;
	@Reference
	private QueryRepository queryRepo;

	@Override
	public int complete(Session session, CommandLine commandLine, List<String> candidates) {

		boolean found = false;

		DataSource dataSource = null;
		found = false;
		for (String arg : commandLine.getArguments()) {
			if (found) {
				dataSource = dataSourceRepo.findByName(arg);
			} else {
				found = "--datasource".equals(arg) || "-dsrc".equals(arg);
			}
		}

		Query query = null;
		found = false;
		for (String arg : commandLine.getArguments()) {
			if (found) {
				query = queryRepo.findByName(arg);
			} else {
				found = "--query".equals(arg) || "-q".equals(arg);
			}
		}

		StringsCompleter delegate = new StringsCompleter();
		for (Schedule ds : scheduleRepo.list(dataSource, query)) {
			delegate.getStrings().add(ds.getId().toString());
		}
		return delegate.complete(session, commandLine, candidates);
	}

}
