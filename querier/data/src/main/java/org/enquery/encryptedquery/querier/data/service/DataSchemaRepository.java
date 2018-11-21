package org.enquery.encryptedquery.querier.data.service;

import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;


public interface DataSchemaRepository {
	DataSchema find(int id);

	DataSchema findByName(String name);

	Collection<DataSchema> list();

	DataSchema add(DataSchema ds);

	DataSchema addOrUpdate(DataSchema ds);

	DataSchema update(DataSchema ds);

	void delete(int id);

	void deleteAll();

	Collection<String> listNames();
}
