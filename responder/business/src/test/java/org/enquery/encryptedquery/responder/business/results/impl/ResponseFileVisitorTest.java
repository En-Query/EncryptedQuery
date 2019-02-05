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
package org.enquery.encryptedquery.responder.business.results.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
@Ignore("not deleting files for now for testing")
public class ResponseFileVisitorTest {

	private static final Path RESPONSE_FILE_NAME = Paths.get("target/response.xml");

	@Before
	public void init() throws IOException {
		FileUtils.deleteDirectory(RESPONSE_FILE_NAME.toFile());
	}

	@Test
	public void test() throws IOException {
		Path day1 = RESPONSE_FILE_NAME.resolve("2019").resolve("11").resolve("1");
		Files.createDirectories(day1);

		Path response11 = day1.resolve("073512-074513.xml");
		createSampleFile(response11);

		Path response12 = day1.resolve("163005-163506.xml");
		createSampleFile(response12);

		Path day2 = RESPONSE_FILE_NAME.resolve("2018").resolve("12").resolve("2");
		Files.createDirectories(day2);

		Path tooDeep = day2.resolve("tooDeep");
		Files.createDirectories(tooDeep);
		Path ignoredFile = tooDeep.resolve("ignored");
		createSampleFile(ignoredFile);

		Path response21 = day2.resolve("073512-074513.xml");
		createSampleFile(response21);
		Path response22 = day2.resolve("163005-163506.xml");
		createSampleFile(response22);

		Path day3 = RESPONSE_FILE_NAME.resolve("2017").resolve("3").resolve("15");
		Files.createDirectories(day3);
		Path response31 = day3.resolve("233005-233506.xml");
		createSampleFile(response31);

		Path badDay = RESPONSE_FILE_NAME.resolve("2017").resolve("3").resolve("bad");
		Files.createDirectories(badDay);
		Path responseBadDay = badDay.resolve("233005-233506.xml");
		createSampleFile(responseBadDay);

		Files.walkFileTree(RESPONSE_FILE_NAME,
				new ResponseFileVisitor(info -> {

					if (info.path.equals(response11) || info.path.equals(response12)) {
						assertEquals(2019, info.year);
						assertEquals(11, info.month);
						assertEquals(1, info.day);
					} else if (info.path.equals(response21) || info.path.equals(response22)) {
						assertEquals(2018, info.year);
						assertEquals(12, info.month);
						assertEquals(2, info.day);
					} else if (info.path.equals(response31)) {
						assertEquals(2017, info.year);
						assertEquals(3, info.month);
						assertEquals(15, info.day);
					}

					assertFalse(responseBadDay.equals(info.path));

					// emulate failure processing file response31
					return !response31.equals(info.path);
				}

				));

		assertTrue(!Files.exists(day1));

		assertTrue(Files.exists(day2));
		assertTrue(Files.exists(ignoredFile));
		assertTrue(!Files.exists(response21));
		assertTrue(!Files.exists(response22));
		assertTrue(Files.exists(day3));
		assertTrue(Files.exists(response31));
		assertTrue(Files.exists(responseBadDay));
		assertTrue(Files.exists(badDay));
	}


	/**
	 * @param response11
	 * @throws IOException
	 */
	private void createSampleFile(Path path) throws IOException {
		Files.write(path, "test".getBytes());
	}

}
