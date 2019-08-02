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
import java.util.Collection;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.completers.FileCompleter;
import org.enquery.encryptedquery.responder.admin.common.BaseCommand;
import org.enquery.encryptedquery.responder.business.execution.ExecutionUpdater;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.transformation.ExecutionTypeConverter;

/**
 *
 */
@Service
@Command(scope = "execution", name = "import", description = "Import executions.")
public class ImportCommand extends BaseCommand implements Action {

	@Reference
	private ExecutionTypeConverter converter;
	@Reference
	private ExecutionUpdater updater;

	@Option(name = "--input-file",
			required = true,
			aliases = {"-i", "Input file."},
			multiValued = false,
			description = "Input Execution file to add.")
	@Completion(FileCompleter.class)
	private String inputFile;

	@Option(name = "--output-file",
			required = false,
			aliases = {"-o", "Optional output file."},
			multiValued = false,
			description = "Output file to write the result of this operation to.")
	@Completion(FileCompleter.class)
	private String outputFile;


	String getInputFile() {
		return inputFile;
	}

	void setInputFile(String inputFile) {
		this.inputFile = inputFile;
	}

	ExecutionUpdater getUpdater() {
		return updater;
	}

	void setUpdater(ExecutionUpdater updater) {
		this.updater = updater;
	}

	ExecutionTypeConverter getConverter() {
		return converter;
	}

	void setConverter(ExecutionTypeConverter converter) {
		this.converter = converter;
	}

	String getOutputFile() {
		return outputFile;
	}

	void setOutputFile(String outputFile) {
		this.outputFile = outputFile;
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

		if (outputFile != null && outputFile.startsWith("~")) {
			outputFile = outputFile.replace("~", System.getProperty("user.home"));
		}

		Path file = Paths.get(inputFile);
		if (!Files.exists(file)) {
			printError(String.format("File '%s' not found.", file));
			return null;
		}

		Path outFile = null;
		if (outputFile == null) {
			outFile = makeOutputFile("add-executions", file.getParent());
		} else {
			outFile = Paths.get(outputFile);
		}

		if (Files.exists(outFile)) {
			Boolean overwrite = inputBoolean(String.format("File '%s' already exists. Overwrite? (yes/no): ", outFile));
			if (!Boolean.TRUE.equals(overwrite)) return null;
		}

		Collection<Execution> executions = null;
		try (InputStream is = Files.newInputStream(file)) {
			executions = updater.createFromImportXML(is);
		}

		printSuccess(String.format("%d execution added.", executions.size()));


		// generate output file that can be used to query status and export results
		// ExecutionUUID uuids = new ExecutionUUID();
		// executions.stream().forEach(e -> uuids.getUuid().add(e.getUuid()));

		try (OutputStream os = Files.newOutputStream(outFile)) {
			converter.marshal(converter.toXMLExecutions(executions), os);
		}
		printSuccess(String.format("Response file '%s' created.\n", outFile));

		return null;
	}
}
