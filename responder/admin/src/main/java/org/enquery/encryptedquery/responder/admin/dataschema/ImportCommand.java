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
import org.enquery.encryptedquery.responder.admin.common.BaseCommand;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.transformation.DataSchemaTypeConverter;
import org.enquery.encryptedquery.xml.schema.DataSchema;

/**
 *
 */
@Service
@Command(scope = "dataschema", name = "import", description = "Import data schema.")
public class ImportCommand extends BaseCommand implements Action {

	@Reference
	private DataSchemaTypeConverter converter;
	@Reference
	private DataSchemaService dataSchemaRepo;

	@Option(name = "--input-file",
			required = true,
			aliases = {"-i", "Input file."},
			multiValued = false,
			description = "Input data schema file to import.")
	@Completion(FileCompleter.class)
	private String inputFile;


	String getInputFile() {
		return inputFile;
	}

	void setInputFile(String inputFile) {
		this.inputFile = inputFile;
	}

	DataSchemaTypeConverter getConverter() {
		return converter;
	}

	void setConverter(DataSchemaTypeConverter converter) {
		this.converter = converter;
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
			DataSchema xmlDSchema = converter.unmarshalDataSchema(is);
			org.enquery.encryptedquery.responder.data.entity.DataSchema jpaEntity = converter.toDataSchemaJPAEntity(xmlDSchema);
			org.enquery.encryptedquery.responder.data.entity.DataSchema added = dataSchemaRepo.addOrUpdate(jpaEntity);

			printSuccess(String.format("'%s' data schema added.\n", added.getName()));
		}

		return null;
	}
}
