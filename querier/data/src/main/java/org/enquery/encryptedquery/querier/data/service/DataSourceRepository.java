package org.enquery.encryptedquery.querier.data.service;

import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;



public interface DataSourceRepository {
	DataSource find(int id);

	DataSource findByName(String name);

	Collection<DataSource> list();

	Collection<DataSource> withDataSchema(int dataSchemaId);

	DataSource add(DataSource ds);

	DataSource update(DataSource ds);

	DataSource addOrUpdate(DataSource ds);

	void delete(int id);

	void deleteAll();

	Collection<String> listNames();
}
