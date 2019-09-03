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
package org.enquery.encryptedquery.hadoop.mapreduce;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 *
 */
public class CommandLineOptions {
	private static final Option queryFileNameOption = Option.builder("q")
			.desc("Encypted query file name (in XML format.)")
			.required()
			.hasArg()
			.build();

	private static final Option outputFileNameOption = Option.builder("o")
			.desc("Output response file name (in XML format.)")
			.required()
			.hasArg()
			.build();

	private static final Option configPropertyFileOption = Option.builder("sp")
			.desc("System configuration property file name.")
			.required()
			.hasArg()
			.build();

	private static final Option inputDataFileOption = Option.builder("i")
			.desc("Input Data File Name.")
			.required()
			.hasArg()
			.build();

	private CommandLine commandLine;

	/**
	 * @throws ParseException
	 * 
	 */
	public CommandLineOptions(String[] args) throws ParseException {
		DefaultParser parser = new DefaultParser();
		commandLine = parser.parse(getOptions(), args);
	}


	public static Options getOptions() {
		Options options = new Options();

		options.addOption(queryFileNameOption);
		options.addOption(outputFileNameOption);
		options.addOption(configPropertyFileOption);
		options.addOption(inputDataFileOption);

		return options;
	}

	private String getValue(Option opt) {
		return commandLine.getOptionValue(opt.getOpt());
	}

	public Path queryFile() {
		return Paths.get(getValue(queryFileNameOption));
	}

	public String inputFile() {
		return getValue(inputDataFileOption);
	}

	public Path outputFile() {
		return Paths.get(getValue(outputFileNameOption));
	}

	public Path configFile() {
		return Paths.get(getValue(configPropertyFileOption));
	}
}
