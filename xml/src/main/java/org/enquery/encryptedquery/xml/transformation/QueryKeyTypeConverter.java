package org.enquery.encryptedquery.xml.transformation;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.enquery.encryptedquery.data.QueryKey;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.xml.schema.Keys;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.QueryKey.EmbedSelectorMap;
import org.enquery.encryptedquery.xml.schema.QueryKey.EmbedSelectorMap.Entry;
import org.enquery.encryptedquery.xml.schema.QueryKey.Selectors;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.xml.sax.SAXException;

@Component(service = QueryKeyTypeConverter.class)
public class QueryKeyTypeConverter {

	private static final String XSD_PATH = "/org/enquery/encryptedquery/xml/schema/queryKey.xsd";
	// needs to match version attribute in the XSD resource (most current version)
	private static final BigDecimal CURRENT_XSD_VERSION = new BigDecimal("2.0");

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	@Reference
	private CryptoSchemeRegistry keyConverter;

	public QueryKeyTypeConverter() {
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


	public void marshal(org.enquery.encryptedquery.xml.schema.QueryKey qk, OutputStream os) throws JAXBException {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.marshal(objectFactory.createQueryKey(qk), os);
	}

	public org.enquery.encryptedquery.xml.schema.QueryKey unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.QueryKey> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.QueryKey.class);
		return element.getValue();
	}

	public QueryKey toCore(org.enquery.encryptedquery.xml.schema.QueryKey queryKey) {

		List<String> selectors = queryKey.getSelectors().getEntry()
				.stream()
				.map(e -> e.getValue())
				.collect(Collectors.toList());

		Map<Integer, String> embedSelectorMap = fromXMLEmbededSelectorMap(queryKey.getEmbedSelectorMap());
		String queryId = queryKey.getQueryId();

		KeyPair keyPair = deserializeKeyPair(queryKey.getKeys());
		String cryptoId = queryKey.getKeys().getCryptoSchemeId();

		return new QueryKey(selectors, keyPair, embedSelectorMap, queryId, cryptoId);
	}

	/**
	 * @param keys
	 * @return
	 */
	public KeyPair deserializeKeyPair(Keys keys) {
		Validate.notNull(keys);
		final String schemeId = keys.getCryptoSchemeId();
		Validate.notBlank(schemeId);

		final CryptoScheme scheme = keyConverter.cryptoSchemeByName(schemeId);
		final PrivateKey privateKey = scheme.privateKeyFromBytes(keys.getPrivate());
		final PublicKey publicKey = scheme.publicKeyFromBytes(keys.getPublic());
		return new KeyPair(publicKey, privateKey);
	}

	public org.enquery.encryptedquery.xml.schema.QueryKey toXMLQueryKey(QueryKey qk) {
		org.enquery.encryptedquery.xml.schema.QueryKey result = new org.enquery.encryptedquery.xml.schema.QueryKey();

		result.setQueryId(qk.getQueryId());
		result.setEmbedSelectorMap(toXMLEmbededSelectorMap(qk.getEmbedSelectorMap()));
		result.setSelectors(toXMLSelectors(qk.getSelectors()));
		result.setKeys(toXMLKeys(qk.getCryptoSchemeId(), qk.getKeyPair()));
		result.setSchemaVersion(CURRENT_XSD_VERSION);

		return result;
	}

	private Keys toXMLKeys(String cryptoSchemeId, KeyPair keyPair) {
		Keys result = new Keys();
		result.setCryptoSchemeId(cryptoSchemeId);
		result.setPrivate(keyPair.getPrivate().getEncoded());
		result.setPublic(keyPair.getPublic().getEncoded());
		return result;
	}

	public KeyPair fromXMLPaillier(Keys xmlKeys) {
		return null;
	}

	private EmbedSelectorMap toXMLEmbededSelectorMap(Map<Integer, String> embedSelectorMap) {
		EmbedSelectorMap result = new EmbedSelectorMap();
		embedSelectorMap.forEach((key, value) -> {
			Entry entry = new Entry();
			entry.setKey(key);
			entry.setValue(value);
			result.getEntry().add(entry);
		});
		return result;
	}

	private Map<Integer, String> fromXMLEmbededSelectorMap(EmbedSelectorMap embedSelectorMap) {
		Map<Integer, String> result = new HashMap<>();
		embedSelectorMap.getEntry()
				.stream()
				.forEach(e -> {
					result.put(e.getKey(), e.getValue());
				});
		return result;
	}

	private Selectors toXMLSelectors(List<String> selectors) {
		Selectors result = new Selectors();
		for (String selector : selectors) {
			org.enquery.encryptedquery.xml.schema.QueryKey.Selectors.Entry entry = new org.enquery.encryptedquery.xml.schema.QueryKey.Selectors.Entry();
			entry.setValue(selector);
			result.getEntry().add(entry);
		}
		return result;
	}
}
