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
package org.enquery.encryptedquery.responder.admin.datasource;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.completers.FileCompleter;
import org.enquery.encryptedquery.responder.admin.common.BaseCommand;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.transformation.DataSourceExportConverter;

/**
 *
 */
@Service
@Command(scope = "datasource", name = "export", description = "Export Data Sources and Data Schemas in XML format for importing into Querier.")
public class ExportCommand extends BaseCommand implements Action {

	@Reference
	private DataSourceRegistry dataSourceRepo;
	@Reference
	private DataSchemaService dataSchemaRepo;
	@Reference
	private DataSourceExportConverter converter;

	@Option(name = "--output-dir",
			required = true,
			aliases = {"-o", "Output directory."},
			multiValued = false,
			description = "Output directory to write the export file to.")
	@Completion(FileCompleter.class)
	private String outputDir;


	DataSourceRegistry getDataSourceRepo() {
		return dataSourceRepo;
	}

	void setDataSourceRepo(DataSourceRegistry dataSourceRepo) {
		this.dataSourceRepo = dataSourceRepo;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.karaf.shell.api.action.Action#execute()
	 */
	@Override
	public Object execute() throws Exception {
		if (outputDir.startsWith("~")) {
			String home = System.getProperty("user.home");
			outputDir = outputDir.replace("~", home);
		}

		Collection<DataSource> list = getDataSourceRepo().list();

		if (list.isEmpty()) {
			printError("No data source found.");
			return null;
		}

		final Path dir = Paths.get(outputDir);
		final Path file = makeOutputFile("export-datasources", dir);
		if (Files.exists(file)) {
			Boolean overwrite = inputBoolean(String.format("File '%s' already exists. Overwrite? (yes/no): ", file));
			if (!Boolean.TRUE.equals(overwrite)) return null;
		}

		try (OutputStream os = Files.newOutputStream(file)) {
			converter.marshal(converter.toXML(dataSchemaRepo.list(), dataSourceRepo.list()), os);
		}

		out.printf("%d Data Sources exported to '%s'.\n", list.size(), file);
		return null;
	}

}
