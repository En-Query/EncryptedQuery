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

import org.enquery.encryptedquery.responder.data.entity.DataSourceType;
import org.osgi.service.cm.ConfigurationAdmin;


public class HadoopJsonRunnerConfigurator {

	private ConfigurationAdmin confAdmin;
	private org.osgi.service.cm.Configuration conf;
	private File tempDir = new File("data/tmp");

	public HadoopJsonRunnerConfigurator(ConfigurationAdmin confAdmin) throws IOException {
		this.confAdmin = confAdmin;
	}


	public void create(String name, String dataSchemaNema, String description, boolean useVersion1) throws IOException {
		conf = confAdmin.createFactoryConfiguration(org.enquery.encryptedquery.responder.hadoop.mapreduce.runner.HadoopMapReduceRunner.class.getName(), "?");

		Hashtable<String, String> properties = new Hashtable<>();
		properties.put("name", name);
		properties.put("description", description);
		properties.put("type", DataSourceType.Batch.toString());
		properties.put("data.schema.name", dataSchemaNema);
		properties.put("data.source.file", "/user/enquery/data/books.json");
		properties.put("data.source.record.type", "json");

		properties.put(".hadoop.server.uri", "hdfs://localhost:9000");
		properties.put(".hdfs.run.directory", "/user/enquery/data");
		properties.put(".application.jar.path", System.getProperty("hadoop.mr.app"));
		properties.put(".hadoop.install.dir", System.getProperty("hadoop.install.dir"));
		properties.put(".run.directory", tempDir.getAbsolutePath());
		if (useVersion1) {
			properties.put(".hadoop.processing.method", "v1");
		}

		conf.update(properties);
	}

	public void delete() throws IOException {
		conf.delete();
	}
}
