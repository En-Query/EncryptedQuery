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
package org.enquery.encryptedquery.responder.it.util;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import org.enquery.encryptedquery.responder.flink.kafka.runner.FlinkKafkaQueryRunner;
import org.osgi.service.cm.ConfigurationAdmin;


public class FlinkKafkaRunnerConfigurator {

	private ConfigurationAdmin confAdmin;
	private org.osgi.service.cm.Configuration conf;
	private File tempDir = new File("data/tmp");

	public FlinkKafkaRunnerConfigurator(ConfigurationAdmin confAdmin) throws IOException {
		this.confAdmin = confAdmin;
	}


	public void create(String name, String dataSchemaNema) throws IOException {
		conf = confAdmin.createFactoryConfiguration(FlinkKafkaQueryRunner.class.getName(), "?");

		Hashtable<String, String> properties = new Hashtable<>();
		properties.put("name", name);
		properties.put("description", name);
		properties.put("data.schema.name", dataSchemaNema);
		properties.put(".kafka.brokers", "localhost:9092");
		properties.put(".kafka.topic", "test");
		// properties.put(".kafka.groupId", "integration-test");
		// properties.put(".stream.window.length.seconds", "10");
		// properties.put(".stream.runtime.seconds", "60");
		properties.put(".flink.install.dir", System.getProperty("flink.install.dir"));
		properties.put(".jar.file.path", System.getProperty("flink.kafka.app"));
		properties.put(".run.directory", tempDir.getAbsolutePath());
		properties.put(".kafka.force.from.start", "true");

		conf.update(properties);
	}

	public void delete() throws IOException {
		conf.delete();
	}
}
