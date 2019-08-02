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
import org.enquery.encryptedquery.responder.flink.jdbc.runner.FlinkJdbcQueryRunner;
import org.osgi.service.cm.ConfigurationAdmin;


public class FlinkJdbcRunnerConfigurator {

	private ConfigurationAdmin confAdmin;
	private org.osgi.service.cm.Configuration conf;
	private File tempDir = new File("data/tmp");

	public FlinkJdbcRunnerConfigurator(ConfigurationAdmin confAdmin) throws IOException {
		this.confAdmin = confAdmin;
	}


	public void create(String name, String dataSchemaNema, String description) throws IOException {
		conf = confAdmin.createFactoryConfiguration(FlinkJdbcQueryRunner.class.getName(), "?");

		Hashtable<String, String> properties = new Hashtable<>();
		properties.put("name", name);
		properties.put("description", description);
		properties.put("type", DataSourceType.Batch.toString());
		properties.put("data.schema.name", dataSchemaNema);
		properties.put(".column.encryption.class.name", "org.enquery.encryptedquery.responder.wideskies.common.ComputeEncryptedColumnBasic");
		properties.put(".mod.pow.class.name", "org.enquery.encryptedquery.encryption.impl.ModPowAbstractionJavaImpl");


		properties.put(".jdbc.driver", "org.apache.derby.jdbc.ClientDriver");
		properties.put(".jdbc.url", "jdbc:derby://localhost/data/derby-data/books");
		properties.put(".jdbc.query", "Select id, title, author, price, qty, isNew From books");

		// paths just needs to exists, no other check is done during initialization
		properties.put(".application.jar.path", System.getProperty("flink.jdbc.app"));
		properties.put(".flink.install.dir", System.getProperty("flink.install.dir"));
		properties.put(".run.directory", tempDir.getAbsolutePath());

		conf.update(properties);
	}

	public void delete() throws IOException {
		conf.delete();
	}
}
