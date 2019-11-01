package org.enquery.encryptedquery.querier.data.transformation;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.json.Error;
import org.enquery.encryptedquery.querier.data.entity.json.Query;
import org.enquery.encryptedquery.querier.data.entity.json.QueryCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QueryResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.entity.json.Source;
import org.enquery.encryptedquery.querier.data.service.QueryStatusResolver;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonPointer;
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
		result.setQuerySchema(querySchemaConverter.toResourceIdentifier(jpaQuery.getQuerySchema()));
		result.setSchedulesUri(registry.schedulesUri(jpaQuery));
		result.setFilterExpression(jpaQuery.getFilterExpression());

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
		result.setQuerySchema(querySchema);
		result.setFilterExpression(jsonQuery.getFilterExpression());

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

	public QueryResponse makeSyntaxErrorResponse(Collection<String> errorMessages) throws UnsupportedEncodingException, URISyntaxException {
		JsonPointer pointer = JsonPointer.valueOf("/filterExpression");

		Source source = new Source();
		source.setPointer(pointer.toString());

		QueryResponse result = new QueryResponse();
		Collection<Error> errors = errorMessages
				.stream()
				.map(s -> {
					Error e = new Error();
					e.setTitle("Syntax Error");
					e.setDetail(s);
					e.setSource(source);
					return e;
				}).collect(Collectors.toList());

		result.setErrors(errors);
		return result;
	}

	public QueryResponse makeNameAlreadyExistsErrorResponse(String querySchemaName, String queryName) {
		Source source = new Source();
		source.setPointer(JsonPointer.valueOf("/name").toString());

		QueryResponse result = new QueryResponse();
		Error e = new Error();
		e.setTitle("Duplicate Name");
		e.setDetail(String.format("Query name '%s' already exists for Query Schema '%s'.",
				queryName,
				querySchemaName));

		e.setSource(source);
		result.setErrors(Arrays.asList(e));

		return result;
	}

	public QueryResponse makePersistenceErrorResponse() {
		Source source = new Source();
		source.setPointer(JsonPointer.valueOf("/").toString());

		QueryResponse result = new QueryResponse();
		Error e = new Error();
		e.setTitle("Persistence");
		e.setDetail("There was a problem saving your query.");
		e.setSource(source);
		result.setErrors(Arrays.asList(e));

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
