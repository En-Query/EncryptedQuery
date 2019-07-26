package org.enquery.encryptedquery.xml.transformation;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
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
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Response;
import org.enquery.encryptedquery.xml.schema.ResultSet;
import org.enquery.encryptedquery.xml.schema.ResultSet.Result;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.xml.sax.SAXException;

@Component(service = ResponseTypeConverter.class)
public class ResponseTypeConverter {

	private static final String XSD_PATH = "/org/enquery/encryptedquery/xml/schema/response.xsd";
	// needs to match version attribute in the XSD resource (most current version)
	private static final BigDecimal CURRENT_XSD_VERSION = new BigDecimal("2.0");

	@Reference
	private CryptoSchemeRegistry schemeRegistry;
	@Reference
	private QueryTypeConverter queryConverter;

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	@Activate
	public void initialize() {
		initializeSchemas();
		Validate.notNull(schemeRegistry);
		Validate.notNull(queryConverter);
	}

	public CryptoSchemeRegistry getSchemeRegistry() {
		return schemeRegistry;
	}

	public void setSchemeRegistry(CryptoSchemeRegistry schemeRegistry) {
		this.schemeRegistry = schemeRegistry;
	}


	public QueryTypeConverter getQueryConverter() {
		return queryConverter;
	}

	public void setQueryConverter(QueryTypeConverter queryConverter) {
		this.queryConverter = queryConverter;
	}

	private void initializeSchemas() {
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
		r.setSchemaVersion(Versions.RESPONSE_BI);
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
	public org.enquery.encryptedquery.xml.schema.Response toXML(org.enquery.encryptedquery.data.Response response) {
		Validate.notNull(response);

		final Response result = new org.enquery.encryptedquery.xml.schema.Response();
		result.setSchemaVersion(CURRENT_XSD_VERSION);
		result.setQueryInfo(queryConverter.toXMLQueryInfo(response.getQueryInfo()));

		response.getResponseElements()
				.stream()
				.forEach(resultSet -> {
					ResultSet rs = new ResultSet();
					resultSet.forEach((col, val) -> {
						Result entry = new Result();
						entry.setColumn(col);
						entry.setValue(val.toBytes());
						rs.getResult().add(entry);
					});
					result.getResultSet().add(rs);
				});

		return result;
	}

	public org.enquery.encryptedquery.data.Response toCore(org.enquery.encryptedquery.xml.schema.Response response) {
		Validate.notNull(response);

		final QueryInfo queryInfo = queryConverter.fromXMLQueryInfo(response.getQueryInfo());

		final org.enquery.encryptedquery.data.Response result = //
				new org.enquery.encryptedquery.data.Response(queryInfo);

		final CryptoScheme scheme = schemeRegistry.cryptoSchemeByName(queryInfo.getCryptoSchemeId());

		response.getResultSet()
				.stream()
				.forEach(resultSet -> {
					TreeMap<Integer, CipherText> map = new TreeMap<>();
					resultSet.getResult()
							.stream()
							.forEach(r -> map.put(r.getColumn(), scheme.cipherTextFromBytes(r.getValue())));

					result.addResponseElements(map);
				});

		return result;
	}

}
