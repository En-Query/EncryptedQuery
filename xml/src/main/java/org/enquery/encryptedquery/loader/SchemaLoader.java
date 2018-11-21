package org.enquery.encryptedquery.loader;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.QuerySchema;
import org.enquery.encryptedquery.xml.schema.QuerySchema.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class SchemaLoader {

	private static final String QUERY_SCHEMA_PATH = "/org/enquery/encryptedquery/xml/schema/query-schema.xsd";
	private static final String DATA_SCHEMA_PATH = "/org/enquery/encryptedquery/xml/schema/data-schema.xsd";

	private final Logger log = LoggerFactory.getLogger(SchemaLoader.class);
	private Schema querySchemaXMLSchema;
	private JAXBContext queryJaxbContext;
	private Schema dataSchemaXMLSchema;
	private JAXBContext dataSchemaJaxbContext;

	public SchemaLoader() throws SAXException, JAXBException {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

		URL resource = getClass().getResource(QUERY_SCHEMA_PATH);
		Validate.notNull(resource);
		querySchemaXMLSchema = factory.newSchema(resource);
		queryJaxbContext = JAXBContext.newInstance(ObjectFactory.class);

		resource = getClass().getResource(DATA_SCHEMA_PATH);
		Validate.notNull(resource);
		dataSchemaXMLSchema = factory.newSchema(resource);
		dataSchemaJaxbContext = JAXBContext.newInstance(ObjectFactory.class);
	}

	public org.enquery.encryptedquery.xml.schema.DataSchema loadDSchema(Path file) throws JAXBException, FileNotFoundException, IOException {
		Validate.notNull(file);
		Validate.isTrue(Files.isRegularFile(file));

		Unmarshaller jaxbUnmarshaller = dataSchemaJaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(dataSchemaXMLSchema);

		try (FileInputStream fis = new FileInputStream(file.toFile())) {
			StreamSource source = new StreamSource(fis);
			JAXBElement<org.enquery.encryptedquery.xml.schema.DataSchema> element = jaxbUnmarshaller.unmarshal(source,
					org.enquery.encryptedquery.xml.schema.DataSchema.class);
			return element.getValue();
		}
	}

	public void saveDSchema(org.enquery.encryptedquery.xml.schema.DataSchema xmlDSchema, Path file) throws JAXBException, IOException {
		Marshaller marshaller = dataSchemaJaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		ObjectFactory of = new ObjectFactory();
		try (FileWriter writer = new FileWriter(file.toFile());) {
			marshaller.marshal(
					of.createDataSchema(xmlDSchema),
					writer);
		}
	}

	public org.enquery.encryptedquery.xml.schema.QuerySchema loadQSchema(Path file) throws JAXBException, FileNotFoundException, IOException {
		Validate.notNull(file);
		Validate.isTrue(Files.isRegularFile(file));


		try (FileInputStream fis = new FileInputStream(file.toFile())) {
			return toXMLQuerySchema(fis);
		}
	}

	private org.enquery.encryptedquery.xml.schema.QuerySchema toXMLQuerySchema(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = queryJaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(querySchemaXMLSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<QuerySchema> element = jaxbUnmarshaller.unmarshal(source, QuerySchema.class);
		return element.getValue();
	}

	public DataSchema loadDataSchema(Path file) throws JAXBException, FileNotFoundException, IOException {
		log.info("Loading data schema from file: " + file);

		Validate.notNull(file);
		Validate.isTrue(Files.isRegularFile(file), "File not found: " + file);

		org.enquery.encryptedquery.xml.schema.DataSchema xmlDS = loadDSchema(file);
		return dataSchemaFromXML(xmlDS);
	}

	public DataSchema dataSchemaFromXML(org.enquery.encryptedquery.xml.schema.DataSchema xmlDS) {
		DataSchema result = new DataSchema();
		result.setName(xmlDS.getName());
		for (org.enquery.encryptedquery.xml.schema.DataSchema.Field field : xmlDS.getField()) {
			result.addElement(fromXML(field));
		}
		result.validate();
		return result;
	}

	private org.enquery.encryptedquery.data.DataSchemaElement fromXML(org.enquery.encryptedquery.xml.schema.DataSchema.Field field) {
		org.enquery.encryptedquery.data.DataSchemaElement result = new org.enquery.encryptedquery.data.DataSchemaElement();
		result.setName(field.getName());
		result.setDataType(field.getDataType());
		result.setIsArray(field.isIsArray());
		result.setPosition(field.getPosition());
		return result;
	}

	public org.enquery.encryptedquery.data.QuerySchema loadQuerySchema(Path file) throws JAXBException, FileNotFoundException, IOException {
		log.info("Loading query schema from file: " + file);

		Validate.notNull(file);
		Validate.isTrue(Files.isRegularFile(file), "File not found: " + file);

		final org.enquery.encryptedquery.xml.schema.QuerySchema xmlQS = loadQSchema(file);

		return convertQuerySchema(xmlQS);
	}

	private org.enquery.encryptedquery.data.QuerySchema convertQuerySchema(final org.enquery.encryptedquery.xml.schema.QuerySchema xmlQS) {
		final org.enquery.encryptedquery.data.QuerySchema result = new org.enquery.encryptedquery.data.QuerySchema();
		result.setName(xmlQS.getName());
		result.setSelectorField(xmlQS.getSelectorField());
		result.setDataSchema(dataSchemaFromXML(xmlQS.getDataSchema()));

		for (Field element : xmlQS.getField()) {
			result.addElement(fromXML(element));
		}

		result.validate();
		return result;
	}

	private org.enquery.encryptedquery.data.QuerySchemaElement fromXML(Field element) {
		org.enquery.encryptedquery.data.QuerySchemaElement result = new org.enquery.encryptedquery.data.QuerySchemaElement();
		result.setName(element.getName());

		if (element.getLengthType() != null) {
			result.setLengthType(element.getLengthType());
		}

		if (element.getSize() != null) {
			result.setSize(element.getSize());
		}

		if (element.getMaxArrayElements() != null) {
			result.setMaxArrayElements(element.getMaxArrayElements());
		}
		return result;
	}

	public org.enquery.encryptedquery.data.QuerySchema loadQuerySchema(byte[] bytes) throws JAXBException {
		Validate.notNull(bytes);

		final org.enquery.encryptedquery.xml.schema.QuerySchema xmlQS = loadXMLQuerySchema(bytes);

		return convertQuerySchema(xmlQS);
	}

	private QuerySchema loadXMLQuerySchema(byte[] bytes) throws JAXBException {
		return toXMLQuerySchema(new ByteArrayInputStream(bytes));
	}

}
