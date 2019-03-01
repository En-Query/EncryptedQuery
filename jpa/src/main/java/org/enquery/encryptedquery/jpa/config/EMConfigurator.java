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

import static javax.persistence.spi.PersistenceUnitTransactionType.RESOURCE_LOCAL;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jpa.EntityManagerFactoryBuilder;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProvider;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures: EntityManagerFactory, and JPAEntityManagerProvider
 */
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
public class EMConfigurator {

	private static final Logger log = LoggerFactory.getLogger(EMConfigurator.class);

	private EntityManagerFactoryBuilder emfb;
	@Reference
	private JPAEntityManagerProviderFactory factory;
	@Reference
	private DataSource ds;

	private EntityManagerFactory emf;
	private JPAEntityManagerProvider provider;
	private BundleContext bundleCtx;
	private String unitName;
	private String unitVersion;
	private String unitProvider;

	private ServiceRegistration<JPAEntityManagerProvider> providerSvcRegistration;

	@Activate
	void start(Map<String, Object> props, ComponentContext cc) {
		log.info("Initializing JPA with unit name: '{}', unitVersion: '{}', and unitProvider: '{}'.",
				unitName, unitVersion, unitProvider);

		bundleCtx = cc.getBundleContext();
		registerEntityManagerFactory(props);
		registerProvider();
	}

	@Deactivate
	void stop() {
		if (providerSvcRegistration != null) providerSvcRegistration.unregister();

		if (factory != null && provider != null) {
			factory.releaseProvider(provider);
		}

		// This unregisters the emf service
		if (emf != null) emf.close();
	}

	/**
	 * Gets injected the EntityManagerFactoryBuilder with its properties. We use the standard OSGi
	 * properties that identify the JPA persistent unit as properties of published services to allow
	 * multiple peristent units in the same container.
	 * 
	 * @param b
	 * @param properties
	 */
	@Reference
	void setEntityManagerFactoryBuilder(EntityManagerFactoryBuilder b, Map<String, Object> properties) {
		emfb = b;
		unitName = (String) properties.get(EntityManagerFactoryBuilder.JPA_UNIT_NAME);
		unitVersion = (String) properties.get(EntityManagerFactoryBuilder.JPA_UNIT_VERSION);
		unitProvider = (String) properties.get(EntityManagerFactoryBuilder.JPA_UNIT_PROVIDER);
	}

	private void registerEntityManagerFactory(Map<String, Object> props) {
		Map<String, Object> jpaProps = new HashMap<>(props);
		jpaProps.put("javax.persistence.nonJtaDataSource", ds);
		jpaProps.put("javax.persistence.transactionType", RESOURCE_LOCAL.name());

		// cleanup undesired properties
		Set<String> tobeRemoved = jpaProps.keySet()
				.stream()
				.filter(k -> k.startsWith("component.") ||
						k.equals("ds.target") ||
						k.equals("emfb.target") ||
						k.startsWith("felix."))
				.collect(Collectors.toSet());

		tobeRemoved.forEach(k -> jpaProps.remove(k));

		// This line also causes the emf to be registered as a service
		emf = emfb.createEntityManagerFactory(jpaProps);
	}

	/**
	 * Registers a JPAEntityManagerProvider service
	 */
	private void registerProvider() {
		// TODO: allow configuration of pooling
		Map<String, Object> pooling = new HashMap<>();
		pooling.put(JPAEntityManagerProviderFactory.CONNECTION_POOLING_ENABLED, "true");

		// creates a provider and shares it as a service
		provider = factory.getProviderFor(emf, pooling);
		providerSvcRegistration = bundleCtx.registerService(JPAEntityManagerProvider.class, provider, standardProperties());
	}

	private Dictionary<String, Object> standardProperties() {
		Dictionary<String, Object> properties = new Hashtable<>();
		properties.put(EntityManagerFactoryBuilder.JPA_UNIT_NAME, unitName);
		properties.put(EntityManagerFactoryBuilder.JPA_UNIT_VERSION, unitVersion);
		properties.put(EntityManagerFactoryBuilder.JPA_UNIT_PROVIDER, unitProvider);
		return properties;
	}
}
