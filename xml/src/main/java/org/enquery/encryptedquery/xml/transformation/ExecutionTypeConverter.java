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

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.xml.sax.SAXException;

public class ExecutionTypeConverter {

	private static final String EX_RESOURCES_XSD_PATH = "/org/enquery/encryptedquery/xml/schema/execution-resources.xsd";

	private Schema executionResourcesXmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	public ExecutionTypeConverter() {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

		URL exResourceUrl = getClass().getResource(EX_RESOURCES_XSD_PATH);
		Validate.notNull(exResourceUrl);

		try {
			executionResourcesXmlSchema = factory.newSchema(exResourceUrl);
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (SAXException | JAXBException e) {
			throw new RuntimeException("Error initializing XSD schema.", e);
		}
		objectFactory = new ObjectFactory();
	}


	public void marshal(org.enquery.encryptedquery.xml.schema.ExecutionResources executions, OutputStream os) throws JAXBException, UnsupportedEncodingException, IOException, XMLStreamException, FactoryConfigurationError {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.setSchema(executionResourcesXmlSchema);
		marshaller.marshal(objectFactory.createExecutionResources(executions), os);
	}

	public void marshal(org.enquery.encryptedquery.xml.schema.ExecutionResource execution, OutputStream os) throws JAXBException, UnsupportedEncodingException, IOException, XMLStreamException, FactoryConfigurationError {
		execution.setSchemaVersion(Versions.EXECUTION_RESOURCE_BI);
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.setSchema(executionResourcesXmlSchema);
		marshaller.marshal(objectFactory.createExecutionResource(execution), os);
	}

	public org.enquery.encryptedquery.xml.schema.ExecutionResources unmarshalExecutionResources(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(executionResourcesXmlSchema);

		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.ExecutionResources> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.ExecutionResources.class);

		return element.getValue();
	}

	public org.enquery.encryptedquery.xml.schema.Execution unmarshalExecution(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(executionResourcesXmlSchema);

		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.Execution> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.Execution.class);

		return element.getValue();
	}

}
