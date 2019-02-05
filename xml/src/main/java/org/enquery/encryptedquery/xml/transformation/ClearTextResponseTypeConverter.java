package org.enquery.encryptedquery.xml.transformation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Record;
import org.enquery.encryptedquery.xml.schema.ClearTextResponse;
import org.enquery.encryptedquery.xml.schema.Field;
import org.enquery.encryptedquery.xml.schema.Hit;
import org.enquery.encryptedquery.xml.schema.Hits;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Selector;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

@Component(service = ClearTextResponseTypeConverter.class)
public class ClearTextResponseTypeConverter {

	private static final Logger log = LoggerFactory.getLogger(ClearTextResponseTypeConverter.class);

	private static final String XSD_PATH = "/org/enquery/encryptedquery/xml/schema/cleartext-response.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	public ClearTextResponseTypeConverter() {
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


	public void marshal(org.enquery.encryptedquery.xml.schema.ClearTextResponse response, OutputStream os) throws JAXBException, UnsupportedEncodingException, IOException, XMLStreamException, FactoryConfigurationError {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.setSchema(xmlSchema);
		marshaller.marshal(objectFactory.createClearTextResponse(response), os);
	}


	public org.enquery.encryptedquery.xml.schema.ClearTextResponse unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);

		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.ClearTextResponse> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.ClearTextResponse.class);

		return element.getValue();
	}

	public InputStream toXMLStream(ClearTextQueryResponse response) throws JAXBException, IOException, XMLStreamException, FactoryConfigurationError {
		try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
			marshal(toXML(response), os);
			return new ByteArrayInputStream(os.toByteArray());
		}
	}

	public org.enquery.encryptedquery.xml.schema.ClearTextResponse toXML(ClearTextQueryResponse response) {
		Validate.notNull(response);

		log.info("Start converstion from ClearTextQueryResponse to xml.");

		final ClearTextResponse result = new ClearTextResponse();
		result.setQueryId(response.getQueryId());
		result.setQueryName(response.getQueryName());

		response.forEach(jSelector -> addSelector(result, jSelector));


		log.info("Finished converstion from ClearTextQueryResponse");
		return result;
	}

	private void addSelector(final ClearTextResponse result, org.enquery.encryptedquery.data.ClearTextQueryResponse.Selector jSelector) {
		Selector selector = makeSelector(jSelector);
		if (selector != null) {
			result.getSelector().add(selector);
		}
	}

	private Selector makeSelector(org.enquery.encryptedquery.data.ClearTextQueryResponse.Selector jSelector) {
		if (jSelector.hitCount() < 0) return null;
		Selector xSelector = new Selector();
		xSelector.setSelectorName(jSelector.getName());
		addHits(jSelector, xSelector);
		if (xSelector.getHits().size() < 0) return null;
		return xSelector;
	}

	private void addHits(org.enquery.encryptedquery.data.ClearTextQueryResponse.Selector jSelector, Selector xSelector) {
		jSelector.forEachHits(jHits -> {
			Hits hits = makeHits(jHits);
			if (hits != null) xSelector.getHits().add(hits);
		});
	}

	private Hits makeHits(org.enquery.encryptedquery.data.ClearTextQueryResponse.Hits jHits) {
		if (jHits.recordCount() < 0) return null;
		Hits xHits = new Hits();
		xHits.setSelectorValue(jHits.getSelectorValue());
		addRecords(jHits, xHits);
		if (xHits.getHit().size() < 0) return null;
		return xHits;
	}

	private void addRecords(org.enquery.encryptedquery.data.ClearTextQueryResponse.Hits jHits, Hits xHits) {
		jHits.forEachRecord(rec -> {
			Hit hit = makeHit(rec);
			if (hit != null) xHits.getHit().add(hit);
		});
	}

	private Hit makeHit(Record rec) {
		if (rec.fieldCount() < 0) return null;
		Hit xHit = new Hit();
		rec.forEachField(f -> {
			Field field = new Field();
			field.setName(f.getName());
			field.setValue(f.getValue().toString());
			xHit.getField().add(field);
		});
		if (xHit.getField().size() < 0) return null;
		return xHit;
	}
}
