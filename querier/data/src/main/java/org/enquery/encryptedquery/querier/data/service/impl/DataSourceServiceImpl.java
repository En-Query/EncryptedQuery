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

import javax.persistence.EntityManager;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.service.DataSourceRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProvider;

@Component
public class DataSourceServiceImpl implements DataSourceRepository {

	@Reference(target = "(osgi.unit.name=querierPersistenUnit)")
	private JPAEntityManagerProvider provider;
	@Reference
	private TransactionControl txControl;
	private EntityManager em;

	@Activate
	void init() {
		em = provider.getResource(txControl);
	}

	@Override
	public DataSource find(int id) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.find(DataSource.class, id));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<DataSource> list() {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select ds From DataSource ds").getResultList());
	}

	@Override
	public DataSource update(DataSource ds) {
		Validate.notNull(ds);
		Validate.notNull(ds.getDataSchema());
		Validate.notNull(ds.getId());
		return txControl
				.build()
				.required(() -> em.merge(ds));
	}

	@Override
	public DataSource add(DataSource ds) {
		Validate.notNull(ds);
		Validate.notNull(ds.getDataSchema());
		Validate.isTrue(ds.getId() == null);

		return txControl
				.build()
				.required(() -> {
					em.persist(ds);
					return ds;
				});
	}

	@Override
	public void delete(int id) {
		txControl
				.build()
				.required(() -> {
					DataSource src = find(id);
					if (src != null) em.remove(src);
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
				.supports(() -> em.createQuery("Select ds.name From DataSource ds")
						.getResultList());
	}


	@SuppressWarnings("unchecked")
	@Override
	public Collection<DataSource> withDataSchema(int dataSchemaId) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select dsrc From DataSource dsrc "
						+ "Join Fetch dsrc.dataSchema dsch  "
						+ "Where dsch.id = :dSchema  ")
						.setParameter("dSchema", dataSchemaId)
						.getResultList());
	}


	@Override
	public DataSource addOrUpdate(DataSource ds) {
		Validate.notNull(ds);
		Validate.notNull(ds.getDataSchema());

		return txControl
				.build()
				.required(() -> {
					if (ds.getId() != null) return update(ds);
					DataSource prev = findByName(ds.getName());
					if (prev != null) {
						copy(prev, ds);
						return update(prev);
					} else {
						return add(ds);
					}
				});
	}

	private void copy(DataSource to, DataSource from) {
		to.setDescription(from.getDescription());
		to.setName(from.getName());
		to.setDataSchema(from.getDataSchema());
	}


	@SuppressWarnings("unchecked")
	@Override
	public DataSource findByName(String name) {
		return (DataSource) txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select ds From DataSource ds Where ds.name = :name")
						.setParameter("name", name)
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}
}
