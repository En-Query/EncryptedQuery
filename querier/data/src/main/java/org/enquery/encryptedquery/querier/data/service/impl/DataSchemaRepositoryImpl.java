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
package org.enquery.encryptedquery.querier.data.service.impl;


import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchemaField;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProvider;

/**
 * A {@link org.enquery.encryptedquery.responder.data.entity.DataSource} service which we rest
 * enable from the {@link org.apache.camel.example.rest.UserRouteBuilder}.
 */
@Component
public class DataSchemaRepositoryImpl implements DataSchemaRepository {

	@Reference(target = "(osgi.unit.name=querierPersistenUnit)")
	private JPAEntityManagerProvider provider;
	@Reference
	private TransactionControl txControl;
	private EntityManager em;

	@Activate
	void init() {
		em = provider.getResource(txControl);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.example.rest.UserService#getUser(java.lang.String)
	 */
	@Override
	public DataSchema find(int id) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.find(DataSchema.class, id));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.example.rest.UserService#listUsers()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Collection<DataSchema> list() {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select ds From DataSchema ds").getResultList());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.example.rest.UserService#updateUser(org.apache.camel.example.rest.User)
	 */
	@Override
	public DataSchema update(DataSchema ds) {
		return txControl
				.build()
				.required(() -> em.merge(ds));
	}

	@Override
	public DataSchema add(DataSchema qs) {
		Validate.notNull(qs);
		Validate.notNull(qs.getName());
		Validate.notNull(qs.getFields());
		Validate.isTrue(qs.getFields().size() > 0, "Need at least one field.");
		Validate.isTrue(
				qs.getFields().stream().map(e -> e.getFieldName()).distinct().collect(Collectors.toList()).size() == qs.getFields().size(),
				"No unique field names found.");

		Validate.isTrue(
				qs.getFields().stream().filter(e -> e.getDataSchema() != qs).collect(Collectors.toList()).size() == 0,
				"At least one field not associated to the same parent data schema instance.");

		Validate.isTrue(
				qs.getFields().stream().filter(e -> e.getId() != null).collect(Collectors.toList()).isEmpty(),
				"At least one field id is not null.");

		return txControl
				.build()
				.required(() -> {
					em.persist(qs);
					return qs;
				});
	}

	@Override
	public void delete(int id) {
		txControl
				.build()
				.required(() -> {
					DataSchema schema = find(id);
					if (schema != null) em.remove(schema);
					return 0;
				});
	}

	@Override
	public void deleteAll() {
		txControl
				.build()
				.required(() -> {
					list().forEach(ds -> em.remove(ds));
					return 0;
				});
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<String> listNames() {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select ds.name From DataSchema ds").getResultList());
	}

	@SuppressWarnings("unchecked")
	@Override
	public DataSchema findByName(String name) {
		Validate.notBlank(name);
		return txControl
				.build()
				.readOnly()
				.supports(() -> {
					Iterator<DataSchema> iter = em
							.createQuery("Select ds From DataSchema ds Where ds.name = :name)")
							.setParameter("name", name)
							.getResultList().iterator();
					return (iter.hasNext()) ? iter.next() : null;
				});
	}

	@Override
	public DataSchema addOrUpdate(DataSchema dataSchema) {
		Validate.notNull(dataSchema);
		Validate.notNull(dataSchema.getFields());

		return txControl
				.build()
				.required(() -> {
					DataSchema prev = findByName(dataSchema.getName());
					if (prev != null) {
						prev.getFields().clear();
						prev = em.merge(prev);
						em.flush();

						for (DataSchemaField f : dataSchema.getFields()) {
							f.setDataSchema(prev);
							prev.getFields().add(f);
						}
						return em.merge(prev);
					} else {
						em.persist(dataSchema);
						return dataSchema;
					}
				});
	}
}
