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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

/**
 * Class holding basic fileIO utils
 */
public class FileIOUtils {

	public interface Callable<V> {
		V call(String line) throws Exception;
	}

	public static ArrayList<String> readToArrayList(String filepath) {
		return (ArrayList<String>) read(filepath, new ArrayList<>(), new Callable<String>() {
			@Override
			public String call(String line) {
				return line;
			}
		});
	}

	public static ArrayList<String> readToArrayList(String filepath, Callable<String> function) {
		return (ArrayList<String>) read(filepath, new ArrayList<>(), function);
	}

	public static HashSet<String> readToHashSet(String filepath) {
		return (HashSet<String>) read(filepath, new HashSet<>(), new Callable<String>() {
			@Override
			public String call(String line) {
				return line;
			}
		});
	}

	public static HashSet<String> readToHashSet(String filepath, Callable<String> function) {
		return (HashSet<String>) read(filepath, new HashSet<>(), function);
	}

	public static AbstractCollection<String> read(String filepath, AbstractCollection<String> collection, Callable<String> function) {
		File file = new File(filepath);

		// if file does not exist, output error and return null
		if (!file.exists()) {
			throw new RuntimeException("file at " + filepath + " does not exist");
		}

		// if file cannot be read, output error and return null
		if (!file.canRead()) {
			throw new RuntimeException("cannot read file at " + filepath);
		}

		// create buffered reader
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			// read through the file, line by line
			String line;
			while ((line = br.readLine()) != null) {
				String item = function.call(line);
				if (item != null) {
					collection.add(item);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("unable to read file");
		}

		return collection;
	}

	public static void writeArrayList(ArrayList<String> aList, File file) throws IOException {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))) {
			for (String s : aList) {
				bw.write(s);
				bw.newLine();
			}
		}
	}

	public static void writeArrayList(ArrayList<String> aList, String filename) throws IOException {
		writeArrayList(aList, new File(filename));
	}

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
