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


import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchemaField;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.enquery.encryptedquery.responder.data.entity.DataSource} service which we rest
 * enable from the {@link org.apache.camel.example.rest.UserRouteBuilder}.
 */
@Component
public class DataSchemaRepositoryImpl implements DataSchemaRepository {

	private static final Logger log = LoggerFactory.getLogger(DataSchemaRepositoryImpl.class);

	@Reference(target = "(osgi.unit.name=querierPersistenUnit)")
	private JpaTemplate jpa;

	public DataSchemaRepositoryImpl() {}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.example.rest.UserService#getUser(java.lang.String)
	 */
	@Override
	public DataSchema find(int id) {
		return jpa.txExpr(TransactionType.Supports, em -> em.find(DataSchema.class, id));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.camel.example.rest.UserService#listUsers()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Collection<DataSchema> list() {
		log.info("Listing all Data Schemas");
		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select ds From DataSchema ds").getResultList());
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

		jpa.tx(em -> {
			em.persist(qs);
		});
		return qs;
	}

	@Override
	public void delete(int id) {
		jpa.tx(em -> {
			DataSchema schema = find(id);
			if (schema != null) em.remove(schema);
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
		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select ds.name From DataSchema ds").getResultList());
	}

	@SuppressWarnings("unchecked")
	@Override
	public DataSchema findByName(String name) {
		Validate.notBlank(name);
		return jpa.txExpr(TransactionType.Supports,
				em -> {
					Iterator<DataSchema> iter = em
							.createQuery("Select ds From DataSchema ds Where ds.name = :name)")
							.setParameter("name", name)
							.getResultList().iterator();
					return (iter.hasNext()) ? iter.next() : null;
				});
	}

	@Override
	public DataSchema addOrUpdate(DataSchema ds) {
		Validate.notNull(ds);
		Validate.notNull(ds.getFields());

		return jpa.txExpr(
				TransactionType.Required,
				em -> {
					if (ds.getId() != null) return update(ds);
					DataSchema prev = findByName(ds.getName());
					if (prev != null) {
						copy(prev, ds);
						return update(prev);
					} else {
						return add(ds);
					}
				});
	}

	/**
	 * Deep copy without overriding destination IDs
	 * 
	 * @param dest
	 * @param source
	 */
	private void copy(DataSchema dest, DataSchema source) {
		dest.setName(source.getName());

		List<DataSchemaField> addedFields = new ArrayList<>();

		// find updated and added fields
		for (DataSchemaField srcField : source.getFields()) {
			DataSchemaField destField = findField(dest.getFields(), srcField);
			if (destField == null) {
				addedFields.add(srcField);
			} else {
				log.info("Updating: " + destField);
				copy(destField, srcField);
			}
		}

		// remove deleted fields
		Iterator<DataSchemaField> destFldIter = dest.getFields().iterator();
		while (destFldIter.hasNext()) {
			DataSchemaField destField = destFldIter.next();
			DataSchemaField srcField = findField(source.getFields(), destField);
			if (srcField == null) {
				log.info("Removing: " + destField);
				destFldIter.remove();
			}
		}

		// add new fields
		for (DataSchemaField srcField : addedFields) {
			Validate.isTrue(srcField.getId() == null, "new field should not have id");
			DataSchemaField destField = new DataSchemaField();
			copy(destField, srcField);
			destField.setDataSchema(dest);
			dest.getFields().add(destField);
			log.info("Adding: " + destField);
		}
	}

	private void copy(DataSchemaField destField, DataSchemaField srcField) {
		destField.setDataType(srcField.getDataType());
		destField.setFieldName(srcField.getFieldName());
		destField.setIsArray(srcField.getIsArray());
		destField.setPosition(srcField.getPosition());
	}

	private DataSchemaField findField(List<DataSchemaField> list, DataSchemaField field) {
		return list
				.stream()
				.filter(f -> field.getFieldName().equals(f.getFieldName()))
				.findFirst()
				.orElse(null);
	}
}
