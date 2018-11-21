package org.enquery.encryptedquery.xml.transformation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;

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
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.xml.schema.ExpTableValue;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Query.ExpFileBasedLookup;
import org.enquery.encryptedquery.xml.schema.Query.ExpTable;
import org.enquery.encryptedquery.xml.schema.Query.QueryElements;
import org.enquery.encryptedquery.xml.schema.Query.QueryElements.Entry;
import org.xml.sax.SAXException;

@Converter
public class QueryTypeConverter {

	private static final String QUERY_XSD_PATH = "/org/enquery/encryptedquery/xml/schema/query.xsd";
	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	public QueryTypeConverter() {
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

	@Converter
	public org.enquery.encryptedquery.xml.schema.Query queryXMLTextToXMLQuery(byte[] queryBytes) throws JAXBException {
		return queryXMLTextToXMLQuery(new ByteArrayInputStream(queryBytes));
	}

	public void marshal(org.enquery.encryptedquery.xml.schema.Query q, OutputStream os) throws JAXBException {
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

	@Converter
	public org.enquery.encryptedquery.xml.schema.Query queryXMLTextToXMLQuery(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.Query> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.Query.class);
		return element.getValue();
	}

	@Converter
	public org.enquery.encryptedquery.xml.schema.Query toXMLQuery(org.enquery.encryptedquery.query.wideskies.Query q) {
		org.enquery.encryptedquery.xml.schema.Query result = new org.enquery.encryptedquery.xml.schema.Query();
		result.setN(q.getN());
		result.setNsquared(q.getNSquared());
		result.setQueryInfo(toXMLQueryInfo(q.getQueryInfo()));
		result.setExpFileBasedLookup(toXMLExpFileBasedLookup(q.getExpFileBasedLookup()));
		result.setExpTable(toXMLExpTable(q.getExpTable()));
		result.setQueryElements(toXMLQueryElements(q.getQueryElements()));
		return result;
	}

	private static QueryElements toXMLQueryElements(SortedMap<Integer, BigInteger> queryElements) {
		QueryElements result = new QueryElements();

		queryElements.forEach(
				(key, value) -> {
					Entry entry = new Entry();
					entry.setKey(key);
					entry.setValue(value);
					result.getEntry().add(entry);
				});

		return result;
	}

	private ExpTable toXMLExpTable(Map<BigInteger, Map<Integer, BigInteger>> expTable) {
		ExpTable result = new ExpTable();
		expTable.forEach((key, value) -> {
			org.enquery.encryptedquery.xml.schema.Query.ExpTable.Entry entry = new org.enquery.encryptedquery.xml.schema.Query.ExpTable.Entry();
			entry.setKey(key);
			addExpTableValues(entry.getValue(), value);
			result.getEntry().add(entry);
		});
		return result;
	}

	private void addExpTableValues(List<ExpTableValue> list, Map<Integer, BigInteger> expTable) {
		expTable.forEach((key, value) -> {
			ExpTableValue result = new ExpTableValue();
			result.setKey(key);
			result.setValue(value);
			list.add(result);
		});
	}

	private ExpFileBasedLookup toXMLExpFileBasedLookup(Map<Integer, String> expFileBasedLookup) {
		ExpFileBasedLookup result = new ExpFileBasedLookup();
		expFileBasedLookup.forEach((key, value) -> {
			org.enquery.encryptedquery.xml.schema.Query.ExpFileBasedLookup.Entry entry = new org.enquery.encryptedquery.xml.schema.Query.ExpFileBasedLookup.Entry();
			entry.setKey(key);
			entry.setValue(value);
			result.getEntry().add(entry);
		});
		return result;
	}

	@Converter
	public org.enquery.encryptedquery.xml.schema.QueryInfo toXMLQueryInfo(QueryInfo queryInfo) {
		org.enquery.encryptedquery.xml.schema.QueryInfo result = new org.enquery.encryptedquery.xml.schema.QueryInfo();
		result.setDataPartitionBitSize(queryInfo.getDataPartitionBitSize());
		result.setEmbedSelector(queryInfo.getEmbedSelector());
		result.setHashBitSize(queryInfo.getHashBitSize());
		result.setHashKey(queryInfo.getHashKey());
		result.setIdentifier(queryInfo.getIdentifier().toString());
		result.setNumBitsPerDataElement(queryInfo.getNumBitsPerDataElement());
		result.setNumPartitionsPerDataElement(queryInfo.getNumPartitionsPerDataElement());
		result.setNumSelectors(queryInfo.getNumSelectors());
		result.setQuerySchema(QuerySchemaTypeConverter.toXMLQuerySchema(queryInfo.getQuerySchema()));
		result.setQueryType(queryInfo.getQueryType());
		result.setUseExpLookupTable(queryInfo.useExpLookupTable());
		result.setUseHDFSLookupTable(queryInfo.useHDFSExpLookupTable());
		return result;
	}

	@Converter
	public QueryInfo fromXMLQueryInfo(org.enquery.encryptedquery.xml.schema.QueryInfo queryInfo) {
		Validate.notNull(queryInfo);

		UUID uuid = UUID.fromString(queryInfo.getIdentifier());
		QueryInfo result = new QueryInfo();

		result.setIdentifier(uuid);
		result.setNumSelectors(queryInfo.getNumSelectors());
		result.setHashBitSize(queryInfo.getHashBitSize());
		result.setDataPartitionBitSize(queryInfo.getDataPartitionBitSize());
		result.setQueryType(queryInfo.getQueryType());
		result.setUseExpLookupTable(queryInfo.isUseExpLookupTable());
		result.setEmbedSelector(queryInfo.isEmbedSelector());
		result.setUseHDFSExpLookupTable(queryInfo.isUseHDFSLookupTable());
		result.setHashKey(queryInfo.getHashKey());
		result.setNumBitsPerDataElement(queryInfo.getNumBitsPerDataElement());
		result.setNumPartitionsPerDataElement(queryInfo.getNumPartitionsPerDataElement());
		result.setNumSelectors(queryInfo.getNumSelectors());
		result.setQuerySchema(QuerySchemaTypeConverter.fromXMLQuerySchema(queryInfo.getQuerySchema()));
		result.setQueryType(queryInfo.getQueryType());

		return result;
	}

	public Query toCoreQuery(InputStream xmlQueryInputStream) throws JAXBException {
		return toCoreQuery(queryXMLTextToXMLQuery(xmlQueryInputStream));
	}

	@Converter
	public Query toCoreQuery(org.enquery.encryptedquery.xml.schema.Query xmlQuery) {
		QueryInfo queryInfo = fromXMLQueryInfo(xmlQuery.getQueryInfo());
		Query result = new Query();
		result.setN(xmlQuery.getN());
		result.setNSquared(xmlQuery.getNsquared());
		result.setQueryInfo(queryInfo);

		xmlQuery.getQueryElements()
				.getEntry()
				.stream()
				.forEach(e -> {
					result.getQueryElements()
							.put(e.getKey(), e.getValue());
				});

		xmlQuery.getExpFileBasedLookup()
				.getEntry()
				.stream()
				.forEach(e -> {
					result.getExpFileBasedLookup()
							.put(e.getKey(), e.getValue());
				});

		xmlQuery.getExpTable()
				.getEntry()
				.stream()
				.forEach(e -> {
					Map<Integer, BigInteger> powMap = new HashMap<>();
					e.getValue()
							.stream()
							.forEach(p -> {
								powMap.put(p.getKey(), p.getValue());
							});
					result.addExp(e.getKey(), powMap);
				});

		return result;
	}

}
