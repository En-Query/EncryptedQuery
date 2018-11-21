package org.enquery.encryptedquery.querier.data.service;

import org.enquery.encryptedquery.querier.data.entity.json.Query;
import org.enquery.encryptedquery.querier.data.entity.json.QueryCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QueryResponse;


public interface QueryStatusResolver {

	QueryCollectionResponse resolve(QueryCollectionResponse response);

	QueryResponse resolve(QueryResponse response);

	Query resolve(Query query);

}
