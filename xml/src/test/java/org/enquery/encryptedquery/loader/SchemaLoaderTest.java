package org.enquery.encryptedquery.loader;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;

import javax.xml.bind.JAXBException;

import org.enquery.encryptedquery.xml.schema.DataSchema;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.xml.sax.SAXException;

public class SchemaLoaderTest {

	@Rule
	public ExpectedException expectedEx = ExpectedException.none();

	@Test
	public void happyQuery() throws JAXBException, SAXException, FileNotFoundException, IOException {
		SchemaLoader loader = new SchemaLoader();
		org.enquery.encryptedquery.xml.schema.QuerySchema qs = loader.loadQSchema(Paths.get("target/test-classes/query-schema.xml"));
		assertEquals("title", qs.getSelectorField());
	}

	@Test
	public void happyData() throws JAXBException, SAXException, FileNotFoundException, IOException {
		SchemaLoader loader = new SchemaLoader();
		DataSchema ds = loader.loadDSchema(Paths.get("target/test-classes/data-schema.xml"));
		assertEquals("schemaName", ds.getName());
	}

	@Test
	public void querySchemaMissingSelector() throws JAXBException, SAXException, FileNotFoundException, IOException {
		expectedEx.expect(javax.xml.bind.UnmarshalException.class);
		expectedEx.expectCause(
				allOf(
						isA(org.xml.sax.SAXParseException.class),
						hasProperty("message",
								containsString("Invalid content was found starting with element 'field'. One of '{\"http://enquery.net/encryptedquery/queryschema\":selectorField}' is expected."))));

		SchemaLoader loader = new SchemaLoader();
		loader.loadQSchema(Paths.get("target/test-classes/query-schema-missing-selector.xml"));
	}


	@Test
	public void duplicateFieldName() throws JAXBException, SAXException, FileNotFoundException, IOException {
		expectedEx.expect(javax.xml.bind.UnmarshalException.class);
		expectedEx.expectCause(allOf(
				isA(org.xml.sax.SAXParseException.class),
				hasProperty("message",
						containsString("Duplicate unique value [name] declared for identity constraint of element \"dataSchema\""))));

		// expectedEx.expectMessage("Duplicate unique value [name]");
		SchemaLoader loader = new SchemaLoader();
		loader.loadDSchema(Paths.get("target/test-classes/duplicate-field-name-dataschema.xml"));
	}

	@Test
	public void duplicateFieldPosition() throws JAXBException, SAXException, FileNotFoundException, IOException {
		expectedEx.expect(javax.xml.bind.UnmarshalException.class);
		expectedEx.expectCause(allOf(
				isA(org.xml.sax.SAXParseException.class),
				hasProperty("message", containsString("Duplicate unique value [0] declared for identity constraint of element \"dataSchema\""))));
		SchemaLoader loader = new SchemaLoader();
		loader.loadDSchema(Paths.get("target/test-classes/duplicate-field-position-dataschema.xml"));
	}

	@Test
	public void querySchemaDuplicateName() throws JAXBException, SAXException, FileNotFoundException, IOException {
		expectedEx.expect(javax.xml.bind.UnmarshalException.class);
		expectedEx.expectCause(
				allOf(
						isA(org.xml.sax.SAXParseException.class),
						hasProperty("message",
								containsString("Duplicate unique value [field1] declared for identity constraint of element \"querySchema\""))));

		SchemaLoader loader = new SchemaLoader();
		loader.loadQSchema(Paths.get("target/test-classes/query-schema-duplicate-name.xml"));
	}


}
