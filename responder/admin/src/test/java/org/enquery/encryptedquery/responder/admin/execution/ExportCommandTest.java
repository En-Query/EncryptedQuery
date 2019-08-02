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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.karaf.shell.api.console.Session;
import org.enquery.encryptedquery.responder.admin.BaseCommandTest;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.enquery.encryptedquery.responder.data.transformation.ExecutionTypeConverter;
import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.enquery.encryptedquery.xml.schema.ExecutionResources;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class ExportCommandTest extends BaseCommandTest {

	private static final String SINGLE_EXECUTION_SRC = "target/test-classes/execution.uuid.single.xml";
	private static final String SINGLE_EXECUTION = "target/execution.uuid.single.xml";

	private static final String MULT_EXECUTION_SRC = "target/test-classes/executions.uuid.mult.xml";
	private static final String MULT_EXECUTION = "target/executions.uuid.mult.xml";
	// private static final String OUTPUT_FILE_NAME = "target/executions.export.xml";

	private ExecutionTypeConverter converter;
	private ExecutionRepository executionRepo;

	@Before
	public void prepare() throws IOException, JAXBException, XMLStreamException, FactoryConfigurationError {
		// delete any result file
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(
				Paths.get("target"), "export-executions*.xml")) {
			dirStream.forEach(path -> FileUtils.deleteQuietly(path.toFile()));
		}

		// FileUtils.deleteQuietly(new File(OUTPUT_FILE_NAME));
		FileUtils.copyFileToDirectory(new File(SINGLE_EXECUTION_SRC), new File("target"));
		FileUtils.copyFileToDirectory(new File(MULT_EXECUTION_SRC), new File("target"));

		mockConverter();
		mockRepository();
	}


	@Test
	public void testSingle() throws Exception {
		final Session mockSession = mock(Session.class);

		runCommand(mockSession, SINGLE_EXECUTION);

		assertTrue(getConsoleOutput()
				.contains("1 executions exported to"));

		Path outFile = null;
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("target"), "export-executions*.xml")) {
			outFile = dirStream.iterator().next();
		}

		try (InputStream is = Files.newInputStream(outFile)) {
			List<String> outputFileLines = IOUtils.readLines(is, "UTF-8");
			assertEquals(1, outputFileLines.size());
			assertEquals("3e7c5e594e424cde88c9e531a5dccb06", outputFileLines.get(0));
		}
	}

	@Test
	public void testMultiple() throws Exception {
		final Session mockSession = mock(Session.class);

		runCommand(mockSession, MULT_EXECUTION);

		assertTrue(getConsoleOutput().contains("2 executions exported to"));

		Path outFile = null;
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("target"), "export-executions*.xml")) {
			outFile = dirStream.iterator().next();
		}
		try (InputStream is = Files.newInputStream(outFile)) {
			List<String> outputFileLines = IOUtils.readLines(is, "UTF-8");
			assertEquals(2, outputFileLines.size());
			assertEquals("3e7c5e594e424cde88c9e531a5dccb06", outputFileLines.get(0));
			assertEquals("5e7c5e594e424cde88c9e531a5dccb06", outputFileLines.get(1));
		}
	}

	private void runCommand(final Session mockSession, String inputFile) throws Exception {
		ExportCommand command = new ExportCommand();
		command.setSession(mockSession);
		command.setInputFileName(inputFile);
		command.setExecutionConverter(converter);
		command.setExecutionRepo(executionRepo);
		command.execute();
	}

	@SuppressWarnings("unchecked")
	private void mockConverter() throws JAXBException, UnsupportedEncodingException, IOException, XMLStreamException, FactoryConfigurationError {
		converter = mock(ExecutionTypeConverter.class);

		doAnswer(invocation -> {
			ExecutionResources result = new ExecutionResources();
			List<Execution> list = (List<Execution>) invocation.getArguments()[0];
			for (Execution jpaEx : list) {
				ExecutionResource res = new ExecutionResource();
				org.enquery.encryptedquery.xml.schema.Execution xmlEx = //
						new org.enquery.encryptedquery.xml.schema.Execution();
				xmlEx.setSchemaVersion(Versions.EXECUTION_BI);
				xmlEx.setUuid(jpaEx.getUuid());
				res.setExecution(xmlEx);
				result.getExecutionResource().add(res);
			}
			return result;
		}).when(converter).toXMLExecutions(any(List.class));


		doAnswer(invocation -> {
			ExecutionResources list = (ExecutionResources) invocation.getArguments()[0];
			OutputStream output = (OutputStream) invocation.getArguments()[1];
			int i = 0;
			for (ExecutionResource ex : list.getExecutionResource()) {
				if (i++ > 0) IOUtils.write("\n", output, "UTF-8");
				IOUtils.write(ex.getExecution().getUuid(), output, "UTF-8");
			}
			return null;
		}).when(converter).marshal(any(ExecutionResources.class), any(OutputStream.class));

		doAnswer(invocation -> {
			ExecutionResources result = new ExecutionResources();
			InputStream input = (InputStream) invocation.getArguments()[0];
			List<String> lines = IOUtils.readLines(input, "UTF-8");
			for (String s : lines) {
				ExecutionResource er = new ExecutionResource();
				org.enquery.encryptedquery.xml.schema.Execution e = new org.enquery.encryptedquery.xml.schema.Execution();
				e.setSchemaVersion(Versions.EXECUTION_BI);
				e.setUuid(s);
				er.setExecution(e);
				result.getExecutionResource().add(er);
			}
			return result;
		}).when(converter).unmarshalExecutionResources(any(InputStream.class));

	}

	int count = 0;

	private void mockRepository() {
		executionRepo = mock(ExecutionRepository.class);
		when(executionRepo.findByUUID(any(String.class))).then(invocation -> {
			Execution result = new Execution();
			result.setId(count++);
			result.setUuid((String) invocation.getArguments()[0]);
			return result;
		});
	}

}
