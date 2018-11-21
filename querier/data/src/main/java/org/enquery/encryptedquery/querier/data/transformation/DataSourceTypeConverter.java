package org.enquery.encryptedquery.querier.data.transformation;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.DataSourceType;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.json.DataSource;
import org.enquery.encryptedquery.querier.data.entity.json.DataSourceCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSourceResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = DataSourceTypeConverter.class)
public class DataSourceTypeConverter {

	@Reference
	private DataSchemaRepository dataSchemaRepo;
	@Reference
	private DataSchemaTypeConverter dataSchemaConverter;
	@Reference(target = "(type=rest-service)")
	private ResourceUriRegistry registry;

	public Resource toResourceIdentifier(org.enquery.encryptedquery.querier.data.entity.jpa.DataSource jpa) {
		Validate.notNull(jpa);
		try {
			Resource result = new Resource();
			initialize(jpa, result);
			return result;
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error converting DataSource from JPA to JSON format.", e);
		}
	}

	private void initialize(org.enquery.encryptedquery.querier.data.entity.jpa.DataSource jpa, Resource result) throws UnsupportedEncodingException, URISyntaxException {

		final String dataSchemaId = jpa.getDataSchema().getId().toString();
		final String dataSourceId = jpa.getId().toString();
		result.setId(dataSourceId);
		result.setSelfUri(registry.dataSourceUri(dataSchemaId, dataSourceId));
		result.setType(DataSource.TYPE);
	}

	public org.enquery.encryptedquery.querier.data.entity.jpa.DataSource toJPA(
			org.enquery.encryptedquery.xml.schema.DataSourceResource resource) {

		Validate.notNull(resource);
		Validate.notNull(resource.getExecutionsUri());
		Validate.notNull(resource.getDataSchemaUri());
		Validate.notNull(resource.getDataSource());

		org.enquery.encryptedquery.querier.data.entity.jpa.DataSource result = toJPA(resource.getDataSource());
		result.setResponderUri(resource.getSelfUri());
		result.setExecutionsUri(resource.getExecutionsUri());

		return result;
	}


	public org.enquery.encryptedquery.querier.data.entity.jpa.DataSource toJPA(
			org.enquery.encryptedquery.xml.schema.DataSource source) {

		Validate.notNull(source);

		final org.enquery.encryptedquery.querier.data.entity.jpa.DataSource result =
				new org.enquery.encryptedquery.querier.data.entity.jpa.DataSource();
		result.setName(source.getName());
		result.setDescription(source.getDescription());
		result.setType(DataSourceType.valueOf(source.getType()));


		final DataSchema dataSchema = dataSchemaRepo.findByName(source.getDataSchemaName());
		Validate.notNull(dataSchema, "No Data Schema found with name: " + source.getDataSchemaName());

		result.setDataSchema(dataSchema);

		return result;
	}


	public DataSource toJSON(org.enquery.encryptedquery.querier.data.entity.jpa.DataSource jpa) {
		try {
			DataSource result = new DataSource();
			initialize(jpa, result);

			result.setName(jpa.getName());
			result.setDescription(jpa.getDescription());
			result.setDataSchema(dataSchemaConverter.toResourceIdentifier(jpa.getDataSchema()));
			result.setProcessingMode(jpa.getType());

			return result;
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error converting DataSource from JPA to JSON format.", e);
		}
	}

	public Set<Resource> referencedObjects(org.enquery.encryptedquery.querier.data.entity.jpa.DataSource data) {
		return Sets.unionOf(
				dataSchemaConverter.referencedObjects(data.getDataSchema()),
				dataSchemaConverter.toJSON(data.getDataSchema()));
	}

	public DataSourceResponse toJSONResponse(org.enquery.encryptedquery.querier.data.entity.jpa.DataSource jpa) throws UnsupportedEncodingException, URISyntaxException {
		Validate.notNull(jpa);
		Validate.notNull(jpa.getDataSchema());

		DataSourceResponse result = new DataSourceResponse(toJSON(jpa));
		result.setIncluded(JSONConverter.objectsToCollectionOfMaps(referencedObjects(jpa)));
		return result;
	}

	public DataSourceCollectionResponse toJSONResponse(Collection<org.enquery.encryptedquery.querier.data.entity.jpa.DataSource> data) {
		List<DataSource> list = new ArrayList<>(data.size());
		data.forEach(jpa -> {
			list.add(toJSON(jpa));
		});
		DataSourceCollectionResponse result = new DataSourceCollectionResponse(list);
		return result;
	}

}
