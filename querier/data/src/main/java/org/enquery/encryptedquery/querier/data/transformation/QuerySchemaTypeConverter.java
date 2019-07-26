package org.enquery.encryptedquery.querier.data.transformation;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaField;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = QuerySchemaTypeConverter.class)
public class QuerySchemaTypeConverter {

	@Reference
	private DataSchemaTypeConverter dataSchemaConverter;
	@Reference(target = "(type=rest-service)")
	private ResourceUriRegistry registry;

	public Resource toResourceIdentifier(org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema jpa) {
		Validate.notNull(jpa);
		Resource result = new Resource();
		initialize(jpa, result);
		return result;
	}

	private void initialize(org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema jpa, Resource result) {
		result.setId(jpa.getId().toString());
		result.setSelfUri(registry.querySchemaUri(jpa));
		result.setType(QuerySchema.TYPE);
	}

	public QuerySchema toJSON(org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema jpaQuerySchema) {
		final QuerySchema result = new QuerySchema();
		initialize(jpaQuerySchema, result);
		result.setName(jpaQuerySchema.getName());
		result.setSelectorField(jpaQuerySchema.getSelectorField());
		result.setDataSchema(dataSchemaConverter.toResourceIdentifier(jpaQuerySchema.getDataSchema()));
		result.setQueriesUri(registry.queriesUri(jpaQuerySchema));

		List<QuerySchemaField> fields = new ArrayList<>();
		for (org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField f : jpaQuerySchema.getFields()) {
			fields.add(toJSONQuerySchemaField(f));
		}

		result.setFields(fields);
		return result;
	}


	public QuerySchemaField toJSONQuerySchemaField(org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField f) {
		QuerySchemaField result = new QuerySchemaField();
		result.setName(f.getName());
		result.setSize(f.getMaxSize());
		result.setMaxArrayElements(f.getMaxArrayElements());
		return result;
	}


	public org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField toJPAQuerySchemaField(QuerySchemaField jsonFld) {
		org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField result = new org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField();
		result.setName(jsonFld.getName());
		result.setMaxArrayElements(jsonFld.getMaxArrayElements());
		result.setMaxSize(jsonFld.getSize());
		return result;
	}


	public org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema toJPA(DataSchema dataSchema, QuerySchema jsonQuerySchema) {
		Validate.notNull(dataSchema);
		Validate.notNull(jsonQuerySchema);

		org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema result = new org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema();
		result.setDataSchema(dataSchema);
		result.setName(jsonQuerySchema.getName());
		result.setSelectorField(jsonQuerySchema.getSelectorField());

		result.getFields().clear();
		for (QuerySchemaField jsonFld : jsonQuerySchema.getFields()) {
			org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField jpaFld = toJPAQuerySchemaField(jsonFld);
			jpaFld.setQuerySchema(result);
			result.getFields().add(jpaFld);
		}

		return result;
	}


	public QuerySchemaCollectionResponse toJSONResponse(Collection<org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema> data) {
		List<QuerySchema> list = new ArrayList<>(data.size());
		data.forEach(jpa -> {
			list.add(toJSON(jpa));
		});
		return new QuerySchemaCollectionResponse(list);
	}


	public QuerySchemaResponse toJSONResponse(org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema jpa) throws UnsupportedEncodingException, URISyntaxException {
		Validate.notNull(jpa);
		Validate.notNull(jpa.getDataSchema());

		QuerySchemaResponse result = new QuerySchemaResponse(toJSON(jpa));
		result.setIncluded(JSONConverter.objectsToCollectionOfMaps(referencedObjects(jpa)));
		return result;
	}

	public Set<Resource> referencedObjects(org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema jpa) {
		return Sets.unionOf(
				dataSchemaConverter.referencedObjects(jpa.getDataSchema()),
				dataSchemaConverter.toJSON(jpa.getDataSchema()));
	}

	public org.enquery.encryptedquery.data.QuerySchema toCoreQuerySchema(org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema querySchema) {
		org.enquery.encryptedquery.data.QuerySchema result = new org.enquery.encryptedquery.data.QuerySchema();
		result.setName(querySchema.getName());
		result.setSelectorField(querySchema.getSelectorField());
		result.setDataSchema(dataSchemaConverter.toCoreDataSchema(querySchema.getDataSchema()));

		ArrayList<QuerySchemaElement> elements = new ArrayList<>();
		for (org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField field : querySchema.getFields()) {
			elements.add(toCoreQuerySchemaField(field));
		}

		result.setElementList(elements);
		return result;
	}

	private QuerySchemaElement toCoreQuerySchemaField(org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField field) {
		QuerySchemaElement result = new QuerySchemaElement();
		result.setName(field.getName());
		// result.setLengthType(field.getLengthType());
		result.setMaxArrayElements(field.getMaxArrayElements());
		result.setSize(field.getMaxSize());
		return result;
	}
}
