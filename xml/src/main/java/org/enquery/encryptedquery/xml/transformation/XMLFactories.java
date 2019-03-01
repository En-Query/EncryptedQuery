package org.enquery.encryptedquery.xml.transformation;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

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
	private static TimeZone utcTimeZone = TimeZone.getTimeZone("UTC");

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

	public static XMLGregorianCalendar toUTCXMLTime(Date date) {
		if (date == null) return null;
		GregorianCalendar c = new GregorianCalendar(utcTimeZone);
		c.setTime(date);
		return dtf.newXMLGregorianCalendar(c);
	}

	public static Date toUTCDate(XMLGregorianCalendar c) {
		if (c == null) return null;
		GregorianCalendar gregorianCalendar = c.toGregorianCalendar();
		ZonedDateTime zdt = gregorianCalendar.toZonedDateTime();
		return Date.from(zdt.toInstant());
	}

}
