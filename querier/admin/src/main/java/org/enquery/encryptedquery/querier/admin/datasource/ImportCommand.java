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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.completers.FileCompleter;
import org.encryptedquery.querier.business.DataSourceImporter;
import org.enquery.encryptedquery.querier.admin.common.BaseCommand;


/**
 *
 */
@Service
@Command(scope = "datasource", name = "import", description = "Import data sources.")
public class ImportCommand extends BaseCommand implements Action {

	@Reference
	private DataSourceImporter updater;

	@Option(name = "--input-file",
			required = true,
			aliases = {"-i", "Input file."},
			multiValued = false,
			description = "Input data source file to import.")
	@Completion(FileCompleter.class)
	private String inputFile;


	String getInputFile() {
		return inputFile;
	}

	void setInputFile(String inputFile) {
		this.inputFile = inputFile;
	}

	DataSourceImporter getUpdater() {
		return updater;
	}

	void setUpdater(DataSourceImporter updater) {
		this.updater = updater;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.karaf.shell.api.action.Action#execute()
	 */
	@Override
	public Object execute() throws Exception {

		if (inputFile.startsWith("~")) {
			inputFile = inputFile.replace("~", System.getProperty("user.home"));
		}

		Path file = Paths.get(inputFile);
		if (!Files.exists(file)) {
			printError(String.format("File '%s' not found.", file));
			return null;
		}

		try (InputStream is = Files.newInputStream(file)) {
			updater.importDataSources(is);
		}

		printSuccess(String.format("Successfully imported '%s'", inputFile));

		return null;
	}
}
