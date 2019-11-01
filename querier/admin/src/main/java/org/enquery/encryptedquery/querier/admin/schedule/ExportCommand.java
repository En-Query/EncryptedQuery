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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.completers.FileCompleter;
import org.enquery.encryptedquery.querier.admin.common.BaseCommand;
import org.enquery.encryptedquery.querier.data.transformation.ExecutionExporter;

/**
 *
 */
@Service
@Command(scope = "schedule", name = "export", description = "Export schedules in XML format to be imported by Responder.")
public class ExportCommand extends BaseCommand implements Action {

	@Reference
	private ExecutionExporter executionExporter;

	@Argument(name = "schedule ids",
			index = 0,
			required = true,
			// aliases = {"-s", "Schedule IDs."},
			multiValued = true,
			description = "Ids of schedules to export.")
	private List<Integer> scheduleIds;

	@Option(name = "--output-file",
			required = true,
			aliases = {"-o", "Output file."},
			multiValued = false,
			description = "Output file to write the results to.")
	@Completion(FileCompleter.class)
	private String outputFileName;


	ExecutionExporter getExecutionExporter() {
		return executionExporter;
	}

	void setExecutionExporter(ExecutionExporter executionExporter) {
		this.executionExporter = executionExporter;
	}

	List<Integer> getScheduleIds() {
		return scheduleIds;
	}

	void setScheduleIds(List<Integer> scheduleIds) {
		this.scheduleIds = scheduleIds;
	}

	String getOutputFileName() {
		return outputFileName;
	}

	void setOutputFileName(String outputFileName) {
		this.outputFileName = outputFileName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.karaf.shell.api.action.Action#execute()
	 */
	@Override
	public Object execute() throws Exception {

		Validate.notNull(outputFileName);
		if (outputFileName != null && outputFileName.startsWith("~")) {
			outputFileName = outputFileName.replace("~", System.getProperty("user.home"));
		}

		Path outputFile = Paths.get(outputFileName);

		if (Files.exists(outputFile)) {
			Boolean overwrite = inputBoolean(String.format("File '%s' already exists. Overwrite? (yes/no): ", outputFile));
			if (!Boolean.TRUE.equals(overwrite)) return null;
		}

		try (InputStream inputStream = executionExporter.streamByScheduleIds(scheduleIds);
				OutputStream outputStream = Files.newOutputStream(outputFile);) {
			IOUtils.copy(inputStream, outputStream);
		}

		out.printf("%d schedules exported to '%s'.", scheduleIds.size(), outputFile);
		return null;
	}

}
