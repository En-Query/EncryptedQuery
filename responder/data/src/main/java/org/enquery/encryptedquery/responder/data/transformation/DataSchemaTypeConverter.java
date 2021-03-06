/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.responder.data.transformation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSchemaField;
import org.enquery.encryptedquery.responder.data.service.ResourceUriRegistry;
import org.enquery.encryptedquery.xml.schema.DataSchemaResource;
import org.enquery.encryptedquery.xml.schema.DataSchemaResources;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = DataSchemaTypeConverter.class)
public class DataSchemaTypeConverter extends org.enquery.encryptedquery.xml.transformation.DataSchemaTypeConverter {

	private static final Logger log = LoggerFactory.getLogger(DataSchemaTypeConverter.class);

	@Reference(target = "(type=rest)")
	private ResourceUriRegistry registry;

	public DataSchema toDataSchemaJPAEntity(org.enquery.encryptedquery.xml.schema.DataSchema xmlDSchema) {
		Validate.notNull(xmlDSchema);
		Validate.notNull(xmlDSchema.getField());

		Set<String> uniqueNames = new HashSet<>();

		DataSchema result = new DataSchema();
		result.setName(xmlDSchema.getName());
		for (org.enquery.encryptedquery.xml.schema.DataSchema.Field element : xmlDSchema.getField()) {
			if (!uniqueNames.add(element.getName())) throw new IllegalArgumentException("Duplicate field names in Data Schema.");

			DataSchemaField dsf = new DataSchemaField();
			dsf.setDataSchema(result);
			dsf.setDataType(FieldType.fromExternalName(element.getDataType()));
			dsf.setFieldName(element.getName());
			// dsf.setIsArray(element.isIsArray());
			dsf.setPosition(element.getPosition());
			result.getFields().add(dsf);
		}

		if (log.isDebugEnabled()) {
			log.debug("Converted {} to {}", xmlDSchema, result.toString());
		}
		return result;
	}

	public org.enquery.encryptedquery.xml.schema.DataSchema toXMLDataSchema(DataSchema ds) {
		org.enquery.encryptedquery.xml.schema.DataSchema result = new org.enquery.encryptedquery.xml.schema.DataSchema();
		result.setName(ds.getName());
		for (DataSchemaField f : ds.getFields()) {
			org.enquery.encryptedquery.xml.schema.DataSchema.Field e = new org.enquery.encryptedquery.xml.schema.DataSchema.Field();
			// e.setIsArray(f.getIsArray());
			e.setName(f.getFieldName());
			e.setDataType(f.getDataType().getExternalName());
			e.setPosition(f.getPosition());
			result.getField().add(e);
		}

		if (log.isDebugEnabled()) {
			log.debug("Converted {} to {}", ds, result.toString());
		}
		return result;
	}

	public DataSchemaResource jpaToResource(DataSchema dataSchema) {

		org.enquery.encryptedquery.xml.schema.DataSchema xmlSchema = toXMLDataSchema(dataSchema);

		DataSchemaResource resource = new DataSchemaResource();
		resource.setId(dataSchema.getId());
		resource.setDataSchema(xmlSchema);
		resource.setSelfUri(registry.dataSchemaUri(dataSchema.getId()));
		resource.setDataSourcesUri(registry.dataSourceUri(dataSchema.getId()));

		return resource;
	}

	public DataSchemaResources toXMLDataSchemas(Collection<DataSchema> list) {
		Validate.notNull(list);
		DataSchemaResources result = new DataSchemaResources();
		for (DataSchema dataSchema : list) {
			DataSchemaResource resource = jpaToResource(dataSchema);
			result.getDataSchemaResource().add(resource);
		}
		return result;
	}

	public Collection<org.enquery.encryptedquery.xml.schema.DataSchema.Field> toXMLDataSchemaElements(Collection<DataSchemaField> fields) {
		List<org.enquery.encryptedquery.xml.schema.DataSchema.Field> result = new ArrayList<>();
		for (DataSchemaField field : fields) {
			org.enquery.encryptedquery.xml.schema.DataSchema.Field xmlField = new org.enquery.encryptedquery.xml.schema.DataSchema.Field();
			xmlField.setName(field.getFieldName());
			xmlField.setDataType(field.getDataType().getExternalName());
			// xmlField.setIsArray(field.getIsArray());
			xmlField.setPosition(field.getPosition());
			result.add(xmlField);
		}
		return result;
	}
}
