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
package org.enquery.encryptedquery.querier.wideskies.decrypt;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.utils.ConversionUtils;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecordExtractor {

	private static final Logger logger = LoggerFactory.getLogger(RecordExtractor.class);

	private static boolean dataDetected(List<Byte> parts, int startIndex, int size) {
		boolean dataFound = false;

		for (int i = startIndex; i < (startIndex + size); i++) {
			if (i >= parts.size()) {
				// logger.warn("Not enough data to extract value");
				return dataFound;
			}
			if (parts.get(i) != 0x00) {
				dataFound = true;
				return dataFound;
			}
		}
		return dataFound;
	}

	/**
	 * Method to convert the list of BigInteger raw data element partitions to a list of
	 * QueryResponseRecord object based upon the given queryType
	 */
	public static List<ClearTextQueryResponse.Record> getQueryResponseRecords(QueryInfo queryInfo, List<Byte> parts, int bytesPerPartition) throws PIRException {

		final List<ClearTextQueryResponse.Record> result = new ArrayList<>();
		final QuerySchema qSchema = queryInfo.getQuerySchema();
		Boolean finishedExtracting = false;

		Partitioner partitioner = new Partitioner();

		int partsIndex = 0;
		while (!finishedExtracting) {
			// logger.info("partIndex: {}", partsIndex);
			// Check to see if there is enough parts data to process a record
			if (partsIndex + 4 > parts.size()) {
				break;
			}
			// queryInfo.printQueryInfo();
			// logger.info("Query Schema {}", qSchema.toString());
			ClearTextQueryResponse.Record qrRecord = new ClearTextQueryResponse.Record();// (queryInfo);

			if (queryInfo.getEmbedSelector()) {
				// The Embedded selector is a 4 byte integer hash of the selector data
				// final String selectorFieldName = qSchema.getSelectorField();
				// logger.info("Selector field ( {} )", selectorFieldName);
				QuerySchemaElement qse = new QuerySchemaElement();
				qse.setName("hash");
				qse.setLengthType("fixed");
				qse.setSize(4);
				qse.setMaxArrayElements(1);

				int embeddedSelector = (int) partitioner.fieldDataFromPartitionedBytes(parts, partsIndex, "int", qse);
				// logger.info("embeddedSelector {}", embeddedSelector.toString());
				qrRecord.setSelector(embeddedSelector);
				partsIndex += 4;
			}

			final List<QuerySchemaElement> fieldsToExtract = qSchema.getElementList();
			// logger.info("Field Count to Extract {}", fieldsToExtract.size());
			for (QuerySchemaElement schemaElement : fieldsToExtract) {
				DataSchemaElement dataElement = qSchema.getDataSchema().elementByName(schemaElement.getName());
				Validate.notNull(dataElement, "Field '%s' not found in data schema.", schemaElement.getName());
				final int numElements = schemaElement.getMaxArrayElements();
				// Decode elements
				for (int i = 0; i < numElements; ++i) {
					// logger.info("Extracting value for fieldName ( {} ) / Type ( {} ) / start
					// Index ( {} )", schemaElement.getName(), dataElement.getDataType(),
					// partsIndex);
					if (dataDetected(parts, partsIndex, schemaElement.getSize())) {
						Object element = partitioner.fieldDataFromPartitionedBytes(parts, partsIndex, dataElement.getDataType(), schemaElement);
						// logger.info("Extracted field '{}' with value '{}'",
						// schemaElement.getName(), element);
						qrRecord.add(schemaElement.getName(), element);
						int size = calcElementSize(partitioner, schemaElement, dataElement, element);
						partsIndex += size;
						if (partsIndex >= parts.size()) {
							finishedExtracting = true;
						}
					}
				}
				if (finishedExtracting) {
					break;
				}
			}
			if (qrRecord != null) {
				result.add(qrRecord);
			}
			if (partsIndex >= parts.size()) {
				finishedExtracting = true;
			}
			final int fillerNeeded = partsIndex % bytesPerPartition;
			int skipBytes = 0;
			if (fillerNeeded > 0) {
				skipBytes = bytesPerPartition - fillerNeeded;
			}
			partsIndex += skipBytes;
			// logger.info("Skipping {} bytes, Starting next field at partsIndex: {}", skipBytes,
			// partsIndex);
		}

		return result;
	}

	private static int calcElementSize(Partitioner partitioner, QuerySchemaElement schemaElement, DataSchemaElement dataElement, Object element) throws PIRException {
		int size = schemaElement.getSize();
		if (element != null) {
			element = element.toString().trim();
			if (schemaElement.getLengthType().equalsIgnoreCase("variable")) {
				if (dataElement.getDataType().equalsIgnoreCase("string")) {
					size = partitioner.getByteSize(schemaElement, element.toString().length(), dataElement.getDataType());
				} else if (dataElement.getDataType().equalsIgnoreCase("bytearray")) {
					size = partitioner.getByteSize(schemaElement, ConversionUtils.objectToByteArray(element).length, dataElement.getDataType());
				}
			}
		}
		return size;
	}

}
