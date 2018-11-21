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
package org.enquery.encryptedquery.jpa.config;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jpa.EntityManagerFactoryBuilder;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
public class EMConfigurator {

	@Reference
	private EntityManagerFactoryBuilder emfb;

	@Reference
	private DataSource ds;

	private EntityManagerFactory emf;

	@Activate
	void start(Map<String, Object> props) {
		Map<String, Object> jpaProps = new HashMap<>(props);

		jpaProps.put("javax.persistence.jtaDataSource", ds);
		// jpaProps.put("javax.persistence.nonJtaDataSource", ds2);
		// Or for resource local just use one non-jta datasource
		// jpaProps.put(“javax.persistence.dataSource”, ds2);
		// This line also causes the emf to be registered as a service
		emf = emfb.createEntityManagerFactory(jpaProps);
	}

	@Deactivate
	void stop() {
		// This unregisters the emf service
		emf.close();
	}
}
