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
		return jpa.txExpr(TransactionType.Required, em -> em.merge(ds));
	}

	@Override
	public DataSchema add(DataSchema ds) {
		Validate.notNull(ds);
		Validate.isTrue(ds.getId() == null);
		Validate.notBlank(ds.getName(), "Name is required.");
		Validate.notNull(ds.getFields(), "Fields are required.");
		Validate.isTrue(ds.getFields().size() > 0, "Need at least one field.");
		log.info("Adding New Data Schema: {}", ds.toString());
		Validate.isTrue(
				ds.getFields().stream().map(e -> e.getFieldName()).distinct().collect(Collectors.toList()).size() == ds.getFields().size(),
				"No unique field names found.");

		Validate.isTrue(
				ds.getFields().stream().filter(e -> e.getDataSchema() != ds).collect(Collectors.toList()).size() == 0,
				"At least one field not associated to the same parent data schema instance.");

		Validate.isTrue(
				ds.getFields().stream().filter(e -> e.getId() != null).collect(Collectors.toList()).isEmpty(),
				"At least one field id is not null.");

		jpa.tx(em -> {
			em.persist(ds);
		});
		return ds;
	}

	@Override
	public void delete(String name) {
		jpa.tx(em -> em.remove(findByName(name)));
	}

}
