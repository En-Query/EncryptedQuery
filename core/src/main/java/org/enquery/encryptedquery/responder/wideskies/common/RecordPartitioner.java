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
package org.enquery.encryptedquery.responder.wideskies.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecordPartitioner {
	private static final Logger logger = LoggerFactory.getLogger(RecordPartitioner.class);
	private static final boolean debugLogOn = logger.isDebugEnabled();

	/**
	 * Pulls the correct selector from the dataMap.
	 * <p>
	 * Pulls first element of array if element is an array type
	 */
	public static String getSelectorValue(QuerySchema qSchema, Map<String, Object> dataMap) {
		String selector;

		DataSchema dSchema = qSchema.getDataSchema();
		DataSchemaElement element = dSchema.elementByName(qSchema.getSelectorField());

		try {
			if (element.getIsArray()) {
				List<String> elementArray = jsonArrayStringToArrayList(dataMap.get(qSchema.getSelectorField()).toString());
				selector = elementArray.get(0);
			} else {
				selector = dataMap.get(qSchema.getSelectorField()).toString();
			}
		} catch (NullPointerException e) {
			return null;
		} catch (Exception e) {
			logger.warn("Exception extracting selector value for selector {} / Exception: {}", qSchema.getSelectorField(), e.getMessage());
			return null;

		}
		return selector;
	}

	/**
	 * Method to take an input json array format string and output an ArrayList
	 */
	public static ArrayList<String> jsonArrayStringToArrayList(String jsonString) {
		String modString = jsonString.replaceFirst("\\[", "");
		modString = modString.replaceFirst("\\]", "");
		modString = modString.replaceAll("\"", "");
		String[] elements = modString.split("\\s*,\\s*");

		return new ArrayList<>(Arrays.asList(elements));
	}

	/**
	 * Create partitions for an array of the same type of elements - used when a data value field is
	 * an array and we wish to encode these into the return value
	 */
	public static List<Byte> arrayToPartitions(List<?> elementList, String dataType, QuerySchemaElement qse, Partitioner partitioner) throws PIRException {
		List<Byte> byteParts = new ArrayList<>();
		int numArrayElementsToReturn = qse.getMaxArrayElements();
		for (int i = 0; i < numArrayElementsToReturn; ++i) {
			if (elementList.size() > i) // we may have an element with a list rep that has fewer
										// than numArrayElementsToReturn elements
			{
				if (debugLogOn) {
					logger.debug("Adding parts for elementArray(" + i + ") = " + elementList.get(i));
				}

				List<Byte> newBytes = partitioner.fieldToBytes(elementList.get(i), dataType, qse);
				byteParts.addAll(newBytes);
			} else {
				String blank = "";
				List<Byte> newBytes = partitioner.fieldToBytes(blank, dataType, qse);
				byteParts.addAll(newBytes);
			}

		}
		return byteParts;
	}

	/**
	 * Method to convert the given data element given by the JSONObject data element into the
	 * extracted BigInteger partitions based upon the given queryType
	 */
	public static List<Byte> partitionRecord(Partitioner partitioner, QuerySchema qSchema, Map<String, Object> recordData, boolean embedSelector) throws PIRException {
		List<Byte> parts = new ArrayList<>();
		DataSchema dSchema = qSchema.getDataSchema();
		String selector = null;
		Object hashedSelector = null;
		// Add the embedded selector to the parts
		if (embedSelector) {
			selector = getSelectorValue(qSchema, recordData);
			if (selector != null) {
				hashedSelector = KeyedHash.hash("aux", 32, selector);
				parts = partitioner.fieldToBytes(hashedSelector, "int", null);
			}
		}

		if (debugLogOn) {
			logger.debug("Embed Selector ( {} ) Selector Value {}, Hash  ( {} )", embedSelector, selector, hashedSelector);
			for (Byte b : parts) {
				logger.debug("Selector bytes {}", String.format("%02X", b.byteValue()));
			}
		}

		// Add all selected data fields
		for (QuerySchemaElement qse : qSchema.getElementList()) {
			Object dataElement = null;
			String dataType = dSchema.elementByName(qse.getName()).getDataType();
			if (recordData.containsKey(qse.getName())) {
				dataElement = recordData.get(qse.getName());
			}

			if (dSchema.elementByName(qse.getName()).getIsArray()) {
				List<String> elementArray;
				if (dataElement == null) {
					elementArray = Collections.singletonList("0");
				} else {
					elementArray = jsonArrayStringToArrayList(dataElement.toString());
				}
				List<Byte> arrayBytes = arrayToPartitions(elementArray, dataType, qse, partitioner);
				parts.addAll(arrayBytes);
			} else {
				if (dataElement == null) {
					dataElement = "0";
				}
				List<Byte> fieldBytes = partitioner.fieldToBytes(dataElement, dataType, qse);
				if (debugLogOn) {
					logger.debug("Bytes for field {}", qse.getName());
					logParts(fieldBytes);
				}
				parts.addAll(fieldBytes);
			}
		}

		if (debugLogOn) {
			logParts(parts);
		}

		return parts;
	}

	/**
	 * Method to print out bytes created for record data. Used for Debugging only!!
	 * 
	 * @param parts
	 */
	private static void logParts(List<Byte> parts) {
		logger.debug("Parts:");
		int counter = 0;
		for (Byte b : parts) {
			logger.debug("    {} value {}", counter++, String.format("%02X", b.byteValue()));
		}
	}

}
