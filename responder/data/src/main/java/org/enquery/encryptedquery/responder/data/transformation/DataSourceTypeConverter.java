/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.responder.data.transformation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Collection;
import java.util.stream.Collectors;

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
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.service.ResourceUriRegistry;
import org.enquery.encryptedquery.xml.schema.DataSourceResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResources;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

@Component(service = DataSourceTypeConverter.class)
public class DataSourceTypeConverter {

	private static final Logger log = LoggerFactory.getLogger(DataSourceTypeConverter.class);

	private static final String XSD_PATH = "/org/enquery/encryptedquery/xml/schema/data-source-resources.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	@Reference(target = "(type=rest)")
	private ResourceUriRegistry registry;

	public DataSourceTypeConverter() {
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


	public void marshal(org.enquery.encryptedquery.xml.schema.DataSourceResources dataSchemas, OutputStream os) throws JAXBException, UnsupportedEncodingException, IOException, XMLStreamException, FactoryConfigurationError {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.setSchema(xmlSchema);
		marshaller.marshal(objectFactory.createDataSourceResources(dataSchemas), os);
	}


	public org.enquery.encryptedquery.xml.schema.DataSourceResources unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);

		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.DataSourceResources> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.DataSourceResources.class);

		return element.getValue();
	}

	public DataSourceResources toXMLDataSources(Collection<DataSource> javaDataSources) {
		if (log.isDebugEnabled()) {
			log.debug("Converting {} to XML DataSourceResources.", javaDataSources);
		}
		DataSourceResources result = new DataSourceResources();
		result.getDataSourceResource().addAll(
				javaDataSources
						.stream()
						.map(javaDataSource -> {
							org.enquery.encryptedquery.xml.schema.DataSource xmlDataSource =
									new org.enquery.encryptedquery.xml.schema.DataSource();

							xmlDataSource.setName(javaDataSource.getName());
							xmlDataSource.setDescription(javaDataSource.getDescription());
							xmlDataSource.setDataSchemaName(javaDataSource.getDataSchemaName());
							xmlDataSource.setType(javaDataSource.getType().toString());

							DataSourceResource resource = new DataSourceResource();
							resource.setDataSource(xmlDataSource);
							resource.setId(javaDataSource.getId());

							final Integer dataSchemaId = javaDataSource.getDataSchema().getId();

							resource.setSelfUri(registry.dataSourceUri(dataSchemaId, javaDataSource.getId()));
							resource.setExecutionsUri(registry.executionsUri(dataSchemaId, javaDataSource.getId()));
							resource.setDataSchemaUri(registry.dataSchemaUri(dataSchemaId));

							return resource;
						})
						.collect(Collectors.toList()));
		return result;
	}

	public DataSourceResource toXMLDataSource(DataSource javaDataSource) {

		if (log.isDebugEnabled()) {
			log.debug("Converting {} to XML DataSourceResource.", javaDataSource);
		}

		org.enquery.encryptedquery.xml.schema.DataSource xmlDataSource =
				new org.enquery.encryptedquery.xml.schema.DataSource();

		xmlDataSource.setName(javaDataSource.getName());
		xmlDataSource.setDescription(javaDataSource.getDescription());
		xmlDataSource.setDataSchemaName(javaDataSource.getDataSchemaName());
		xmlDataSource.setType(javaDataSource.getType().toString());

		DataSourceResource resource = new DataSourceResource();
		resource.setDataSource(xmlDataSource);
		resource.setId(javaDataSource.getId());

		final Integer dataSchemaId = javaDataSource.getDataSchema().getId();

		resource.setSelfUri(registry.dataSourceUri(dataSchemaId, javaDataSource.getId()));
		resource.setExecutionsUri(registry.executionsUri(dataSchemaId, javaDataSource.getId()));
		resource.setDataSchemaUri(registry.dataSchemaUri(dataSchemaId));

		return resource;
	}
}
