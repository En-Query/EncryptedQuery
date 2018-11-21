package org.enquery.encryptedquery.xml.transformation;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.camel.Converter;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Response;
import org.enquery.encryptedquery.xml.schema.ResultSet;
import org.enquery.encryptedquery.xml.schema.ResultSet.Result;
import org.xml.sax.SAXException;

@Converter
public class ResponseTypeConverter {

	private static final String XSD_PATH = "/org/enquery/encryptedquery/xml/schema/response.xsd";

	private QueryTypeConverter qtc;
	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	public ResponseTypeConverter(QueryTypeConverter qtc) {
		this.qtc = qtc;
		initSchemas();
	}

	public ResponseTypeConverter() {
		qtc = new QueryTypeConverter();
		initSchemas();
	}

	private void initSchemas() {
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

	public void marshal(org.enquery.encryptedquery.xml.schema.Response r, OutputStream os) throws JAXBException {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.marshal(objectFactory.createResponse(r), os);
	}

	public org.enquery.encryptedquery.xml.schema.Response unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.Response> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.Response.class);
		return element.getValue();
	}

	@Converter
	public org.enquery.encryptedquery.xml.schema.Response toXML(org.enquery.encryptedquery.response.wideskies.Response response) {
		Validate.notNull(response);

		final Response result = new org.enquery.encryptedquery.xml.schema.Response();
		result.setQueryInfo(qtc.toXMLQueryInfo(response.getQueryInfo()));

		response.getResponseElements()
				.stream()
				.forEach(resultSet -> {
					ResultSet rs = new ResultSet();
					resultSet.forEach((col, val) -> {
						Result entry = new Result();
						entry.setColumn(col);
						entry.setValue(val);
						rs.getResult().add(entry);
					});
					result.getResultSet().add(rs);
				});

		return result;
	}

	@Converter
	public org.enquery.encryptedquery.response.wideskies.Response toCore(org.enquery.encryptedquery.xml.schema.Response response) {
		Validate.notNull(response);

		final QueryInfo queryInfo = qtc.fromXMLQueryInfo(response.getQueryInfo());

		final org.enquery.encryptedquery.response.wideskies.Response result = new org.enquery.encryptedquery.response.wideskies.Response(
				queryInfo);

		response.getResultSet()
				.stream()
				.forEach(resultSet -> {
					TreeMap<Integer, BigInteger> map = new TreeMap<>();

					resultSet.getResult()
							.stream()
							.forEach(r -> map.put(r.getColumn(), r.getValue()));

					result.addResponseElements(map);
				});

		return result;
	}

}
