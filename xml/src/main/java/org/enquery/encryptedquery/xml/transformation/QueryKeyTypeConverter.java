package org.enquery.encryptedquery.xml.transformation;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

import org.apache.camel.Converter;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.wideskies.encrypt.QueryKey;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Paillier;
import org.enquery.encryptedquery.xml.schema.QueryKey.EmbedSelectorMap;
import org.enquery.encryptedquery.xml.schema.QueryKey.EmbedSelectorMap.Entry;
import org.enquery.encryptedquery.xml.schema.QueryKey.Selectors;
import org.xml.sax.SAXException;

@Converter
public class QueryKeyTypeConverter {

	private static final String XSD_PATH = "/org/enquery/encryptedquery/xml/schema/queryKey.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

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

	@Converter
	public static QueryKey toCore(org.enquery.encryptedquery.xml.schema.QueryKey queryKey) {

		List<String> selectors = queryKey.getSelectors().getEntry()
				.stream()
				.map(e -> e.getValue())
				.collect(Collectors.toList());

		org.enquery.encryptedquery.encryption.Paillier paillier = fromXMLPaillier(queryKey.getPaillier());
		Map<Integer, String> embedSelectorMap = fromXMLEmbededSelectorMap(queryKey.getEmbedSelectorMap());
		UUID id = UUID.fromString(queryKey.getIdentifier());
		return new QueryKey(selectors, paillier, embedSelectorMap, id);
	}

	@Converter
	public static org.enquery.encryptedquery.xml.schema.QueryKey toXMLQueryKey(QueryKey qk) {
		org.enquery.encryptedquery.xml.schema.QueryKey result = new org.enquery.encryptedquery.xml.schema.QueryKey();

		result.setIdentifier(qk.getIdentifier().toString());
		result.setEmbedSelectorMap(toXMLEmbededSelectorMap(qk.getEmbedSelectorMap()));
		result.setSelectors(toXMLSelectors(qk.getSelectors()));
		result.setPaillier(toXMLPaillier(qk.getPaillier()));

		return result;
	}

	private static Paillier toXMLPaillier(org.enquery.encryptedquery.encryption.Paillier paillier) {
		Paillier result = new Paillier();
		result.setBitLength(paillier.getBitLength());
		result.setN(paillier.getN());
		result.setNSquared(paillier.getNSquared());
		result.setP(paillier.getP());
		result.setPBasePoint(paillier.getPBasePoint());
		result.setPMaxExponent(paillier.getPMaxExponent());
		result.setPMinusOne(paillier.getpMinusOne());
		result.setPSquared(paillier.getPSquared());
		result.setQ(paillier.getQ());
		result.setQBasePoint(paillier.getQBasePoint());
		result.setQMaxExponent(paillier.getQMaxExponent());
		result.setQMinusOne(paillier.getQMinusOne());
		result.setQSquared(paillier.getQSquared());
		result.setWp(paillier.getWp());
		result.setWq(paillier.getWq());
		return result;
	}

	public static org.enquery.encryptedquery.encryption.Paillier fromXMLPaillier(Paillier xmlPaillier) {
		org.enquery.encryptedquery.encryption.Paillier result = new org.enquery.encryptedquery.encryption.Paillier();
		result.setBitLength(xmlPaillier.getBitLength());
		result.setN(xmlPaillier.getN());
		result.setNSquared(xmlPaillier.getNSquared());
		result.setP(xmlPaillier.getP());
		result.setPBasePoint(xmlPaillier.getPBasePoint());
		result.setPMaxExponent(xmlPaillier.getPMaxExponent());
		result.setPMinusOne(xmlPaillier.getPMinusOne());
		result.setPSquared(xmlPaillier.getPSquared());
		result.setQ(xmlPaillier.getQ());
		result.setQBasePoint(xmlPaillier.getQBasePoint());
		result.setQMaxExponent(xmlPaillier.getQMaxExponent());
		result.setQMinusOne(xmlPaillier.getQMinusOne());
		result.setQSquared(xmlPaillier.getQSquared());
		result.setWp(xmlPaillier.getWp());
		result.setWq(xmlPaillier.getWq());
		return result;
	}

	private static EmbedSelectorMap toXMLEmbededSelectorMap(Map<Integer, String> embedSelectorMap) {
		EmbedSelectorMap result = new EmbedSelectorMap();
		embedSelectorMap.forEach((key, value) -> {
			Entry entry = new Entry();
			entry.setKey(key);
			entry.setValue(value);
			result.getEntry().add(entry);
		});
		return result;
	}

	private static Map<Integer, String> fromXMLEmbededSelectorMap(EmbedSelectorMap embedSelectorMap) {
		Map<Integer, String> result = new HashMap<>();
		embedSelectorMap.getEntry()
				.stream()
				.forEach(e -> {
					result.put(e.getKey(), e.getValue());
				});
		return result;
	}

	private static Selectors toXMLSelectors(List<String> selectors) {
		Selectors result = new Selectors();
		for (String selector : selectors) {
			org.enquery.encryptedquery.xml.schema.QueryKey.Selectors.Entry entry = new org.enquery.encryptedquery.xml.schema.QueryKey.Selectors.Entry();
			entry.setValue(selector);
			result.getEntry().add(entry);
		}
		return result;
	}
}
