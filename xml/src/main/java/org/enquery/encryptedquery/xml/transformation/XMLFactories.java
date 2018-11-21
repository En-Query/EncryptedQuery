package org.enquery.encryptedquery.xml.transformation;

import java.util.Date;
import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;

public class XMLFactories {
	public static final DatatypeFactory dtf;
	public static final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
	public static final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
	public static final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

	static {
		try {
			dtf = DatatypeFactory.newInstance();
		} catch (DatatypeConfigurationException e) {
			throw new RuntimeException("Initialization of DatatypeFactory error.", e);
		}

		xmlInputFactory.setProperty(
				XMLInputFactory.IS_NAMESPACE_AWARE,
				Boolean.TRUE);

		xmlInputFactory.setProperty(
				XMLInputFactory.IS_COALESCING,
				Boolean.TRUE);

		xmlOutputFactory.setProperty(
				XMLOutputFactory.IS_REPAIRING_NAMESPACES,
				Boolean.TRUE);
	}

	public static XMLGregorianCalendar toXMLTime(Date date) {
		if (date == null) return null;
		GregorianCalendar c = new GregorianCalendar();
		c.setTime(date);
		return dtf.newXMLGregorianCalendar(c);
	}

	public static Date toLocalTime(XMLGregorianCalendar c) {
		if (c == null) return null;
		return c.toGregorianCalendar().getTime();
	}

}
