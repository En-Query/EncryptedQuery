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
import java.util.List;
import java.util.stream.Collectors;

import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSchemaField;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.enquery.encryptedquery.responder.data.entity.DataSource} service which we rest
 * enable from the {@link org.apache.camel.example.rest.UserRouteBuilder}.
 */
@Component
public class DataSchemaServiceImpl implements DataSchemaService {

	private static final Logger log = LoggerFactory.getLogger(DataSchemaServiceImpl.class);

	@Reference(target = "(osgi.unit.name=responderPersistenUnit)")
	private JpaTemplate jpa;

	public DataSchemaServiceImpl() {}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.example.rest.UserService#getUser(java.lang.String)
	 */
	@Override
	public DataSchema findByName(String name) {
		return jpa.txExpr(TransactionType.Supports,
				em -> em
						.createQuery("Select ds From DataSchema ds Where ds.name = :name", DataSchema.class)
						.setParameter("name", name)
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}

	@Override
	public DataSchema find(int id) {
		return jpa.txExpr(TransactionType.Supports, em -> em.find(DataSchema.class, id));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.example.rest.UserService#listUsers()
	 */
	@Override
	public Collection<DataSchema> list() {
		log.info("Getting all DataSchemas");
		List<DataSchema> result = jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select ds From DataSchema ds", DataSchema.class).getResultList());
		log.info("Returning DataSchema list of size: {}", result.size());
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.example.rest.UserService#updateUser(org.apache.camel.example.rest.User)
	 */
	@Override
	public DataSchema update(DataSchema ds) {
		validateDataSchema(ds);
		Validate.notNull(ds.getId());

		return jpa.txExpr(TransactionType.Required,
				em -> {
					DataSchema prev = em.find(DataSchema.class, ds.getId());
					Validate.notNull(prev, "Can't update non existing data schema: %s", ds.toString());

					prev.setName(ds.getName());
					prev.getFields().clear();
					// let hibernate delete orphans first, to avoid constraint violation errors
					em.flush();

					for (DataSchemaField f : ds.getFields()) {
						f.setDataSchema(prev);
						prev.getFields().add(f);
					}

					return em.merge(prev);
				});
	}

	@Override
	public DataSchema add(DataSchema dataSchema) {
		validateDataSchema(dataSchema);
		Validate.isTrue(dataSchema.getId() == null);
		jpa.tx(em -> {
			em.persist(dataSchema);
		});
		return dataSchema;
	}

	private void validateDataSchema(DataSchema dataSchema) {
		Validate.notNull(dataSchema);
		Validate.notBlank(dataSchema.getName(), "Name is required.");
		Validate.notNull(dataSchema.getFields(), "Fields are required.");
		Validate.isTrue(dataSchema.getFields().size() > 0, "Need at least one field.");
		log.info("Adding New Data Schema: {}", dataSchema.toString());
		Validate.isTrue(
				dataSchema.getFields().stream().map(e -> e.getFieldName()).distinct().collect(Collectors.toList()).size() == dataSchema.getFields().size(),
				"No unique field names found.");

		Validate.isTrue(
				dataSchema.getFields().stream().filter(e -> e.getDataSchema() != dataSchema).collect(Collectors.toList()).size() == 0,
				"At least one field not associated to the same parent data schema instance.");

		Validate.isTrue(
				dataSchema.getFields().stream().filter(e -> e.getId() != null).collect(Collectors.toList()).isEmpty(),
				"At least one field id is not null.");
	}

	@Override
	public void delete(String name) {
		jpa.tx(em -> {
			DataSchema dataSchema = findByName(name);
			if (dataSchema != null) {
				em.remove(dataSchema);
			}
		});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.data.service.DataSchemaService#addOrUpdate(org.enquery.
	 * encryptedquery.responder.data.entity.DataSchema)
	 */
	@Override
	public DataSchema addOrUpdate(DataSchema dataSchema) {
		validateDataSchema(dataSchema);

		DataSchema[] result = {dataSchema};
		jpa.tx(em -> {
			DataSchema prev = findByName(dataSchema.getName());
			if (prev != null) {
				prev.getFields().clear();
				prev = em.merge(prev);
				em.flush();

				for (DataSchemaField f : dataSchema.getFields()) {
					f.setDataSchema(prev);
					prev.getFields().add(f);
				}
				result[0] = em.merge(prev);
			} else {
				em.persist(dataSchema);
			}
		});
		return result[0];
	}

}
