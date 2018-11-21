package org.enquery.encryptedquery.querier.data.service;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.Decryption;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;


public interface ResourceUriRegistry {

	/**
	 * @return the baseUri
	 */
	String getBaseUri();

	Collection<Resource> list();

	Resource findByName(String name);

	String querySchemasUri(String dataSchemaId) throws UnsupportedEncodingException, URISyntaxException;

	String querySchemaUri(String dataSchemaId, String querySchemaId) throws UnsupportedEncodingException, URISyntaxException;

	String dataSchemaUri() throws UnsupportedEncodingException, URISyntaxException;

	String dataSchemaUri(String dataSchemaId) throws UnsupportedEncodingException, URISyntaxException;

	String dataSchemaUri(DataSchema dataSchema);

	String dataSourceUri(String dataSchemaId) throws UnsupportedEncodingException, URISyntaxException;

	String dataSourceUri(String dataSchemaId, String dataSourceId) throws UnsupportedEncodingException, URISyntaxException;

	String queriesUri(String dataSchemaId, String querySchemaId) throws UnsupportedEncodingException, URISyntaxException;

	String queryUri(String dataSchemaId, String querySchemaId, String queryId) throws UnsupportedEncodingException, URISyntaxException;

	String schedulesUri(String dataSchemaId, String querySchemaId, String queryId) throws UnsupportedEncodingException, URISyntaxException;

	String scheduleUri(String dataSchemaId, String querySchemaId, String queryId, String scheduleId) throws UnsupportedEncodingException, URISyntaxException;

	String resultsUri(String dataSchemaId, String querySchemaId, String queryId, String scheduleId) throws UnsupportedEncodingException, URISyntaxException;

	String resultsUri(Schedule jpa);

	String resultUri(String dataSchemaId, String querySchemaId, String queryId, String scheduleId, String resultId) throws UnsupportedEncodingException, URISyntaxException;

	String retrievalsUri(String dataSchemaId, String querySchemaId, String queryId, String scheduleId, String resultId) throws UnsupportedEncodingException, URISyntaxException;

	String retrievalUri(String dataSchemaId, String querySchemaId, String queryId, String scheduleId, String resultId, String retrievalId) throws UnsupportedEncodingException, URISyntaxException;

	String querySchemaUri(QuerySchema jpa);

	String queriesUri(QuerySchema jpaQuerySchema);

	String queryUri(Query jpa);

	String schedulesUri(Query jpaQuery);

	String resultUri(Result jpa);

	String scheduleUri(Schedule jpa);

	String retrievalsUri(Result jpa);

	String retrievalUri(Retrieval retrieval);

	String decryptionsUri(Retrieval retrieval);

	String decryptionUri(Decryption jpa);

}
