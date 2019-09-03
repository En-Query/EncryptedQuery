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
package org.enquery.encryptedquery.data;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deals with encoding/decoding of data records for Query processing
 */
public class RecordEncoding {

	private static final Logger log = LoggerFactory.getLogger(RecordEncoding.class);
	private final QueryInfo queryInfo;
	private final VarIntEncoding varIntEncoder = new VarIntEncoding();

	private Integer embeddedSelector;

	/**
	 * 
	 */
	public RecordEncoding(QueryInfo queryInfo) {
		Validate.notNull(queryInfo);
		Validate.notNull(queryInfo.getQuerySchema());
		queryInfo.getQuerySchema().validate();

		this.queryInfo = queryInfo;
	}

	/**
	 * After a call to <code>encode</code> or <code>decode</code> the hashed selector value
	 * 
	 * @return
	 */
	public Integer getEmbeddedSelector() {
		return embeddedSelector;
	}

	/**
	 * Pulls the correct selector from the dataMap.
	 * <p>
	 * Pulls first element of array if element is an array type
	 */
	public String getSelectorStringValue(Map<String, Object> recordData) {
		return getSelectorStringValue(recordData.get(queryInfo.getQuerySchema().getSelectorField()));
	}

	/**
	 * @param field
	 * @return
	 */
	public String getSelectorStringValue(Object data) {
		final QuerySchema qSchema = queryInfo.getQuerySchema();
		final DataSchema dSchema = qSchema.getDataSchema();
		final String selectorFieldName = qSchema.getSelectorField();
		final DataSchemaElement selectorField = dSchema.elementByName(selectorFieldName);
		return (data == null) ? "" : FieldType.asString(selectorField.getDataType(), data);
	}


	/**
	 * Convert a record to byte sequence
	 * 
	 * @param partitioner
	 * @param queryInfo
	 * @param recordData
	 * @return
	 * @throws PIRException
	 */
	public ByteBuffer encode(Map<String, Object> recordData) throws PIRException {
		Validate.notNull(recordData);

		final QuerySchema qSchema = queryInfo.getQuerySchema();

		// write the record size (encoded)
		int recordSize = calculateRecordSize(recordData);
		if (log.isDebugEnabled()) {
			log.debug("Encoding record size as: {} bytes.", recordSize);
		}

		// encode the record size, allocate buffer and store it
		byte[] recordSizeBytes = varIntEncoder.encodeNonnegative64(recordSize);
		final ByteBuffer buffer = ByteBuffer.allocate(recordSize + recordSizeBytes.length);
		buffer.put(recordSizeBytes);

		// Add the embedded selector
		writeSelector(recordData, buffer);

		encodeFields(recordData, qSchema, buffer);

		buffer.rewind();
		return buffer;
	}

	private void encodeFields(Map<String, Object> recordData, final QuerySchema qSchema, ByteBuffer buffer) throws PIRException {
		final boolean debugging = log.isDebugEnabled();

		final FieldEncoder fieldEncoder = new FieldEncoder(varIntEncoder, buffer);

		for (QuerySchemaElement qse : qSchema.getElementList()) {

			final DataSchemaElement dse = qSchema.getDataSchema().elementByName(qse.getName());
			Validate.notNull(dse, "Field '%s' not found in data schema.", qse.getName());

			if (debugging) {
				log.debug("Before writing field '{}' offset is {}.", qse.getName(),
						buffer.position());
			}

			fieldEncoder.setFieldName(dse.getName());
			fieldEncoder.setMaxSize(qse.getSize());
			fieldEncoder.setMaxArraySize(qse.getMaxArrayElements());

			final Object fieldValue = recordData.get(qse.getName());
			final FieldType dataType = dse.getDataType();

			dataType.convert(fieldEncoder, fieldValue);
			if (debugging) {
				log.debug("After writing field '{}' offset is {}.", qse.getName(),
						buffer.position());
			}
		}
	}

