package org.enquery.encryptedquery.xml.transformation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Record;
import org.enquery.encryptedquery.xml.schema.ClearTextResponse;
import org.enquery.encryptedquery.xml.schema.Field;
import org.enquery.encryptedquery.xml.schema.Hit;
import org.enquery.encryptedquery.xml.schema.Hits;
import org.enquery.encryptedquery.xml.schema.Selector;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearTextResponseTypeConverterTest {

	private static Logger log = LoggerFactory.getLogger(ClearTextResponseTypeConverterTest.class);

	@Test
	public void test() throws Exception {
		ClearTextResponse ctr = createXMLSampleTextResponse();
		ClearTextResponseTypeConverter converter = new ClearTextResponseTypeConverter();

		String xml;
		try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
			converter.marshal(ctr, os);
			xml = new String(os.toByteArray());
		}
		log.info(xml);

		ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes());
		validate(converter.unmarshal(is));
	}

	@Test
	public void testStream() throws Exception {
		ClearTextResponseTypeConverter converter = new ClearTextResponseTypeConverter();

		ClearTextQueryResponse ctr = createCoreSampleResponse();
		log.info("core:" + ctr);
		try (InputStream inputStream = converter.toXMLStream(ctr)) {
			ClearTextResponse xmlResponse = converter.unmarshal(inputStream);
			validate(xmlResponse);
		}
	}

	@Test
	public void testWithCamel() throws Exception {
		ClearTextResponse ctr = createXMLSampleTextResponse();

		JaxbDataFormat dataFormat = new JaxbDataFormat();
		dataFormat.setContextPath("org.enquery.encryptedquery.xml.schema");
		dataFormat.setSchema("classpath:/org/enquery/encryptedquery/xml/schema/cleartext-response.xsd");
		dataFormat.setObjectFactory(true);
		dataFormat.setMustBeJAXBElement(true);

		Map<String, DataFormatDefinition> dataFormats = new HashMap<>();
		dataFormats.put("clearTextResponseFormat", new DataFormatDefinition(dataFormat));

		DefaultCamelContext itCamelContext = new DefaultCamelContext();
		itCamelContext.setTracing(true);
		itCamelContext.setStreamCaching(true);
		itCamelContext.setName(this.getClass().getSimpleName());
		itCamelContext.setDataFormats(dataFormats);
		itCamelContext.start();


		itCamelContext.addRoutes(new RouteBuilder() {
			@Override
			public void configure() {
				from("direct:marshal")
						.marshal("clearTextResponseFormat")
						.log("${body}")
						.to("mock:marshal-result");

				from("direct:unmarshal")
						.log("${body}")
						.unmarshal("clearTextResponseFormat")
						.to("mock:unmarshal-result");
			}
		});

		MockEndpoint marshalMockEndpoint = itCamelContext.getEndpoint("mock:marshal-result", MockEndpoint.class);
		MockEndpoint unmarshalMockEndpoint = itCamelContext.getEndpoint("mock:unmarshal-result", MockEndpoint.class);

		ProducerTemplate producer = itCamelContext.createProducerTemplate();
		producer.start();

		marshalMockEndpoint.reset();
		marshalMockEndpoint.expectedMessageCount(1);
		producer.requestBody("direct:marshal", ctr);
		marshalMockEndpoint.assertIsSatisfied();

		String xml = marshalMockEndpoint.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(String.class);

		unmarshalMockEndpoint.reset();
		unmarshalMockEndpoint.expectedMessageCount(1);
		producer.requestBody("direct:unmarshal", xml);
		unmarshalMockEndpoint.assertIsSatisfied();


		ClearTextResponse unmarshaled = unmarshalMockEndpoint.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(ClearTextResponse.class);

		validate(unmarshaled);
	}

	private void validate(ClearTextResponse unmarshaled) {
		assertNotNull(unmarshaled);
		assertNotNull(unmarshaled.getSelector());
		assertEquals(1, unmarshaled.getSelector().size());
		Selector selector = unmarshaled.getSelector().get(0);
		assertEquals("name", selector.getSelectorName());
		List<Hits> hits = selector.getHits();
		assertEquals(3, hits.size());

		validate(hits.stream().filter(h -> h.getSelectorValue().equals("Bob>")).findFirst().orElse(null));
		validate(hits.stream().filter(h -> h.getSelectorValue().equals("Joe")).findFirst().orElse(null));
		validate(hits.stream().filter(h -> h.getSelectorValue().equals("Ann")).findFirst().orElse(null));
	}

	private void validate(Hits hits) {
		assertNotNull(hits);
		assertNotNull(hits.getSelectorValue());
		assertEquals(3, hits.getHit().size());
		validate(hits.getHit().get(0));
		validate(hits.getHit().get(1));
		validate(hits.getHit().get(2));
	}

	private void validate(Hit hit) {
		List<Field> fields = hit.getField();
		assertEquals(3, fields.size());

		validate(fields.stream().filter(f -> f.getName().equals("mother")).findFirst().orElseGet(null), "mother", "Alice");
		validate(fields.stream().filter(f -> f.getName().equals("sister")).findFirst().orElseGet(null), "sister", "Rose");
		validate(fields.stream().filter(f -> f.getName().equals("father")).findFirst().orElseGet(null), "father", "Joe");
	}

	private void validate(Field field, String name, String value) {
		assertEquals(name, field.getName());
		assertEquals(value, field.getValue());
	}

	private ClearTextQueryResponse createCoreSampleResponse() {
		ClearTextQueryResponse ctr = new ClearTextQueryResponse("query name", "query Id");

		ClearTextQueryResponse.Selector selector = new ClearTextQueryResponse.Selector("name");
		selector.add(createCoreSampleHits("Bob>"));
		selector.add(createCoreSampleHits("Joe"));
		selector.add(createCoreSampleHits("Ann"));
		ctr.add(selector);
		return ctr;
	}



	private ClearTextResponse createXMLSampleTextResponse() {
		ClearTextResponse ctr = new ClearTextResponse();
		ctr.setQueryId("query id");
		ctr.setQueryName("query name");

		Selector selector = new Selector();
		selector.setSelectorName("name");
		selector.getHits().add(createXMLSampleHits("Bob>"));
		selector.getHits().add(createXMLSampleHits("Joe"));
		selector.getHits().add(createXMLSampleHits("Ann"));

		ctr.getSelector().add(selector);
		return ctr;
	}

	private org.enquery.encryptedquery.data.ClearTextQueryResponse.Hits createCoreSampleHits(String selectorValue) {
		org.enquery.encryptedquery.data.ClearTextQueryResponse.Hits hits =
				new org.enquery.encryptedquery.data.ClearTextQueryResponse.Hits(selectorValue);


		org.enquery.encryptedquery.data.ClearTextQueryResponse.Field alice = createCoreSampleField("mother", "Alice");
		org.enquery.encryptedquery.data.ClearTextQueryResponse.Field rose = createCoreSampleField("sister", "Rose");
		org.enquery.encryptedquery.data.ClearTextQueryResponse.Field joe = createCoreSampleField("father", "Joe");
		hits.add(createCoreSampleRecord(alice, rose, joe));
		hits.add(createCoreSampleRecord(alice, rose, joe));
		hits.add(createCoreSampleRecord(alice, rose, joe));

		return hits;
	}


	private Hits createXMLSampleHits(String selectorValue) {

		Hits hits = new Hits();
		hits.setSelectorValue(selectorValue);


		Field alice = createXMLSampleField("mother", "Alice");
		Field rose = createXMLSampleField("sister", "Rose");
		Field joe = createXMLSampleField("father", "Joe");
		hits.getHit().add(createXMLSampleHit(alice, rose, joe));
		hits.getHit().add(createXMLSampleHit(alice, rose, joe));
		hits.getHit().add(createXMLSampleHit(alice, rose, joe));

		return hits;
	}


	private Hit createXMLSampleHit(Field... fields) {
		Hit result = new Hit();
		for (Field f : fields) {
			result.getField().add(f);
		}
		return result;
	}

	private Record createCoreSampleRecord(org.enquery.encryptedquery.data.ClearTextQueryResponse.Field... fields) {
		org.enquery.encryptedquery.data.ClearTextQueryResponse.Record result =
				new org.enquery.encryptedquery.data.ClearTextQueryResponse.Record();
		for (org.enquery.encryptedquery.data.ClearTextQueryResponse.Field f : fields) {
			result.add(f);
		}
		return result;
	}

	private Field createXMLSampleField(String fieldName, String fieldValue) {
		Field field = new Field();
		field.setName(fieldName);
		field.setValue(fieldValue);
		return field;
	}

	private org.enquery.encryptedquery.data.ClearTextQueryResponse.Field createCoreSampleField(String fieldName, String fieldValue) {
		return new org.enquery.encryptedquery.data.ClearTextQueryResponse.Field(fieldName, fieldValue);
	}
}
