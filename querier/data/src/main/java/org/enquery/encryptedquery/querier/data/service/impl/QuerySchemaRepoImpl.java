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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;
import org.enquery.encryptedquery.querier.data.service.QuerySchemaRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.enquery.encryptedquery.responder.data.entity.DataSource} service which we rest
 * enable from the {@link org.apache.camel.example.rest.UserRouteBuilder}.
 */
@Component
public class QuerySchemaRepoImpl implements QuerySchemaRepository {

	private static final Logger log = LoggerFactory.getLogger(QuerySchemaRepoImpl.class);

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
	public QuerySchema find(int id) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> {
					return em.find(QuerySchema.class, id, fetchAllAssociationsHint(em));
				});
		// result.getDataSchema();
		// return result;
	}

	@SuppressWarnings("rawtypes")
	private Map<String, Object> fetchAllAssociationsHint(EntityManager em) {
		EntityGraph graph = em.getEntityGraph(QuerySchema.ALL_ENTITY_GRAPH);
		Map<String, Object> hints = new HashMap<>();
		hints.put("javax.persistence.fetchgraph", graph);
		return hints;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.example.rest.UserService#listUsers()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Collection<QuerySchema> list() {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select qs From QuerySchema qs")
						.setHint("javax.persistence.fetchgraph", em.getEntityGraph(QuerySchema.ALL_ENTITY_GRAPH))
						.getResultList());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.example.rest.UserService#updateUser(org.apache.camel.example.rest.User)
	 */
	@Override
	public QuerySchema update(QuerySchema ds) {
		return txControl
				.build()
				.required(() -> em.merge(ds));
	}

	@Override
	public QuerySchema add(QuerySchema qs) {
		Validate.notNull(qs);
		Validate.isTrue(qs.getId() == null);
		Validate.notNull(qs.getDataSchema());
		Validate.notNull(qs.getName());
		Validate.notNull(qs.getFields());
		Validate.isTrue(qs.getFields().size() > 0, "Need at least one field.");
		Validate.isTrue(
				qs.getFields().stream().map(e -> e.getName()).distinct().collect(Collectors.toList()).size() == qs.getFields().size(),
				"No unique field names found.");

		Validate.isTrue(
				qs.getFields().stream().filter(e -> e.getQuerySchema() != qs).collect(Collectors.toList()).size() == 0,
				"At least one field not associated to the same parent query schema instance.");

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
					QuerySchema qs = find(id);
					if (qs != null) em.remove(qs);
					return 0;
				});
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<String> listNames() {
		log.info("Getting the names of all Query Schemas");
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select qs.name From QuerySchema qs").getResultList());
	}

	@SuppressWarnings("unchecked")
	@Override
	public QuerySchema findByNamefind(String name) {
		return (QuerySchema) txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select qs From QuerySchema qs Where qs.name = :name")
						.setParameter("name", name)
						.setHint("javax.persistence.fetchgraph", em.getEntityGraph(QuerySchema.ALL_ENTITY_GRAPH))
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<QuerySchema> withDataSchema(DataSchema dataSchema) {
		Validate.notNull(dataSchema);
		return txControl
				.build()
				.readOnly()
				.supports(() -> em
						.createQuery("Select Distinct qs  "
								+ "From QuerySchema qs  "
								+ "Inner Join qs.dataSchema ds  "
								+ "With ds = :dataSchema")
						.setParameter("dataSchema", em.find(DataSchema.class, dataSchema.getId()))
						.setHint("javax.persistence.fetchgraph", em.getEntityGraph(QuerySchema.ALL_ENTITY_GRAPH))
						.getResultList());
	}

	@Override
	public QuerySchema findForDataSchema(DataSchema dataSchema, int id) {
		Validate.notNull(dataSchema);
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select qs "
						+ "From QuerySchema qs  "
						+ "Where qs.id = :id "
						+ "And   qs.dataSchema = :dataSchema ",
						QuerySchema.class)
						.setParameter("id", id)
						.setParameter("dataSchema", em.find(DataSchema.class, dataSchema.getId()))
						.setHint("javax.persistence.fetchgraph", em.getEntityGraph(QuerySchema.ALL_ENTITY_GRAPH))
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}
}