	/**
	 * @return
	 */
	private int calculateRecordSize(Map<String, Object> recordData) {
		// the size of the rowhash (embedded selector)
		int result = Integer.BYTES;
		result += calculateSizeOfFields(recordData);
		return result;
	}

	/**
	 * @param recordData
	 * @return
	 */
	private int calculateSizeOfFields(Map<String, Object> recordData) {
		int result = 0;
		for (QuerySchemaElement qse : queryInfo.getQuerySchema().getElementList()) {
			result += calculateFieldSize(qse.getName(), recordData.get(qse.getName()));
		}
		return result;
	}

	/**
	 * @param fieldName
	 * @param value
	 * @return
	 */
	private int calculateFieldSize(String fieldName, Object data) {
		Validate.notNull(fieldName);

		final QuerySchemaElement qse = queryInfo.getQuerySchema().getElement(fieldName);
		final DataSchemaElement dse = queryInfo.getQuerySchema().getDataSchema().elementByName(fieldName);

		int result = calculateFieldSize(dse, qse, data);

		if (log.isDebugEnabled()) {
			log.debug("Size of field '{}' of type '{}' is {}", fieldName, dse.getDataType(),
					result);
		}

		return result;
	}

	/**
	 * @param dse
	 * @param qse
	 * @param data
	 * @return
	 */
	private int calculateFieldSize(DataSchemaElement dse, QuerySchemaElement qse, Object data) {
		FieldSizeCalculator calculator = new FieldSizeCalculator(varIntEncoder, qse.getSize(), qse.getMaxArrayElements());
		FieldType dataType = dse.getDataType();
		Validate.notNull(dataType);
		return dataType.convert(calculator, data);
	}

	public Map<String, Object> decode(ByteBuffer recordBuffer) {
		Validate.notNull(recordBuffer);
		final boolean debugging = log.isDebugEnabled();

		Map<String, Object> result = new HashMap<>();
		// first must read record length
		int recordLength = (int) varIntEncoder.decodeNonnegative64(recordBuffer);

		if (debugging) {
			log.debug("Decoded record size as: {} bytes.", recordLength);
		}

		embeddedSelector = readEmbeddedSelector(recordBuffer);

		if (debugging) {
			log.debug("Read embedded selector: {}.", embeddedSelector);
		}

		result.putAll(readFields(recordBuffer));
		return result;
	}

	/**
	 * @param fieldBuffer
	 * @return
	 */
	private int readEmbeddedSelector(ByteBuffer fieldBuffer) {
		return fieldBuffer.getInt();
	}

	/**
	 * @param fieldBuffer
	 * @return
	 */
	private Map<String, Object> readFields(ByteBuffer buffer) {
		final boolean debugging = log.isDebugEnabled();

		final Map<String, Object> result = new HashMap<>();
		final QuerySchema qSchema = queryInfo.getQuerySchema();
		final FieldDecoder fieldDecoder = new FieldDecoder(varIntEncoder, buffer);

		final List<QuerySchemaElement> fieldsToExtract = qSchema.getElementList();
		for (QuerySchemaElement qse : fieldsToExtract) {
			final DataSchemaElement dse = qSchema.getDataSchema().elementByName(qse.getName());
			Validate.notNull(dse, "Field '%s' not found in data schema.", qse.getName());

			if (debugging) {
				log.debug("Before reading field '{}' offset is {}.", qse.getName(),
						buffer.position());
			}

			fieldDecoder.setFieldName(dse.getName());
			final FieldType dataType = dse.getDataType();
			Object value = dataType.convert(fieldDecoder);
			result.put(dse.getName(), value);
			if (debugging) {
				log.debug("After reading field '{}' = {} offset is {}.", qse.getName(), value,
						buffer.position());
			}
		}

		return result;
	}

	private void writeSelector(Map<String, Object> recordData, ByteBuffer buffer) throws PIRException {
		embeddedSelector = KeyedHash.hash("aux", 32, getSelectorStringValue(recordData));
		buffer.putInt(embeddedSelector);
	}
}
