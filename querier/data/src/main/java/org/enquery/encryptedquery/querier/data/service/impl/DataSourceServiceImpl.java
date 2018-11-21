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

import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.service.DataSourceRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DataSourceServiceImpl implements DataSourceRepository {

	private static final Logger log = LoggerFactory.getLogger(DataSourceServiceImpl.class);

	@Reference(target = "(osgi.unit.name=querierPersistenUnit)")
	private JpaTemplate jpa;

	public DataSourceServiceImpl() {}


	@Override
	public DataSource find(int id) {
		return jpa.txExpr(TransactionType.Supports, em -> em.find(DataSource.class, id));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<DataSource> list() {
		log.info("Listing all Data Sources");
		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select ds From DataSource ds").getResultList());
	}

	@Override
	public DataSource update(DataSource ds) {
		Validate.notNull(ds);
		Validate.notNull(ds.getDataSchema());
		Validate.notNull(ds.getId());
		return jpa.txExpr(TransactionType.Required, em -> em.merge(ds));
	}

	@Override
	public DataSource add(DataSource ds) {
		Validate.notNull(ds);
		Validate.notNull(ds.getDataSchema());
		Validate.isTrue(ds.getId() == null);

		jpa.tx(em -> {
			em.persist(ds);
		});
		return ds;
	}

	@Override
	public void delete(int id) {
		jpa.tx(em -> {
			DataSource src = find(id);
			if (src != null) em.remove(src);
		});
	}


	@Override
	public void deleteAll() {
		jpa.tx(em -> {
			list().forEach(ds -> em.remove(ds));
		});
	}


	@SuppressWarnings("unchecked")
	@Override
	public Collection<String> listNames() {
		log.info("Listing names of all Data Sources");
		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select ds.name From DataSource ds")
						.getResultList());
	}


	@SuppressWarnings("unchecked")
	@Override
	public Collection<DataSource> withDataSchema(int dataSchemaId) {
		return jpa.txExpr(
				TransactionType.Supports,
				em -> em.createQuery("Select dsrc From DataSource dsrc "
						+ "Join Fetch dsrc.dataSchema dsch  "
						+ "Where dsch.id = :dSchema  ")
						.setParameter("dSchema", dataSchemaId)
						.getResultList());
	}


	@Override
	public DataSource addOrUpdate(DataSource ds) {
		Validate.notNull(ds);
		Validate.notNull(ds.getDataSchema());

		return jpa.txExpr(
				TransactionType.Required,
				em -> {
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
		return (DataSource) jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select ds From DataSource ds Where ds.name = :name")
						.setParameter("name", name)
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}
}
