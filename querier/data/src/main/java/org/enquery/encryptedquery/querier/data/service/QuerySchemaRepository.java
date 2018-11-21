package org.enquery.encryptedquery.querier.data.service;

import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;


public interface QuerySchemaRepository {
	QuerySchema find(int id);

	QuerySchema findForDataSchema(DataSchema dataSchema, int id);

	QuerySchema findByNamefind(String name);

	Collection<QuerySchema> list();

	QuerySchema add(QuerySchema ds);

	QuerySchema update(QuerySchema ds);

	void delete(int id);

	Collection<String> listNames();

	Collection<QuerySchema> withDataSchema(DataSchema dataSchema);
}
