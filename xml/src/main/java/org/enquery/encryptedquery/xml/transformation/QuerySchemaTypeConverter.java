package org.enquery.encryptedquery.xml.transformation;

import org.apache.camel.Converter;
import org.enquery.encryptedquery.data.QuerySchemaElement;

@Converter
public class QuerySchemaTypeConverter {

	@Converter
	public static org.enquery.encryptedquery.xml.schema.QuerySchema toXMLQuerySchema(org.enquery.encryptedquery.data.QuerySchema querySchema) {
		org.enquery.encryptedquery.xml.schema.QuerySchema result = new org.enquery.encryptedquery.xml.schema.QuerySchema();
		result.setDataSchema(DataSchemaTypeConverter.toXMLDataSchema(querySchema.getDataSchema()));
		result.setName(querySchema.getName());
		result.setSelectorField(querySchema.getSelectorField());

		querySchema.getElementList().stream().forEach(el -> {
			org.enquery.encryptedquery.xml.schema.QuerySchema.Field field = new org.enquery.encryptedquery.xml.schema.QuerySchema.Field();

			field.setName(el.getName());
			// field.setLengthType(el.getLengthType());
			field.setMaxArrayElements(el.getMaxArrayElements());
			field.setSize(el.getSize());
			result.getField().add(field);
		});
		return result;
	}

	public static org.enquery.encryptedquery.data.QuerySchema fromXMLQuerySchema(org.enquery.encryptedquery.xml.schema.QuerySchema xmlQuerySchema) {
		org.enquery.encryptedquery.data.QuerySchema result = new org.enquery.encryptedquery.data.QuerySchema();
		result.setName(xmlQuerySchema.getName());
		result.setSelectorField(xmlQuerySchema.getSelectorField());
		xmlQuerySchema
				.getField()
				.stream()
				.forEach(f -> {
					QuerySchemaElement e = new QuerySchemaElement();
					// e.setLengthType(f.getLengthType());
					e.setMaxArrayElements(f.getMaxArrayElements());
					e.setName(f.getName());
					e.setSize(f.getSize());
					result.getElementList().add(e);
				});

		result.setDataSchema(DataSchemaTypeConverter.fromXMLDataSchema(xmlQuerySchema.getDataSchema()));
		return result;
	}

}
