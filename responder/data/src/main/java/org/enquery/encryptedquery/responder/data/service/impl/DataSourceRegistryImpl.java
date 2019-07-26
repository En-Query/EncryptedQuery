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
package org.enquery.encryptedquery.responder.data.service.impl;


import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.DataSourceId;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.QueryRunner;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class DataSourceRegistryImpl implements DataSourceRegistry {

	private static final Logger log = LoggerFactory.getLogger(DataSourceRegistryImpl.class);

	private Collection<DataSource> dataSources = new CopyOnWriteArrayList<>();

	@Reference
	private DataSchemaService dataSchemaRepo;

	@Reference(target = "(osgi.unit.name=responderPersistenUnit)")
	private JPAEntityManagerProvider provider;
	@Reference
	private TransactionControl txControl;
	private EntityManager em;

	@Activate
	void init() {
		log.info("Activating Data Source Registry");
		em = provider.getResource(txControl);
		resolveReferences();
	}

	private void resolveDataSourceIds() {
		dataSources
				.stream()
				.filter(ds -> ds.getId() == null)
				.forEach(ds -> ds.setId(generateId(ds.getName())));
	}

	private void resolveDataSchemaReferences() {
		// always re-bind the data source to the data schema, since
		// data schemas may be updated, their Id may change
		dataSources
				.stream()
				.forEach(ds -> ds.setDataSchema(findDataSchema(ds.getDataSchemaName())));
	}

	private void resolveReferences() {
		if (txControl == null) return;
		txControl
				.build()
				.required(() -> {
					resolveDataSourceIds();
					resolveDataSchemaReferences();
					return 0;
				});
	}

	@Deactivate
	void deactivate() {
		log.info("Deactivating Data Source Registry");
	}

	@Reference(cardinality = ReferenceCardinality.MULTIPLE,
			policy = ReferencePolicy.DYNAMIC,
			policyOption = ReferencePolicyOption.GREEDY)
	void addRunner(QueryRunner runner) {
		log.info("Runner activated: " + runner);
		DataSource ds = new DataSource();
		ds.setId(generateId(runner.name()));
		ds.setName(runner.name());
		ds.setDescription(runner.description());
		ds.setDataSchemaName(runner.dataSchemaName());
		ds.setRunner(runner);
		ds.setType(runner.getType());
		ds.setDataSchema(findDataSchema(runner.dataSchemaName()));
		log.info("Adding datasource: " + ds);
		dataSources.add(ds);
	}

	private DataSchema findDataSchema(String dataSchemaName) {
		if (dataSchemaRepo == null) return null;

		DataSchema dataSchema = dataSchemaRepo.findByName(dataSchemaName);
		if (dataSchema == null) {
			log.warn("No data schema found with name '{}'.", dataSchemaName);
		}
		return dataSchema;
	}

	private Integer generateId(String name) {
		if (txControl == null) return null;

		return txControl
				.build()
				.required(() -> {
					DataSourceId dataSourceId = em
							.createQuery(
									"Select ds From DataSourceId ds Where ds.name = :name",
									DataSourceId.class)
							.setParameter("name", name)
							.getResultList()
							.stream()
							.findFirst()
							.orElse(null);

					if (dataSourceId != null) {
						return dataSourceId.getId();
					}

					dataSourceId = new DataSourceId();
					dataSourceId.setName(name);
					em.persist(dataSourceId);
					em.flush();
					return dataSourceId.getId();
				});
	}

	void removeRunner(QueryRunner runner) {
		Validate.notNull(runner);

		DataSource dataSource = dataSources
				.stream()
				.filter(ds -> runner.name().equals(ds.getName()))
				.findFirst()
				.orElse(null);

		if (dataSource == null) return;

		dataSources.remove(dataSource);
		log.info("Removed datasource: " + dataSource);
	}

	@Override
	public DataSource find(String name) {
		Validate.notNull(name);
		resolveReferences();
		return dataSources
				.stream()
				.filter(ds -> ds.getId() != null)
				.filter(ds -> ds.getDataSchema() != null)
				.filter(ds -> name.equals(ds.getName()))
				.findFirst()
				.orElse(null);
	}

	@Override
	public DataSource findForDataSchema(DataSchema dataSchema, String dataSourceName) {
		Validate.notNull(dataSchema);
		Validate.notNull(dataSourceName);

		resolveReferences();
		return dataSources
				.stream()
				.filter(ds -> ds.getId() != null)
				.filter(ds -> dataSchema.equals(ds.getDataSchema()))
				.filter(ds -> dataSourceName.equals(ds.getName()))
				.findFirst()
				.orElse(null);
	}

	@Override
	public Collection<DataSource> list() {
		resolveReferences();
		return dataSources
				.stream()
				.filter(ds -> ds.getId() != null)
				.filter(ds -> ds.getDataSchema() != null)
				.collect(Collectors.toList());
	}

	@Override
	public DataSource find(DataSchema dataSchema, int id) {
		Validate.notNull(dataSchema);
		resolveReferences();
		return dataSources
				.stream()
				.filter(ds -> ds.getId() != null)
				.filter(ds -> ds.getDataSchema() != null)
				.filter(ds -> id == ds.getId() && dataSchema.equals(ds.getDataSchema()))
				.findFirst()
				.orElse(null);
	}

	@Override
	public Collection<DataSource> listForDataSchema(DataSchema dataSchema) {
		Validate.notNull(dataSchema);
		resolveReferences();
		return dataSources
				.stream()
				.filter(ds -> ds.getId() != null)
				.filter(ds -> ds.getDataSchema() != null)
				.filter(ds -> dataSchema.equals(ds.getDataSchema()))
				.collect(Collectors.toList());
	}
}
