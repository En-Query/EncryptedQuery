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
package org.enquery.encryptedquery.xml.transformation;



import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.xml.Versions;

/**
 *
 */
public class BaseReader implements Closeable {

	protected XMLEventReader reader;
	protected boolean ownsReader;

	protected XMLEventReader makeReader(InputStream inputStream) throws XMLStreamException {
		this.reader = XMLFactories.xmlInputFactory.createXMLEventReader(inputStream);
		this.reader = XMLFactories.xmlInputFactory.createFilteredReader(reader, new IgnoreCommentsFilter());
		ownsReader = true;
		return reader;
	}

	protected void useReader(XMLEventReader reader) throws XMLStreamException {
		this.reader = XMLFactories.xmlInputFactory.createFilteredReader(reader, new IgnoreCommentsFilter());
		ownsReader = false;
	}

	protected boolean nextStartElementIs(QName name) throws XMLStreamException {
		boolean result = false;
		skipCharacters();
		XMLEvent event = reader.peek();
		if (event.isStartElement()) {
			StartElement startElement = event.asStartElement();
			result = name.equals(startElement.getName());
		}

		return result;
	}

	protected boolean nextEndElementIs(QName name) throws XMLStreamException {
		boolean result = false;
		skipCharacters();
		XMLEvent event = reader.peek();
		if (event.isEndElement()) {
			EndElement element = event.asEndElement();
			result = name.equals(element.getName());
		}

		return result;
	}

	private void skipCharacters() throws XMLStreamException {
		while (reader.hasNext()) {
			XMLEvent event = reader.peek();
			if (event.isCharacters() ||
					event.isStartDocument() ||
					event.isProcessingInstruction()) {
				reader.nextEvent();
			} else {
				break;
			}
		}
	}

	protected StartElement skipStartElement(QName name) throws XMLStreamException {
		validateNextStartElement(name);
		return reader.nextEvent().asStartElement();
	}

	protected void validateNextStartElement(QName name) throws XMLStreamException {
		if (!nextStartElementIs(name)) {
			throw new XMLStreamException("Expected '" + name + "'", reader.peek().getLocation());
		}
	}

	protected String parseString(QName qName, boolean required) throws XMLStreamException {
		if (!required && !nextStartElementIs(qName)) {
			return null;
		}
		skipStartElement(qName);
		return reader.getElementText();
	}

	protected boolean isStartNode(XMLEvent event, QName qName) {
		if (!event.isStartElement()) return false;
		return qName.equals(event.asStartElement().getName());
	}

	protected boolean isEndNode(XMLEvent event, QName qName) {
		if (!event.isEndElement()) return false;
		return qName.equals(event.asEndElement().getName());
	}

	protected Date parseDate(QName qName) throws XMLStreamException {
		if (!nextStartElementIs(qName)) return null;
		skipStartElement(qName);
		return XMLFactories.toUTCDate(XMLFactories.dtf.newXMLGregorianCalendar(reader.getElementText()));
	}

	protected Integer parseInteger(QName qName, boolean required) throws XMLStreamException {
		if (!required && !nextStartElementIs(qName)) {
			return null;
		}
		skipStartElement(qName);
		return Integer.valueOf(reader.getElementText());
	}

	protected boolean parseBoolean(QName qName, boolean required) throws XMLStreamException {
		if (!required && !nextStartElementIs(qName)) {
			return false;
		}
		skipStartElement(qName);
		return Boolean.parseBoolean(reader.getElementText());
	}

	protected byte[] parseBase64(QName qName, boolean required) throws XMLStreamException {
		if (!required && !nextStartElementIs(qName)) {
			return null;
		}
		skipStartElement(qName);
		return Base64.decodeBase64(reader.getElementText());
	}

	protected void validateNextEndElement(QName name) throws XMLStreamException {
		if (!nextEndElementIs(name)) {
			throw new XMLStreamException("Expected '" + name + "'", reader.peek().getLocation());
		}
	}

	protected void skipEndElement(QName qName) throws XMLStreamException {
		validateNextEndElement(qName);
		reader.nextEvent();
	}

	@Override
	public void close() throws IOException {
		try {
			if (reader != null && ownsReader) {
				reader.close();
			}
		} catch (XMLStreamException e) {
			throw new IOException("Error closing reader.", e);
		}
	}

	protected void validateVersion(StartElement element, String expectedVersion) throws XMLStreamException {
		Validate.notNull(element);

		// make sure the version is the currently supported
		Attribute attribute = element.getAttributeByName(Versions.VERSION_ATTRIB);
		if (attribute == null) {
			throw new XMLStreamException("Missing attribute: " + Versions.VERSION_ATTRIB, element.getLocation());
		}
		if (!expectedVersion.equals(attribute.getValue())) {
			throw new XMLStreamException(
					String.format("Incorrect version in '%s' attribute. Actual: '%s', Expected: '%s'.",
							Versions.VERSION_ATTRIB,
							attribute.getValue(),
							expectedVersion,
							element.getLocation()));
		}
	}

	/**
	 * @param writer
	 * @param response2
	 * @throws XMLStreamException
	 */
	protected void writeVersionAttribute(XMLEventWriter writer, String version) throws XMLStreamException {
		writer.add(XMLFactories.eventFactory.createAttribute(Versions.SCHEMA_VERSION_ATTRIB, version));
	}

}
