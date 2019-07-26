package org.enquery.encryptedquery.querier.data.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;

public interface QueryRepository {
	Query find(int id);

	Query findForQuerySchema(QuerySchema querySchema, int id);

	Query findByName(String name);

	Collection<Query> list();

	Query add(Query q);

	Query update(Query q);

	void delete(int id);

	void deleteAll();

	Collection<String> listNames();

	Collection<Query> withQuerySchema(int querySchemaId);

	/**
	 * If The query has been generated. Since the query can be very large, this allows to check
	 * without loading the content.
	 * 
	 * @return
	 */
	boolean isGenerated(int queryId);

	/**
	 * Save the query bytes (encrypted query) may override previous value.
	 * 
	 * @param queryId
	 * @param inputStream
	 * @return
	 * @throws IOException
	 */
	Query updateQueryBytes(int queryId, InputStream inputStream) throws IOException;

	/**
	 * Load the bytes of a given Query.
	 * 
	 * @param queryId
	 * @return InputStream from which the Query bytes can be read. Client is responsible for closing
	 *         this stream.
	 * @throws IOException
	 */
	InputStream loadQueryBytes(int queryId) throws IOException;

	/**
	 * Save the encryption keys of a given Query. May override previous value.
	 * 
	 * @param queryId
	 * @param inputStream
	 * @return
	 * @throws IOException
	 */
	Query updateQueryKeyBytes(int queryId, InputStream inputStream) throws IOException;

	/**
	 * Load the encryption keys of a given Query.
	 * 
	 * @param queryId
	 * @return InputStream from which the Query keys can be read. Client is responsible for closing
	 * @throws IOException
	 */
	InputStream loadQueryKeyBytes(int queryId) throws IOException;


	/**
	 * Update query error message from the passed Exception. This error could be during encryption.
	 * 
	 * @param query
	 * @param ex
	 * @return
	 * @throws IOException
	 */
	Query updateWithError(Query query, Exception ex) throws IOException;
	//
	// /**
	// * @param dataSource
	// * @return
	// */
	// Collection<Query> listForDataSource(DataSource dataSource);

}
