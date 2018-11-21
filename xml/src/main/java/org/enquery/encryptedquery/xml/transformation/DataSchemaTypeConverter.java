package org.enquery.encryptedquery.xml.transformation;

import org.apache.camel.Converter;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public class DataSchemaTypeConverter {

	private static final Logger log = LoggerFactory.getLogger(DataSchemaTypeConverter.class);

	@Converter
	public static org.enquery.encryptedquery.xml.schema.DataSchema toXMLDataSchema(org.enquery.encryptedquery.data.DataSchema dataSchema) {
		org.enquery.encryptedquery.xml.schema.DataSchema result = new org.enquery.encryptedquery.xml.schema.DataSchema();
		result.setName(dataSchema.getName());

		dataSchema.elements().stream().forEach(el -> {
			org.enquery.encryptedquery.xml.schema.DataSchema.Field field = new org.enquery.encryptedquery.xml.schema.DataSchema.Field();

			field.setName(el.getName());
			field.setIsArray(el.getIsArray());
			field.setDataType(el.getDataType());
			field.setPosition(el.getPosition());
			result.getField().add(field);
		});

		if (log.isDebugEnabled()) {
			log.debug("Converted {} to {}", dataSchema, result.toString());
		}

		return result;
	}

	public static DataSchema fromXMLDataSchema(org.enquery.encryptedquery.xml.schema.DataSchema xmlDataSchema) {
		DataSchema result = new DataSchema();
		result.setName(xmlDataSchema.getName());
		xmlDataSchema.getField()
				.stream()
				.forEach(f -> {
					DataSchemaElement element = new DataSchemaElement();
					element.setName(f.getName());
					element.setPosition(f.getPosition());
					element.setDataType(f.getDataType());
					element.setIsArray(f.isIsArray());
					result.addElement(element);
				});
		return result;
	}
}
