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
package org.enquery.encryptedquery.flink.jdbc;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.utils.FileIOUtils;

public class App {

	private String driverClassName;
	private String connectionUrl;
	private Path queryFileName;
	private Path outputFileName;
	private CommandLine commandLine;
	private String sqlQuery;

	private static final Option jdbcConnectionPropertyFileOption = Option.builder("c")
			.desc("JDBC connection property file name.")
			.required()
			.hasArg()
			.build();


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
	private Map<String, String> config;

	public static void main(String[] args) {
		try {
			App app = new App();
			app.configure(args);
			app.run();
		} catch (ParseException e) {
			System.out.println(MessageFormat.format("{0}\n", e.getMessage()));
			new HelpFormatter().printHelp("flink-jdbc-enquery", getOptions());
			System.exit(1);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void configure(String[] args) throws ParseException, Exception {
		commandLine = new DefaultParser().parse(getOptions(), args);

		queryFileName = Paths.get(getValue(queryFileNameOption));
		Validate.isTrue(Files.exists(queryFileName), "File %s does not exist.", queryFileName);

		outputFileName = Paths.get(getValue(outputFileNameOption));
		Validate.isTrue(!Files.exists(outputFileName), "Output file %s exists. Delete first.", outputFileName);

		loadJDBCProperties();

		config = FileIOUtils.loadPropertyFile(Paths.get(getValue(configPropertyFileOption)));
	}

	public static Options getOptions() {
		Options options = new Options();

		options.addOption(jdbcConnectionPropertyFileOption);
		options.addOption(queryFileNameOption);
		options.addOption(outputFileNameOption);
		options.addOption(configPropertyFileOption);

		return options;
	}

	public void run() throws Exception {
		try (Responder q = new Responder()) {
			q.setConnectionUrl(connectionUrl);
			q.setDriverClassName(driverClassName);
			q.setSqlQuery(sqlQuery);
			q.setInputFileName(queryFileName);
			q.setOutputFileName(outputFileName);
			q.setConfig(config);
			q.run();
		}
	}

	private String getValue(Option opt) {
		return commandLine.getOptionValue(opt.getOpt());
	}


	private void loadJDBCProperties() throws FileNotFoundException, IOException {
		Properties p = new Properties();
		try (InputStream is = new FileInputStream(getValue(jdbcConnectionPropertyFileOption))) {
			p.load(is);
		}
		driverClassName = p.getProperty("jdbc.driver");
		Validate.notBlank(driverClassName);

		connectionUrl = p.getProperty("jdbc.url");
		Validate.notBlank(connectionUrl);

		sqlQuery = p.getProperty("jdbc.query");
		Validate.notBlank(sqlQuery);
	}

}
