package org.enquery.encryptedquery.querier.data.service;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.Decryption;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.transformation.URIUtils;

public class ResourceUriRegistryImpl implements ResourceUriRegistry {
	private static final String DATASCHEMA = "dataschema";

	private final Map<String, Resource> registry = new HashMap<>();
	private String baseUri;

	public ResourceUriRegistryImpl() {}

	public ResourceUriRegistryImpl(String baseUri) {
		setBaseUri(baseUri);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.encryptedquery.querier.integration.RestServiceRegistry#getBaseUri()
	 */

	@Override
	public String getBaseUri() {
		return baseUri;
	}

	/**
	 * @param baseUri the baseUri to set
	 */
	public void setBaseUri(String baseUri) {
		this.baseUri = baseUri;
		Resource r = make(DATASCHEMA, "dataschemas");
		add(r);
	}

	private Resource make(String name, String relativeUri) {
		Resource result = new Resource();
		result.setId(name);
		result.setSelfUri(URIUtils.concat(baseUri, relativeUri).toString());
		return result;
	}

	private void add(Resource restEndpoint) {
		registry.put(restEndpoint.getId(), restEndpoint);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.encryptedquery.querier.integration.RestServiceRegistry#list()
	 */

	@Override
	public Collection<Resource> list() {
		return registry.values();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.encryptedquery.querier.integration.RestServiceRegistry#findByName(java.lang.String)
	 */

	@Override
	public Resource findByName(String name) {
		return registry.get(name);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.integration.RestServiceRegistry#querySchemasUri(java.lang.String)
	 */

	@Override
	public String querySchemasUri(String dataSchemaId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(dataSchemaUri(dataSchemaId), "queryschemas").toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.integration.RestServiceRegistry#querySchemaUri(java.lang.String,
	 * java.lang.String)
	 */

	@Override
	public String querySchemaUri(String dataSchemaId, String querySchemaId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(querySchemasUri(dataSchemaId), querySchemaId).toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.encryptedquery.querier.integration.RestServiceRegistry#dataSchemaUri()
	 */

	@Override
	public String dataSchemaUri() throws UnsupportedEncodingException, URISyntaxException {
		return findByName(DATASCHEMA).getSelfUri().toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.integration.RestServiceRegistry#dataSchemaUri(java.lang.String)
	 */

	@Override
	public String dataSchemaUri(String dataSchemaId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(findByName(DATASCHEMA).getSelfUri(), dataSchemaId).toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.integration.RestServiceRegistry#dataSourceUri(java.lang.String)
	 */

	@Override
	public String dataSourceUri(String dataSchemaId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(dataSchemaUri(dataSchemaId), "datasources").toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.integration.RestServiceRegistry#dataSourceUri(java.lang.String,
	 * java.lang.String)
	 */

	@Override
	public String dataSourceUri(String dataSchemaId, String dataSourceId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(dataSourceUri(dataSchemaId), dataSourceId).toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.encryptedquery.querier.integration.RestServiceRegistry#queriesUri(java.lang.String,
	 * java.lang.String)
	 */

	@Override
	public String queriesUri(String dataSchemaId, String querySchemaId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(querySchemaUri(dataSchemaId, querySchemaId), "queries").toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.encryptedquery.querier.integration.RestServiceRegistry#queryUri(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */

	@Override
	public String queryUri(String dataSchemaId, String querySchemaId, String queryId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(queriesUri(dataSchemaId, querySchemaId), queryId).toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.integration.RestServiceRegistry#schedulesUri(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */

	@Override
	public String schedulesUri(String dataSchemaId, String querySchemaId, String queryId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(queryUri(dataSchemaId, querySchemaId, queryId), "schedules").toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.encryptedquery.querier.integration.RestServiceRegistry#scheduleUri(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String)
	 */

	@Override
	public String scheduleUri(String dataSchemaId, String querySchemaId, String queryId, String scheduleId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(schedulesUri(dataSchemaId, querySchemaId, queryId), scheduleId).toString();
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.encryptedquery.querier.integration.RestServiceRegistry#resultsUri(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String)
	 */

	@Override
	public String resultsUri(String dataSchemaId, String querySchemaId, String queryId, String scheduleId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(scheduleUri(dataSchemaId, querySchemaId, queryId, scheduleId), "results").toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.encryptedquery.querier.integration.RestServiceRegistry#resultUri(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */

	@Override
	public String resultUri(String dataSchemaId, String querySchemaId, String queryId, String scheduleId, String resultId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(resultsUri(dataSchemaId, querySchemaId, queryId, scheduleId), resultId).toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.integration.RestServiceRegistry#retrievalsUri(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */

	@Override
	public String retrievalsUri(String dataSchemaId, String querySchemaId, String queryId, String scheduleId, String resultId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(resultUri(dataSchemaId, querySchemaId, queryId, scheduleId, resultId), "retrievals").toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.integration.RestServiceRegistry#retrievalUri(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */

	@Override
	public String retrievalUri(String dataSchemaId, String querySchemaId, String queryId, String scheduleId, String resultId, String retrievalId) throws UnsupportedEncodingException, URISyntaxException {
		return URIUtils.concat(retrievalsUri(dataSchemaId, querySchemaId, queryId, scheduleId, resultId), retrievalId).toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.integration.RestServiceRegistry#decryptionsUri(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */

	// @Override
	// public String decryptionsUri(String dataSchemaId,
	// String querySchemaId,
	// String queryId,
	// String scheduleId,
	// String resultId,
	// String retrievalId) throws UnsupportedEncodingException, URISyntaxException {
	// return URIUtils.concat(
	// retrievalUri(
	// dataSchemaId,
	// querySchemaId,
	// queryId,
	// scheduleId,
	// resultId,
	// retrievalId),
	// "decryptions").toString();
	// }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.integration.RestServiceRegistry#decryptionUri(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String,
	 * java.lang.String)
	 */
	// @Override
	// public String decryptionUri(String dataSchemaId,
	// String querySchemaId,
	// String queryId,
	// String scheduleId,
	// String resultId,
	// String retrievalId,
	// String decryptionId) throws UnsupportedEncodingException, URISyntaxException {
	//
	// return URIUtils.concat(
	// decryptionsUri(
	// dataSchemaId,
	// querySchemaId,
	// queryId,
	// scheduleId,
	// resultId,
	// retrievalId),
	// decryptionId)
	// .toString();
	// }

	@Override
	public String resultsUri(Schedule jpa) {
		Validate.notNull(jpa);

		final String scheduleId = jpa.getId().toString();
		final Query query = jpa.getQuery();
		final QuerySchema querySchema = query.getQuerySchema();

		final String queryId = query.getId().toString();
		final String querySchemaId = querySchema.getId().toString();
		final String dataSchemaId = querySchema.getDataSchema().getId().toString();

		try {
			return resultsUri(dataSchemaId, querySchemaId, queryId, scheduleId);
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error builind Results URI.", e);
		}
	}

	@Override
	public String dataSchemaUri(DataSchema dataSchema) {
		Validate.notNull(dataSchema);
		try {
			return dataSchemaUri(dataSchema.getId().toString());
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error buildind DataSchema URI.", e);
		}
	}

	@Override
	public String querySchemaUri(QuerySchema jpaQuerySchema) {
		Validate.notNull(jpaQuerySchema);

		final String dataSchemaId = jpaQuerySchema.getDataSchema().getId().toString();
		final String querySchemaId = jpaQuerySchema.getId().toString();
		try {
			return querySchemaUri(dataSchemaId, querySchemaId);
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error buildind QuerySchema URI.", e);
		}
	}

	@Override
	public String queriesUri(QuerySchema jpaQuerySchema) {
		Validate.notNull(jpaQuerySchema);

		final String dataSchemaId = jpaQuerySchema.getDataSchema().getId().toString();
		final String querySchemaId = jpaQuerySchema.getId().toString();
		try {
			return queriesUri(dataSchemaId, querySchemaId);
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error buildind Queries URI.", e);
		}
	}

	@Override
	public String queryUri(Query jpa) {
		Validate.notNull(jpa);

		final String queryId = jpa.getId().toString();
		final String dataSchemaId = jpa.getQuerySchema().getDataSchema().getId().toString();
		final String querySchemaId = jpa.getQuerySchema().getId().toString();
		try {
			return queryUri(dataSchemaId, querySchemaId, queryId);
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error buildind Query URI.", e);
		}
	}

	@Override
	public String schedulesUri(Query jpa) {
		Validate.notNull(jpa);

		final String queryId = jpa.getId().toString();
		final String dataSchemaId = jpa.getQuerySchema().getDataSchema().getId().toString();
		final String querySchemaId = jpa.getQuerySchema().getId().toString();

		try {
			return schedulesUri(dataSchemaId, querySchemaId, queryId);
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error buildind Schedules URI.", e);
		}
	}

	@Override
	public String resultUri(Result jpa) {
		Validate.notNull(jpa);

		final String resultId = jpa.getId().toString();
		final String scheduleId = jpa.getSchedule().getId().toString();
		final String queryId = jpa.getSchedule().getQuery().getId().toString();
		final String querySchemaId =
				jpa.getSchedule().getQuery().getQuerySchema().getId().toString();
		final String dataSchemaId =
				jpa.getSchedule().getQuery().getQuerySchema().getDataSchema().getId().toString();

		try {
			return resultUri(dataSchemaId, querySchemaId, queryId, scheduleId, resultId);
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error buildind Result URI.", e);
		}
	}

	@Override
	public String scheduleUri(Schedule jpa) {
		Validate.notNull(jpa);
		final String scheduleId = jpa.getId().toString();
		final Query query = jpa.getQuery();
		final QuerySchema querySchema = query.getQuerySchema();

		final String queryId = query.getId().toString();
		final String querySchemaId = querySchema.getId().toString();
		final String dataSchemaId = querySchema.getDataSchema().getId().toString();

		try {
			return scheduleUri(dataSchemaId, querySchemaId, queryId, scheduleId);
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error buildind Schedule URI.", e);
		}
	}

	@Override
	public String retrievalsUri(Result jpa) {
		Validate.notNull(jpa);

		final Query query = jpa.getSchedule().getQuery();
		final QuerySchema querySchema = query.getQuerySchema();

		final String resultId = jpa.getId().toString();
		final String scheduleId = jpa.getSchedule().getId().toString();
		final String queryId = query.getId().toString();
		final String querySchemaId = querySchema.getId().toString();
		final String dataSchemaId = querySchema.getDataSchema().getId().toString();

		try {
			return retrievalsUri(dataSchemaId, querySchemaId, queryId, scheduleId, resultId);
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error buildind Retrieval URI.", e);
		}
	}

	@Override
	public String retrievalUri(Retrieval jpa) {
		Validate.notNull(jpa);

		final org.enquery.encryptedquery.querier.data.entity.jpa.Result jpaResult = jpa.getResult();
		final Schedule schedule = jpaResult.getSchedule();
		final Query query = schedule.getQuery();
		final QuerySchema querySchema = query.getQuerySchema();

		final String retrievalId = jpa.getId().toString();
		final String resultId = jpaResult.getId().toString();
		final String scheduleId = schedule.getId().toString();
		final String queryId = query.getId().toString();
		final String querySchemaId = querySchema.getId().toString();
		final String dataSchemaId = querySchema.getDataSchema().getId().toString();

		try {
			return retrievalUri(dataSchemaId, querySchemaId, queryId, scheduleId, resultId, retrievalId);
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error building Retrievals URI.", e);
		}
	}

	@Override
	public String decryptionsUri(Retrieval jpa) {
		Validate.notNull(jpa);
		return URIUtils.concat(retrievalUri(jpa), "decryptions").toString();
	}

	@Override
	public String decryptionUri(Decryption jpa) {
		return URIUtils.concat(decryptionsUri(jpa.getRetrieval()), jpa.getId().toString()).toString();
	}

}
