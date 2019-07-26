package org.enquery.encryptedquery.xml.transformation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.camel.Converter;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

@Converter
public class DataSchemaTypeConverter {

	private static final Logger log = LoggerFactory.getLogger(DataSchemaTypeConverter.class);

	private static final String XSD_PATH = "/org/enquery/encryptedquery/xml/schema/data-schema-resources.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	public DataSchemaTypeConverter() {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

		URL resource = getClass().getResource(XSD_PATH);
		Validate.notNull(resource);
		try {
			xmlSchema = factory.newSchema(resource);
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (SAXException | JAXBException e) {
			throw new RuntimeException("Error initializing XSD schema.", e);
		}
		objectFactory = new ObjectFactory();
	}


	public void marshal(org.enquery.encryptedquery.xml.schema.DataSchemaResources dataSchemas, OutputStream os) throws JAXBException, UnsupportedEncodingException, IOException, XMLStreamException, FactoryConfigurationError {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.setSchema(xmlSchema);
		marshaller.marshal(objectFactory.createDataSchemaResources(dataSchemas), os);
	}


	public org.enquery.encryptedquery.xml.schema.DataSchemaResources unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);

		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.DataSchemaResources> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.DataSchemaResources.class);

		return element.getValue();
	}

	public org.enquery.encryptedquery.xml.schema.DataSchema unmarshalDataSchema(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);

		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.DataSchema> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.DataSchema.class);

		return element.getValue();
	}

	@Converter
	public static org.enquery.encryptedquery.xml.schema.DataSchema toXMLDataSchema(org.enquery.encryptedquery.data.DataSchema dataSchema) {
		org.enquery.encryptedquery.xml.schema.DataSchema result = new org.enquery.encryptedquery.xml.schema.DataSchema();
		result.setName(dataSchema.getName());

		dataSchema.elements().stream().forEach(el -> {
			org.enquery.encryptedquery.xml.schema.DataSchema.Field field = new org.enquery.encryptedquery.xml.schema.DataSchema.Field();

			field.setName(el.getName());
			// field.setIsArray(el.getIsArray());
			field.setDataType(el.getDataType().getExternalName());
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
					element.setDataType(FieldType.fromExternalName(f.getDataType()));
					// element.setIsArray(f.isIsArray());
					result.addElement(element);
				});
		return result;
	}
}
