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
import org.enquery.encryptedquery.xml.schema.DataSourceExport;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.osgi.service.component.annotations.Component;
import org.xml.sax.SAXException;

@Component(service = DataSourceExportConverter.class)
public class DataSourceExportConverter {

	private static final String XSD_PATH = "/org/enquery/encryptedquery/xml/schema/data-source-export.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	public DataSourceExportConverter() {
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


	public void marshal(DataSourceExport dataSchemas, OutputStream os) throws JAXBException, UnsupportedEncodingException, IOException, XMLStreamException, FactoryConfigurationError {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.setSchema(xmlSchema);
		marshaller.marshal(objectFactory.createDataSourceExport(dataSchemas), os);
	}


	public DataSourceExport unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);

		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.DataSourceExport> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.DataSourceExport.class);

		return element.getValue();
	}

}
