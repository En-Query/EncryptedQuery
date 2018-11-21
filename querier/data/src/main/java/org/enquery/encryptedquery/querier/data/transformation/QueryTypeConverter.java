package org.enquery.encryptedquery.querier.data.transformation;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.json.Query;
import org.enquery.encryptedquery.querier.data.entity.json.QueryCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QueryResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.service.QueryStatusResolver;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;


@Component(service = QueryTypeConverter.class)
public class QueryTypeConverter {

	@Reference(target = "(type=rest-service)")
	private ResourceUriRegistry registry;
	@Reference
	private QuerySchemaTypeConverter querySchemaConverter;
	@Reference
	private DataSchemaTypeConverter dataSchemaConverter;

	@Reference
	private QueryStatusResolver queryStatusResolver;

	public Resource toResourceIdentifier(org.enquery.encryptedquery.querier.data.entity.jpa.Query jpa) {
		Validate.notNull(jpa);
		Resource result = new Resource();
		initialize(jpa, result);
		return result;
	}

	private void initialize(org.enquery.encryptedquery.querier.data.entity.jpa.Query jpa, Resource result) {
		result.setId(jpa.getId().toString());
		result.setSelfUri(registry.queryUri(jpa));
		result.setType(Query.TYPE);
	}

	public org.enquery.encryptedquery.querier.data.entity.json.Query toJSON(
			org.enquery.encryptedquery.querier.data.entity.jpa.Query jpaQuery) {

		Validate.notNull(jpaQuery);

		org.enquery.encryptedquery.querier.data.entity.json.Query result = new Query();
		initialize(jpaQuery, result);

		result.setName(jpaQuery.getName());
		result.setParameters(JSONConverter.toMapStringString(jpaQuery.getParameters()));
		result.setSelectorValues(JSONConverter.toList(jpaQuery.getSelectorValues()));
		result.setEmbedSelector(jpaQuery.getEmbedSelector());
		result.setQuerySchema(querySchemaConverter.toResourceIdentifier(jpaQuery.getQuerySchema()));
		result.setSchedulesUri(registry.schedulesUri(jpaQuery));

		return queryStatusResolver.resolve(result);
	}


	public org.enquery.encryptedquery.querier.data.entity.jpa.Query toJPA(QuerySchema querySchema, Query jsonQuery) throws JsonParseException, JsonMappingException, IOException {

		Validate.notNull(querySchema);
		Validate.notNull(jsonQuery);

		org.enquery.encryptedquery.querier.data.entity.jpa.Query result =
				new org.enquery.encryptedquery.querier.data.entity.jpa.Query();

		result.setName(jsonQuery.getName());
		result.setParameters(JSONConverter.toString(jsonQuery.getParameters()));
		result.setSelectorValues(JSONConverter.toString(jsonQuery.getSelectorValues()));
		result.setEmbedSelector(jsonQuery.getEmbedSelector());
		result.setQuerySchema(querySchema);

		return result;
	}


	public QueryCollectionResponse toQueryCollectionResponse(Collection<Query> jsonQueries) {
		return new QueryCollectionResponse(jsonQueries);
	}

	public QueryResponse toJSONResponse(org.enquery.encryptedquery.querier.data.entity.jpa.Query data) throws UnsupportedEncodingException, URISyntaxException {
		QueryResponse result = new QueryResponse(toJSON(data));
		result.setIncluded(JSONConverter.objectsToCollectionOfMaps(referencedObjects(data)));
		return result;
	}

	public Set<Resource> referencedObjects(org.enquery.encryptedquery.querier.data.entity.jpa.Query data) {
		QuerySchema querySchema = data.getQuerySchema();
		Validate.notNull(querySchema);
		return Sets.unionOf(
				querySchemaConverter.referencedObjects(querySchema),
				querySchemaConverter.toJSON(querySchema));
	}
}
