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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.karaf.shell.api.console.Session;
import org.enquery.encryptedquery.querier.admin.BaseCommandTest;
import org.enquery.encryptedquery.querier.data.transformation.ExecutionExporter;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class ExportCommandTest extends BaseCommandTest {

	private static final Path OUTPUT_FILE_NAME = Paths.get("target/schedule.export.xml");
	private ExecutionExporter executionExporter;

	@Before
	public void prepare() throws IOException, JAXBException, XMLStreamException, FactoryConfigurationError {
		FileUtils.deleteQuietly(OUTPUT_FILE_NAME.toFile());
		mockExporter();
	}

	@Test
	public void testSingle() throws Exception {
		final Session mockSession = mock(Session.class);

		runCommand(mockSession, 1);

		assertTrue(getConsoleOutput()
				.contains("1 schedules exported to"));

		try (InputStream is = Files.newInputStream(OUTPUT_FILE_NAME)) {
			List<String> outputFileLines = IOUtils.readLines(is, "UTF-8");
			assertEquals(1, outputFileLines.size());
			assertEquals("1", outputFileLines.get(0));
		}
	}

	@Test
	public void testMultiple() throws Exception {
		final Session mockSession = mock(Session.class);

		runCommand(mockSession, 1, 2);

		assertTrue(getConsoleOutput().contains("2 schedules exported to"));

		try (InputStream is = Files.newInputStream(OUTPUT_FILE_NAME)) {
			List<String> outputFileLines = IOUtils.readLines(is, "UTF-8");
			assertEquals(1, outputFileLines.size());
			assertEquals("1, 2", outputFileLines.get(0));
		}
	}

	private void runCommand(final Session mockSession, Integer... ids) throws Exception {
		ExportCommand command = new ExportCommand();
		command.setSession(mockSession);
		command.setScheduleIds(Arrays.asList(ids));
		command.setExecutionExporter(executionExporter);
		command.setOutputFileName(OUTPUT_FILE_NAME.toString());
		command.execute();
	}

	@SuppressWarnings("unchecked")
	private void mockExporter() throws JAXBException, UnsupportedEncodingException, IOException, XMLStreamException, FactoryConfigurationError {
		executionExporter = mock(ExecutionExporter.class);

		doAnswer(invocation -> {
			StringBuilder sb = new StringBuilder();
			List<Integer> ids = (List<Integer>) invocation.getArguments()[0];
			for (Integer id : ids) {
				if (sb.length() > 0) sb.append(", ");
				sb.append(id);
			}
			return new ByteArrayInputStream(sb.toString().getBytes());
		}).when(executionExporter).streamByScheduleIds(any(List.class));

	}
}
