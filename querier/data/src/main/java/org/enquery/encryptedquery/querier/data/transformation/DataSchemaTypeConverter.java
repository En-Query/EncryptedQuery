package org.enquery.encryptedquery.querier.data.transformation;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchemaField;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = DataSchemaTypeConverter.class)
public class DataSchemaTypeConverter {

	private final Logger log = LoggerFactory.getLogger(DataSchemaTypeConverter.class);

	@Reference(target = "(type=rest-service)")
	private ResourceUriRegistry registry;

	public Resource toResourceIdentifier(org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema jpa) {
		Validate.notNull(jpa);
		Resource result = new Resource();
		initialize(jpa, result);
		return result;
	}

	private void initialize(org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema jpa, Resource result) {
		result.setId(jpa.getId().toString());
		result.setSelfUri(registry.dataSchemaUri(jpa));
		result.setType(DataSchema.TYPE);
	}

	public org.enquery.encryptedquery.data.DataSchema toCoreDataSchema(org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema dataSchema) {
		org.enquery.encryptedquery.data.DataSchema result = new org.enquery.encryptedquery.data.DataSchema();
		result.setName(dataSchema.getName());

		for (DataSchemaField field : dataSchema.getFields()) {
			result.addElement(toCoreDataSchemaElement(field));
		}

		return result;
	}

	private DataSchemaElement toCoreDataSchemaElement(DataSchemaField field) {
		DataSchemaElement result = new DataSchemaElement();
		result.setDataType(field.getDataType());
		// result.setIsArray(field.getIsArray());
		result.setName(field.getFieldName());
		result.setPosition(field.getPosition());
		return result;
	}


	public org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema toJPA(org.enquery.encryptedquery.xml.schema.DataSchema xmlDSchema) {
		Validate.notNull(xmlDSchema);
		Validate.notNull(xmlDSchema.getField());

		Set<String> uniqueNames = new HashSet<>();

		org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema result = new org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema();
		result.setName(xmlDSchema.getName());

		List<DataSchemaField> fields = new ArrayList<>();
		for (org.enquery.encryptedquery.xml.schema.DataSchema.Field element : xmlDSchema.getField()) {
			if (!uniqueNames.add(element.getName())) throw new IllegalArgumentException("Duplicate field names in Data Schema.");

			DataSchemaField dsf = new DataSchemaField();
			dsf.setDataSchema(result);
			dsf.setDataType(FieldType.fromExternalName(element.getDataType()));
			dsf.setFieldName(element.getName());
			// dsf.setIsArray(element.isIsArray() == null ? false : element.isIsArray());
			dsf.setPosition(element.getPosition());
			fields.add(dsf);
		}
		result.setFields(fields);

		if (log.isDebugEnabled()) {
			log.debug("Converted {} to {}", xmlDSchema, result.toString());
		}
		return result;
	}


	public org.enquery.encryptedquery.xml.schema.DataSchema toXMLDataSchema(org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema ds) {
		org.enquery.encryptedquery.xml.schema.DataSchema result = new org.enquery.encryptedquery.xml.schema.DataSchema();
		result.setName(ds.getName());
		for (DataSchemaField f : ds.getFields()) {
			org.enquery.encryptedquery.xml.schema.DataSchema.Field e = new org.enquery.encryptedquery.xml.schema.DataSchema.Field();
			// e.setIsArray(f.getIsArray());
			e.setName(f.getFieldName());
			e.setDataType(f.getDataType().toString());
			e.setPosition(f.getPosition());
			result.getField().add(e);
		}

		if (log.isDebugEnabled()) {
			log.debug("Converted {} to {}", ds, result.toString());
		}
		return result;
	}



	public Collection<org.enquery.encryptedquery.xml.schema.DataSchema.Field> toXMLDataSchemaElements(Collection<DataSchemaField> fields) {
		List<org.enquery.encryptedquery.xml.schema.DataSchema.Field> result = new ArrayList<>();
		for (DataSchemaField field : fields) {
			org.enquery.encryptedquery.xml.schema.DataSchema.Field xmlField = new org.enquery.encryptedquery.xml.schema.DataSchema.Field();
			xmlField.setName(field.getFieldName());
			xmlField.setDataType(field.getDataType().toString());
			// xmlField.setIsArray(field.getIsArray());
			xmlField.setPosition(field.getPosition());
			result.add(xmlField);
		}
		return result;
	}


	public org.enquery.encryptedquery.querier.data.entity.json.DataSchema toJSON(org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema jpa) {
		try {
			final String dataSchemaId = jpa.getId().toString();
			org.enquery.encryptedquery.querier.data.entity.json.DataSchema result = new org.enquery.encryptedquery.querier.data.entity.json.DataSchema();
			initialize(jpa, result);
			result.setName(jpa.getName());
			result.setQuerySchemasUri(registry.querySchemasUri(dataSchemaId));
			result.setDataSourcesUri(registry.dataSourceUri(dataSchemaId));

			List<org.enquery.encryptedquery.querier.data.entity.json.DataSchemaField> fields = new ArrayList<>();
			for (DataSchemaField field : jpa.getFields()) {
				org.enquery.encryptedquery.querier.data.entity.json.DataSchemaField dse = new org.enquery.encryptedquery.querier.data.entity.json.DataSchemaField();
				dse.setName(field.getFieldName());
				dse.setDataType(field.getDataType().toString());
				// dse.setIsArray(field.getIsArray());
				dse.setPosition(field.getPosition());
				fields.add(dse);
			}
			result.setFields(fields);
			return result;
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error converting DataSchema from JPA to JSON format.", e);
		}
	}

	public DataSchemaCollectionResponse toJSONCollectionResponse(Collection<org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema> data) {
		List<DataSchema> result = new ArrayList<>(data.size());
		data.forEach(jpa -> {
			result.add(toJSON(jpa));
		});
		return new DataSchemaCollectionResponse(result);
	}

	public DataSchemaResponse toJSONResponse(org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema data) {
		DataSchemaResponse result = new DataSchemaResponse();
		result.setData(toJSON(data));
		result.setIncluded(JSONConverter.objectsToCollectionOfMaps(referencedObjects(data)));
		return result;
	}

	public Set<Resource> referencedObjects(org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema data) {
		// this is the root object, no parents or siblings
		return null;
	}
}
