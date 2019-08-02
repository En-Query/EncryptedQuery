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
package org.enquery.encryptedquery.responder.admin.result;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.Validate;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.completers.FileCompleter;
import org.enquery.encryptedquery.responder.admin.common.BaseCommand;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.entity.Result;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.enquery.encryptedquery.responder.data.service.ResultRepository;
import org.enquery.encryptedquery.responder.data.transformation.ExecutionTypeConverter;
import org.enquery.encryptedquery.responder.data.transformation.ResultTypeConverter;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.enquery.encryptedquery.xml.schema.ExecutionResources;
import org.enquery.encryptedquery.xml.schema.ResultResource;
import org.enquery.encryptedquery.xml.transformation.ResultExportWriter;

/**
 *
 */
@Service
@Command(scope = "result", name = "export", description = "Export results in XML format to be imported by Querier.")
public class ExportCommand extends BaseCommand implements Action {

	@Reference
	private ExecutionRepository executionRepo;
	@Reference
	private DataSchemaService dataSchemaRepo;
	@Reference
	private DataSourceRegistry dataSourceRepo;
	@Reference
	private ExecutionTypeConverter executionConverter;
	@Reference
	private ResultRepository resultRepo;
	@Reference
	private ExecutorService threadPool;
	@Reference
	private ResultTypeConverter resultConverter;

	@Option(name = "--input-file",
			required = true,
			aliases = {"-i", "Input file."},
			multiValued = false,
			description = "Input file as returned by 'execution:import' or 'execution:export' commands.")
	@Completion(FileCompleter.class)
	private String inputFileName;

	@Option(name = "--output-file",
			required = true,
			aliases = {"-o", "Output file."},
			multiValued = false,
			description = "Output file to write the results to.")
	@Completion(FileCompleter.class)
	private String outputFileName;

	private int count;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.karaf.shell.api.action.Action#execute()
	 */
	@Override
	public Object execute() throws Exception {
		Validate.notNull(inputFileName);
		Validate.notNull(outputFileName);

		if (inputFileName.startsWith("~")) {
			String home = System.getProperty("user.home");
			inputFileName = inputFileName.replace("~", home);
		}

		if (outputFileName.startsWith("~")) {
			outputFileName = outputFileName.replace("~", System.getProperty("user.home"));
		}

		Path inputFile = Paths.get(inputFileName);
		if (!Files.exists(inputFile)) {
			printError(String.format("File '%s' not found.", inputFile));
			return null;
		}

		Path outputFile = Paths.get(outputFileName);

		if (Files.exists(outputFile)) {
			Boolean overwrite = inputBoolean(String.format("File '%s' already exists. Overwrite? (yes/no): ", outputFile));
			if (!Boolean.TRUE.equals(overwrite)) return null;
		}

		try (InputStream inputStream = Files.newInputStream(inputFile);
				OutputStream outputStream = Files.newOutputStream(outputFile);
				ResultExportWriter writer = new ResultExportWriter(outputStream);) {

			writer.begin(true);
			ExecutionResources executionResources = executionConverter.unmarshalExecutionResources(inputStream);
			for (ExecutionResource er : executionResources.getExecutionResource()) {
				processExecution(er.getExecution().getUuid(), writer);
			}
			writer.end();
		}

		out.printf("%d results exported to '%s'.\n", count, outputFile);
		return null;
	}

	/**
	 * @param extractor
	 * @param outputStream
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	private void processExecution(String uuid, ResultExportWriter writer) throws IOException, XMLStreamException {
		Execution execution = executionRepo.findByUUID(uuid);
		if (execution == null) {
			printError(String.format("Execution uuid '%s' not found.", uuid));
			return;
		}

		DataSource dataSource = dataSourceRepo.find(execution.getDataSourceName());
		if (dataSource == null) {
			printError(String.format("Data Source '%s' not found for execution '%s'.", execution.getDataSourceName(), uuid));
			return;
		}

		DataSchema dataSchema = execution.getDataSchema();
		if (dataSchema == null) {
			printError(String.format("Data Schema not found for execution '%s'.", uuid));
			return;
		}

		ExecutionResource executionResource = executionConverter.toXMLExecution(execution);

		writer.beginItem();
		writer.writeExecutionResource(executionResource);

		Collection<Result> list = resultRepo.listForExecution(execution);
		if (!list.isEmpty()) {
			writer.beginResults();
			for (Result r : list) {
				ResultResource resource = resultConverter.toXMLResultResource(r, dataSchema, dataSource, execution);
				InputStream payloadInputStream = resultRepo.payloadInputStream(r.getId());
				writer.writeResultResource(resource, payloadInputStream);
				++count;
			}
			writer.endResults();
		}
		writer.endItem();
	}
}
