package org.enquery.encryptedquery.xml.transformation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Query.QueryElements;
import org.enquery.encryptedquery.xml.schema.Query.QueryElements.Entry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.xml.sax.SAXException;

@Component(service = QueryTypeConverter.class)
public class QueryTypeConverter {

	private static final String QUERY_XSD_PATH = "/org/enquery/encryptedquery/xml/schema/query.xsd";
	// needs to match version attribute in the XSD resource (most current version)
	private static final BigDecimal CURRENT_XSD_VERSION = new BigDecimal("2.0");

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	@Reference
	private CryptoSchemeRegistry cryptoRegistry;

	@Activate
	public void initialize() {
		initializeSchemas();
		Validate.notNull(cryptoRegistry);
	}

	private void initializeSchemas() {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

		URL resource = getClass().getResource(QUERY_XSD_PATH);
		Validate.notNull(resource);
		try {
			xmlSchema = factory.newSchema(resource);
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (SAXException | JAXBException e) {
			throw new RuntimeException("Error initializing Query XSD schema.", e);
		}
		objectFactory = new ObjectFactory();
	}

	public CryptoSchemeRegistry getCryptoRegistry() {
		return cryptoRegistry;
	}

	public void setCryptoRegistry(CryptoSchemeRegistry cryptoRegistry) {
		this.cryptoRegistry = cryptoRegistry;
	}

	public org.enquery.encryptedquery.xml.schema.Query queryXMLTextToXMLQuery(byte[] queryBytes) throws JAXBException {
		return queryXMLTextToXMLQuery(new ByteArrayInputStream(queryBytes));
	}

	public void marshal(org.enquery.encryptedquery.xml.schema.Query q, OutputStream os) throws JAXBException {
		q.setSchemaVersion(Versions.QUERY_BI);
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.marshal(objectFactory.createQuery(q), os);
	}

	public org.enquery.encryptedquery.xml.schema.Query unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.Query> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.Query.class);
		return element.getValue();
	}


	public org.enquery.encryptedquery.xml.schema.Query queryXMLTextToXMLQuery(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.Query> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.Query.class);
		return element.getValue();
	}


	public org.enquery.encryptedquery.xml.schema.Query toXMLQuery(//
			org.enquery.encryptedquery.data.Query q)//
	{
		Validate.notNull(q);
		org.enquery.encryptedquery.xml.schema.Query result = new org.enquery.encryptedquery.xml.schema.Query();

		result.setSchemaVersion(CURRENT_XSD_VERSION);
		result.setQueryInfo(toXMLQueryInfo(q.getQueryInfo()));
		result.setQueryElements(toXMLQueryElements(q.getQueryElements()));
		return result;
	}

	private QueryElements toXMLQueryElements(Map<Integer, CipherText> queryElements) {
		QueryElements result = new QueryElements();
		queryElements.forEach(
				(key, value) -> {
					Entry entry = new Entry();
					entry.setKey(key);
					entry.setValue(value.toBytes());
					result.getEntry().add(entry);
				});

		return result;
	}


	public org.enquery.encryptedquery.xml.schema.QueryInfo toXMLQueryInfo(QueryInfo queryInfo) {
		org.enquery.encryptedquery.xml.schema.QueryInfo result = //
				new org.enquery.encryptedquery.xml.schema.QueryInfo();

		result.setQueryId(queryInfo.getIdentifier());
		result.setQueryName(queryInfo.getQueryName());
		result.setPublicKey(queryInfo.getPublicKey().getEncoded());
		result.setCryptoSchemeId(queryInfo.getCryptoSchemeId());
		result.setDataChunkSize(queryInfo.getDataChunkSize());
		// result.setEmbedSelector(queryInfo.getEmbedSelector());
		result.setHashBitSize(queryInfo.getHashBitSize());
		result.setHashKey(queryInfo.getHashKey());
		result.setNumBitsPerDataElement(queryInfo.getNumBitsPerDataElement());
		result.setNumPartitionsPerDataElement(queryInfo.getNumPartitionsPerDataElement());
		result.setNumSelectors(queryInfo.getNumSelectors());
		result.setQuerySchema(QuerySchemaTypeConverter.toXMLQuerySchema(queryInfo.getQuerySchema()));
		return result;
	}


	public QueryInfo fromXMLQueryInfo(org.enquery.encryptedquery.xml.schema.QueryInfo queryInfo) {
		Validate.notNull(queryInfo);

		String schemeId = queryInfo.getCryptoSchemeId();
		final CryptoScheme scheme = cryptoRegistry.cryptoSchemeByName(schemeId);
		Validate.notNull(scheme, "CryptoScheme not found for name: " + schemeId);

		QueryInfo result = new QueryInfo();
		result.setIdentifier(queryInfo.getQueryId());
		result.setQueryName(queryInfo.getQueryName());
		result.setCryptoSchemeId(schemeId);
		result.setPublicKey(scheme.publicKeyFromBytes(queryInfo.getPublicKey()));
		result.setNumSelectors(queryInfo.getNumSelectors());
		result.setHashBitSize(queryInfo.getHashBitSize());
		result.setDataChunkSize(queryInfo.getDataChunkSize());
		// result.setEmbedSelector(queryInfo.isEmbedSelector());
		result.setHashKey(queryInfo.getHashKey());
		result.setNumBitsPerDataElement(queryInfo.getNumBitsPerDataElement());
		result.setNumPartitionsPerDataElement(queryInfo.getNumPartitionsPerDataElement());
		result.setNumSelectors(queryInfo.getNumSelectors());
		result.setQuerySchema(QuerySchemaTypeConverter.fromXMLQuerySchema(queryInfo.getQuerySchema()));
		return result;
	}

	public Query toCoreQuery(InputStream xmlQueryInputStream) throws JAXBException {
		return toCoreQuery(queryXMLTextToXMLQuery(xmlQueryInputStream));
	}


	public Query toCoreQuery(org.enquery.encryptedquery.xml.schema.Query xmlQuery) {

		final QueryInfo queryInfo = fromXMLQueryInfo(xmlQuery.getQueryInfo());
		final CryptoScheme scheme = cryptoRegistry.cryptoSchemeByName(queryInfo.getCryptoSchemeId());

		final Map<Integer, CipherText> elements = new ConcurrentHashMap<>();
		xmlQuery.getQueryElements()
				.getEntry()
				.parallelStream()
				.forEach(e -> elements.put(
						e.getKey(),
						scheme.cipherTextFromBytes(e.getValue())));


		return new Query(queryInfo, elements);
	}

}
