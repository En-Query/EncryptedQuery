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
package org.enquery.encryptedquery.standalone;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.utils.FileIOUtils;

public class Driver {

	private Path queryFileName;
	private Path outputFileName;
	private CommandLine commandLine;

	private static final Option queryFileNameOption = Option.builder("q")
			.desc("Query input file name (in XML format).")
			.required()
			.hasArg()
			.build();

	private static final Option outputFileNameOption = Option.builder("o")
			.desc("Output response file name (in XML format.)")
			.required()
			.hasArg()
			.build();

	private static final Option configPropertyFileOption = Option.builder("c")
			.desc("System configuration property file name.")
			.required()
			.hasArg()
			.build();

	private static final Option inputDataFileOption = Option.builder("i")
			.desc("Input Data File Name.")
			.required()
			.hasArg()
			.build();

	private Map<String, String> config;
	// private Query query;
	private Path inputDataFile;

	public static void main(String[] args) {
		try {
			Driver driver = new Driver();
			driver.configure(args);
			driver.run();
		} catch (ParseException e) {
			e.printStackTrace();
			System.out.println(MessageFormat.format("{0}\n", e.getMessage()));
			new HelpFormatter().printHelp("standalone query executer", getOptions());
			System.exit(1);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void configure(String[] args) throws ParseException, Exception {
		commandLine = new DefaultParser().parse(getOptions(), args);

		inputDataFile = Paths.get(getValue(inputDataFileOption));
		Validate.isTrue(Files.exists(inputDataFile), "File %s does not exist.", inputDataFile);

		queryFileName = Paths.get(getValue(queryFileNameOption));
		Validate.isTrue(Files.exists(queryFileName), "File %s does not exist.", queryFileName);

		outputFileName = Paths.get(getValue(outputFileNameOption));
		Validate.isTrue(!Files.exists(outputFileName), "Output file %s exists. Delete first.", outputFileName);

		config = FileIOUtils.loadPropertyFile(Paths.get(getValue(configPropertyFileOption)));
	}

	public static Options getOptions() {
		Options options = new Options();

		options.addOption(queryFileNameOption);
		options.addOption(outputFileNameOption);
		options.addOption(configPropertyFileOption);
		options.addOption(inputDataFileOption);

		return options;
	}

	public void run() throws Exception {
		String version = "v2";
		if (config.containsKey(StandaloneConfigurationProperties.ALG_VERSION)) {
			version = config.get(StandaloneConfigurationProperties.ALG_VERSION);
		}

		if ("v1".equals(version)) {
			try (Responder responder = new Responder();) {
				responder.setQueryFileName(queryFileName);
				responder.setOutputFileName(outputFileName);
				responder.setInputDataFile(inputDataFile);
				responder.run(config);
			}
		} else if ("v2".equals(version)) {
			try (ResponderV2 responder = new ResponderV2();) {
				responder.setQueryFileName(queryFileName);
				responder.setOutputFileName(outputFileName);
				responder.setInputDataFile(inputDataFile);
				responder.run(config);
			}
		} else {
			throw new RuntimeException("Invalid algorithn version " + version);
		}
	}

	private String getValue(Option opt) {
		return commandLine.getOptionValue(opt.getOpt());
	}

}
