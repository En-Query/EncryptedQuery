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
import org.apache.karaf.shell.support.table.ShellTable;
import org.enquery.encryptedquery.responder.admin.common.BaseCommand;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.enquery.encryptedquery.responder.data.transformation.ExecutionTypeConverter;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.enquery.encryptedquery.xml.schema.ExecutionResources;

/**
 *
 */
@Service
@Command(scope = "execution", name = "status", description = "Prints status of executions given file.")
public class StatusCommand extends BaseCommand implements Action {

	@Reference
	private ExecutionRepository executionRepo;
	@Reference
	private ExecutionTypeConverter executionConverter;

	@Option(name = "--input-file",
			required = true,
			aliases = {"-i", "Input file."},
			multiValued = false,
			description = "Input file as returned by 'execution:import' or 'execution:export' commands.")
	@Completion(FileCompleter.class)
	private String inputFileName;

	ExecutionRepository getExecutionRepo() {
		return executionRepo;
	}

	void setExecutionRepo(ExecutionRepository executionRepo) {
		this.executionRepo = executionRepo;
	}

	ExecutionTypeConverter getExecutionConverter() {
		return executionConverter;
	}

	void setExecutionConverter(ExecutionTypeConverter executionConverter) {
		this.executionConverter = executionConverter;
	}

	String getInputFileName() {
		return inputFileName;
	}

	void setInputFileName(String inputFileName) {
		this.inputFileName = inputFileName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.karaf.shell.api.action.Action#execute()
	 */
	@Override
	public Object execute() throws Exception {

		if (inputFileName.startsWith("~")) {
			String home = System.getProperty("user.home");
			inputFileName = inputFileName.replace("~", home);
		}

		Path inputFile = Paths.get(inputFileName);
		if (!Files.exists(inputFile)) {
			printError(String.format("File '%s' not found.", inputFile));
			return null;
		}

		Path outputFile = makeOutputFile("export-executions", inputFile.getParent());
		if (Files.exists(outputFile)) {
			Boolean overwrite = inputBoolean(String.format("File '%s' already exists. Overwrite? (yes/no): ", outputFile));
			if (!Boolean.TRUE.equals(overwrite)) return null;
		}

		ShellTable table = new ShellTable();
		table.column("Id").alignRight();
		table.column("UUID");
		table.column("Schedule Time");
		table.column("Start Time");
		table.column("End Time");
		table.column("Canceled");
		table.column("Error Msg.");

		try (InputStream inputStream = Files.newInputStream(inputFile)) {
			ExecutionResources executionResources = executionConverter.unmarshalExecutionResources(inputStream);
			for (ExecutionResource er : executionResources.getExecutionResource()) {
				String uuid = er.getExecution().getUuid();
				Execution e = executionRepo.findByUUID(uuid);
				if (e == null) {
					printError(String.format("Execution with UUID '%s' not found.", uuid));
					continue;
				}
				table.addRow()
						.addContent(
								e.getId(),
								e.getUuid(),
								toString(e.getScheduleTime()),
								toString(e.getStartTime()),
								toString(e.getEndTime()),
								e.isCanceled(),
								e.getErrorMsg());
			}
		}

		table.print(System.out);

		return null;
	}


}
