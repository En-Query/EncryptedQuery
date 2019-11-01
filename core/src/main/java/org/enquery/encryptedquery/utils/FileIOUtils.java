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
package org.enquery.encryptedquery.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Class holding basic fileIO utils
 */
public class FileIOUtils {

	public static Map<String, String> loadPropertyFile(Path file) throws FileNotFoundException, IOException {
		Properties properties = new Properties();

		try (FileInputStream fis = new FileInputStream(file.toFile());) {
			properties.load(fis);
		}
		HashMap<String, String> result = new HashMap<>();
		properties
				.entrySet()
				.stream()
				.forEach(
						entry -> result.put((String) entry.getKey(), (String) entry.getValue()));
		return result;
	}

	public static void savePropertyFile(Path file, Map<String, String> properties) throws FileNotFoundException, IOException {
		Properties prop = new Properties();
		properties.forEach((key, value) -> {
			if (key != null && value != null) prop.put(key, value);
		});

		try (FileOutputStream fos = new FileOutputStream(file.toFile());) {
			prop.store(fos, "");
		}
	}
}
