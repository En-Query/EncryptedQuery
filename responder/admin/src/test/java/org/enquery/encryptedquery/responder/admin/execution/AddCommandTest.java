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
import static org.mockito.Matchers.anyCollectionOf;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.karaf.shell.api.console.Session;
import org.enquery.encryptedquery.responder.admin.BaseCommandTest;
import org.enquery.encryptedquery.responder.business.execution.ExecutionUpdater;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.transformation.ExecutionTypeConverter;
import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.enquery.encryptedquery.xml.schema.ExecutionResources;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 *
 */
public class AddCommandTest extends BaseCommandTest {

	private static final String SINGLE_EXECUTION_SRC = "target/test-classes/execution-import-single.xml";
	private static final String SINGLE_EXECUTION = "target/execution-import-single.xml";

	private static final String MULT_EXECUTION_SRC = "target/test-classes/execution-import-mult.xml";
	private static final String MULT_EXECUTION = "target/execution-import-mult.xml";


	private ExecutionTypeConverter converter;

	@Before
	public void prepare() throws IOException, JAXBException, XMLStreamException, FactoryConfigurationError {

		// delete any result file
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(
				Paths.get("target"), "add-executions*.xml")) {
			dirStream.forEach(path -> FileUtils.deleteQuietly(path.toFile()));
		}

		FileUtils.copyFileToDirectory(new File(SINGLE_EXECUTION_SRC), new File("target"));
		FileUtils.copyFileToDirectory(new File(MULT_EXECUTION_SRC), new File("target"));

		mockConverter();
	}

	@Test
	public void testSingleNoOutputFileGiven() throws Exception {
		final Session mockSession = mock(Session.class);

		// mock the update
		Execution execution = new Execution();
		execution.setId(301);
		execution.setReceivedTime(new Date());
		execution.setScheduleTime(new Date());
		execution.setUuid("3e7c5e594e424cde88c9e531a5dccb06");

		List<Execution> executions = new ArrayList<>();
		executions.add(execution);

		ExecutionUpdater updater = mock(ExecutionUpdater.class);
		when(updater.createFromImportXML(any(InputStream.class)))
				.thenReturn(executions);


		runCommand(mockSession, updater, SINGLE_EXECUTION, null);

		assertTrue(getConsoleOutput().contains("1 execution added"));

		Path outFile = null;
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("target"), "add-executions*.xml")) {
			outFile = dirStream.iterator().next();
		}
		try (InputStream is = Files.newInputStream(outFile)) {
			List<String> outputFileLines = IOUtils.readLines(is, "UTF-8");
			assertEquals(1, outputFileLines.size());
			assertEquals("3e7c5e594e424cde88c9e531a5dccb06", outputFileLines.get(0));
		}
	}


	@Test
	public void testSingleOutputFileGiven() throws Exception {
		final Session mockSession = mock(Session.class);

		Path outputFile = Paths.get("target/add-execution-result.xml");
		FileUtils.deleteQuietly(outputFile.toFile());

		Execution execution = new Execution();
		execution.setId(301);
		execution.setReceivedTime(new Date());
		execution.setScheduleTime(new Date());
		execution.setUuid("3e7c5e594e424cde88c9e531a5dccb06");

		List<Execution> executions = new ArrayList<>();
		executions.add(execution);

		ExecutionUpdater updater = mock(ExecutionUpdater.class);
		when(updater.createFromImportXML(any(InputStream.class)))
				.thenReturn(executions);


		runCommand(mockSession, updater, SINGLE_EXECUTION, outputFile.toString());

		assertTrue(getConsoleOutput().contains("1 execution added"));


		try (InputStream is = Files.newInputStream(outputFile)) {
			List<String> outputFileLines = IOUtils.readLines(is, "UTF-8");
			assertEquals(1, outputFileLines.size());
			assertEquals("3e7c5e594e424cde88c9e531a5dccb06", outputFileLines.get(0));
		}
	}


	@Test
	public void testMultiple() throws Exception {
		final Session mockSession = mock(Session.class);

		// mock the update
		Execution execution1 = new Execution();
		execution1.setId(301);
		execution1.setReceivedTime(new Date());
		execution1.setScheduleTime(new Date());
		execution1.setUuid("3e7c5e59-4e42-4cde-88c9-e531a5dccb06");

		Execution execution2 = new Execution();
		execution2.setId(302);
		execution2.setReceivedTime(new Date());
		execution2.setScheduleTime(new Date());
		execution2.setUuid("3e7c5e59-4e42-4cde-88c9-e531a5dccb07");

		List<Execution> executions = new ArrayList<>();
		executions.add(execution1);
		executions.add(execution2);

		ExecutionUpdater updater = mock(ExecutionUpdater.class);
		when(updater.createFromImportXML(any(InputStream.class)))
				.thenReturn(executions);


		runCommand(mockSession, updater, MULT_EXECUTION, null);

		assertTrue(getConsoleOutput().contains("2 execution added"));

		Path outFile = null;
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("target"), "add-executions*.xml")) {
			outFile = dirStream.iterator().next();
		}
		try (InputStream is = Files.newInputStream(outFile)) {
			List<String> outputFileLines = IOUtils.readLines(is, "UTF-8");
			assertEquals(2, outputFileLines.size());
			assertEquals("3e7c5e59-4e42-4cde-88c9-e531a5dccb06", outputFileLines.get(0));
			assertEquals("3e7c5e59-4e42-4cde-88c9-e531a5dccb07", outputFileLines.get(1));
		}
	}

	private void runCommand(final Session mockSession, ExecutionUpdater updater, String inputFile, String outputFile) throws Exception {
		ImportCommand command = new ImportCommand();
		command.setSession(mockSession);
		command.setInputFile(inputFile);
		command.setUpdater(updater);
		command.setConverter(converter);
		command.setOutputFile(outputFile);
		command.execute();
	}

	@SuppressWarnings("unchecked")
	private void mockConverter() throws JAXBException, UnsupportedEncodingException, IOException, XMLStreamException, FactoryConfigurationError {
		converter = mock(ExecutionTypeConverter.class);
		Mockito.doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws IOException {
				List<ExecutionResource> list = ((ExecutionResources) invocation.getArguments()[0]).getExecutionResource();
				OutputStream output = (OutputStream) invocation.getArguments()[1];
				int i = 0;
				for (ExecutionResource er : list) {
					if (i++ > 0) IOUtils.write("\n", output, "UTF-8");
					IOUtils.write(er.getExecution().getUuid(), output, "UTF-8");
				}
				return null;
			}
		}).when(converter).marshal(any(ExecutionResources.class), any(OutputStream.class));


		when(converter.toXMLExecutions(anyCollectionOf(Execution.class))).thenAnswer(
				invocation -> {
					ExecutionResources result = new ExecutionResources();
					List<Execution> list = (List<Execution>) invocation.getArguments()[0];
					for (Execution ex : list) {
						ExecutionResource er = new ExecutionResource();
						org.enquery.encryptedquery.xml.schema.Execution execution = //
								new org.enquery.encryptedquery.xml.schema.Execution();
						execution.setSchemaVersion(Versions.EXECUTION_BI);
						execution.setUuid(ex.getUuid());
						er.setExecution(execution);
						result.getExecutionResource().add(er);
					}
					return result;
				});
	}
}
