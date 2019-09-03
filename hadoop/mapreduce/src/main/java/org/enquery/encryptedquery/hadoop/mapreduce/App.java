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

import java.security.PrivilegedExceptionAction;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

	private static final Logger log = LoggerFactory.getLogger(App.class);
	private static final Option runAsOption = Option.builder("runas")
			.desc("Run job as user.")
			.hasArg()
			.build();

	public static void main(String[] args) {
		try {
			log.info("Running Hadoop Map Reduce with args: {}", Arrays.toString(args));

			DefaultParser parser = new DefaultParser();
			Options options = new Options();
			options.addOption(runAsOption);
			CommandLine commandLine = parser.parse(options, args, true);

			int status = 0;
			String user = commandLine.getOptionValue(runAsOption.getOpt());
			if (user != null) {
				log.info("Running as '{}'", user);
				UserGroupInformation ugi = UserGroupInformation.createRemoteUser(user);
				Responder r = ugi.doAs(new PrivilegedExceptionAction<Responder>() {
					@Override
					public Responder run() throws Exception {
						Responder responder = new Responder();
						ToolRunner.run(responder, commandLine.getArgs());
						return responder;
					}
				});
				if (!r.isSuccess()) {
					status = -1;
				}
			} else {
				status = ToolRunner.run(new Responder(), commandLine.getArgs());
			}

			if (status != 0) {
				log.info("Hadoop Map Reduce Job Failed.");
				System.exit(status);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
