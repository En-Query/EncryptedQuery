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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.completers.FileCompleter;
import org.enquery.encryptedquery.responder.admin.common.BaseCommand;
import org.enquery.encryptedquery.responder.admin.common.DataSchemaCompleter;
import org.enquery.encryptedquery.responder.admin.common.DataSourceCompleter;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.enquery.encryptedquery.responder.data.transformation.ExecutionTypeConverter;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.enquery.encryptedquery.xml.schema.ExecutionResources;

/**
 *
 */
@Service
@Command(scope = "execution", name = "export", description = "Export executions in XML format.")
public class ExportCommand extends BaseCommand implements Action {

	@Reference
	private DataSchemaService dataSchemaRepo;
	@Reference
	private DataSourceRegistry dataSourceRepo;
	@Reference
	private ExecutionRepository executionRepo;
	@Reference
	private ExecutionTypeConverter executionConverter;

	@Option(name = "--input-file",
			required = false,
			aliases = {"-i", "Input file."},
			multiValued = false,
			description = "Input execution file returned by the `add` command.")
	@Completion(FileCompleter.class)
	private String inputFileName;

	@Option(name = "--output-file",
			required = false,
			aliases = {"-o", "Output file."},
			multiValued = false,
			description = "Output file to write the executions to..")
	@Completion(FileCompleter.class)
	private String outputFileName;


	@Option(
			name = "--dataschema",
			required = false,
			aliases = {"-dsch", "Data Schema"},
			multiValued = false,
			description = "Data schema name. If provided, only executions belonging to this Data Schema will be displayed.")
	@Completion(DataSchemaCompleter.class)
	private String dataSchemaName;


	@Option(
			name = "--datasource",
			required = false,
			aliases = {"-dsrc", "Data Source."},
			multiValued = false,
			description = "The data source name. If provided, only executions belonging to this Data Schema will be displayed.")
	@Completion(DataSourceCompleter.class)
	private String dataSourceName;


	@Option(
			name = "--incomplete",
			required = false,
			aliases = {"-i", "Incomplete only."},
			multiValued = false,
			description = "Display incomplete executions only.")
	private boolean incomplete;

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
		final String home = System.getProperty("user.home");

		Collection<Execution> list = new ArrayList<>();
		Path inputFile = null;
		Path outputFile = null;

		if (outputFileName != null) {
			if (outputFileName.startsWith("~")) {
				outputFileName = outputFileName.replace("~", home);
			}
		}

		if (inputFileName != null) {
			if (inputFileName.startsWith("~")) {
				inputFileName = inputFileName.replace("~", home);
			}

			inputFile = Paths.get(inputFileName);
			if (!Files.exists(inputFile)) {
				printError(String.format("File '%s' not found.", inputFile));
				return null;
			}

			try (InputStream inputStream = Files.newInputStream(inputFile)) {
				ExecutionResources executionResources = executionConverter.unmarshalExecutionResources(inputStream);
				for (ExecutionResource er : executionResources.getExecutionResource()) {
					String uuid = er.getExecution().getUuid();
					Execution ex = executionRepo.findByUUID(uuid);
					if (ex == null) {
						printError(String.format("Execution with UUID '%s' not found.", uuid));
						continue;
					}
					list.add(ex);
				}
			}
		} else {
			DataSchema dataSchema = null;
			DataSource dataSource = null;

			if (dataSchemaName != null) {
				dataSchema = dataSchemaRepo.findByName(dataSchemaName);
				if (dataSchema == null) {
					printError("Data Schema not found.");
					return null;
				}
			}
			if (dataSourceName != null) {
				dataSource = dataSourceRepo.find(dataSourceName);
				if (dataSource == null) {
					printError("Data Source not found.");
					return null;
				}
			}

			list = executionRepo.list(dataSchema, dataSource, incomplete);
		}

		if (outputFileName != null) {
			outputFile = Paths.get(outputFileName);
		} else {
			if (inputFile != null) {
				outputFile = makeOutputFile("export-executions", inputFile.getParent());
			} else {
				outputFile = makeOutputFile("export-executions", Paths.get("data"));
			}
		}

		if (Files.exists(outputFile)) {
			Boolean overwrite = inputBoolean(String.format("File '%s' already exists. Overwrite? (yes/no): ", outputFile));
			if (!Boolean.TRUE.equals(overwrite)) return null;
		}

		ExecutionResources xml = executionConverter.toXMLExecutions(list);
		try (OutputStream os = Files.newOutputStream(outputFile)) {
			executionConverter.marshal(xml, os);
		}

		out.printf("%d executions exported to '%s'.\n", list.size(), outputFile);
		return null;
	}

}
