/*
 * Copyright 2017 EnQuery.
 * This product includes software licensed to EnQuery under 
 * one or more license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 * This file has been modified from its original source.
 */
package org.enquery.encryptedquery.query.wideskies;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.elasticsearch.hadoop.mr.WritableArrayWritable;
import org.enquery.encryptedquery.schema.data.DataSchema;
import org.enquery.encryptedquery.schema.data.DataSchemaRegistry;
import org.enquery.encryptedquery.schema.data.partitioner.DataPartitioner;
import org.enquery.encryptedquery.schema.data.partitioner.PrimitiveTypePartitioner;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.response.QueryResponseJSON;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.StringUtils;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for helper methods to perform the encrypted query
 */
public class QueryUtils {
	private static final Logger logger = LoggerFactory.getLogger(QueryUtils.class);

	/**
	 * Method to convert the given BigInteger raw data element partitions to a QueryResponseJSON
	 * object based upon the given queryType
	 */
	public static QueryResponseJSON extractQueryResponseJSON(QueryInfo queryInfo, QuerySchema qSchema, List<Byte> parts) throws PIRException {
		QueryResponseJSON qrJSON = new QueryResponseJSON(queryInfo);

		DataSchema dSchema = DataSchemaRegistry.get(qSchema.getDataSchemaName());

		int numArrayElementsToReturn = SystemConfiguration.getIntProperty("pir.numReturnArrayElements", 1);

		int partsIndex = 0;
		if (queryInfo.getEmbedSelector()) {
			String selectorFieldName = qSchema.getSelectorName();
			String type = dSchema.getElementType(selectorFieldName);
			String embeddedSelector = getEmbeddedSelectorFromPartitions(parts, partsIndex, type, dSchema.getPartitionerForElement(selectorFieldName));

			qrJSON.setSelector(embeddedSelector);
			partsIndex += 4;

	//		logger.info("Extracted embedded selector = " + embeddedSelector + " parts.size() = " + parts.size());
		}

		List<String> dataFieldsToExtract = qSchema.getElementNames();
		for (String fieldName : dataFieldsToExtract) {
			int numElements = 1;
			if (dSchema.isArrayElement(fieldName)) {
				numElements = numArrayElementsToReturn;
			}
			// Decode elements
			for (int i = 0; i < numElements; ++i) {
				String type = dSchema.getElementType(fieldName);
				Object element = dSchema.getPartitionerForElement(fieldName).fromPartitions(parts, partsIndex, type);
				qrJSON.setMapping(fieldName, element);
				partsIndex += dSchema.getPartitionerForElement(fieldName).getNumPartitions(type);
			}
		}

		return qrJSON;
	}

	/**
	 * Method to convert the given data element given by the JSONObject data element into the
	 * extracted BigInteger partitions based upon the given queryType
	 */
	public static List<Byte> partitionDataElement(QuerySchema qSchema, JSONObject jsonData, boolean embedSelector) throws PIRException {
		List<Byte> parts = new ArrayList<>();
		DataSchema dSchema = DataSchemaRegistry.get(qSchema.getDataSchemaName());

		// Add the embedded selector to the parts
		if (embedSelector) {
			String selectorFieldName = qSchema.getSelectorName();
			String type = dSchema.getElementType(selectorFieldName);
			String selector = getSelectorByQueryTypeJSON(qSchema, jsonData);
			parts.addAll(embeddedSelectorToPartitions(selector, type, dSchema.getPartitionerForElement(selectorFieldName)));
		}

		// Add all appropriate data fields
		List<String> dataFieldsToExtract = qSchema.getElementNames();
		for (String fieldName : dataFieldsToExtract) {
			Object dataElement = null;
			if (jsonData.containsKey(fieldName)) {
				dataElement = jsonData.get(fieldName);
			}

			if (dSchema.isArrayElement(fieldName)) {
				List<String> elementArray;
				if (dataElement == null) {
					elementArray = Collections.singletonList("0");
				} else {
					elementArray = StringUtils.jsonArrayStringToArrayList(dataElement.toString());
				}
				parts.addAll(dSchema.getPartitionerForElement(fieldName).arrayToPartitions(elementArray, dSchema.getElementType(fieldName)));
			} else {
				if (dataElement == null) {
					dataElement = "0";
				}
				parts.addAll(dSchema.getPartitionerForElement(fieldName).toPartitions(dataElement.toString(), dSchema.getElementType(fieldName)));
			}
		}

		return parts;

	}

	/**
	 * Method to convert the given data element given by the JSONObject data element into the
	 * extracted BigInteger partitions based upon the given queryType
	 */
	public static List<Byte> partitionDataElementMap(QuerySchema qSchema, Map<String, Object> recordData, boolean embedSelector) throws PIRException {
		List<Byte> parts = new ArrayList<>();
		DataSchema dSchema = DataSchemaRegistry.get(qSchema.getDataSchemaName());

		// Add the embedded selector to the parts
		if (embedSelector) {
			String selectorFieldName = qSchema.getSelectorName();
			String type = dSchema.getElementType(selectorFieldName);
			String selector = (String) recordData.get(selectorFieldName); //getSelectorByQueryTypeJSON(qSchema, jsonData);
            if (selector != null) {
            	parts.addAll(embeddedSelectorToPartitions(selector, type, dSchema.getPartitionerForElement(selectorFieldName)));
            }
		}

		// Add all appropriate data fields
		List<String> dataFieldsToExtract = qSchema.getElementNames();
		for (String fieldName : dataFieldsToExtract) {
			Object dataElement = null;
			if (recordData.containsKey(fieldName)) {
				dataElement = recordData.get(fieldName);
			}

			if (dSchema.isArrayElement(fieldName)) {
				List<String> elementArray;
				if (dataElement == null) {
					elementArray = Collections.singletonList("0");
				} else {
					elementArray = StringUtils.jsonArrayStringToArrayList(dataElement.toString());
				}
				parts.addAll(dSchema.getPartitionerForElement(fieldName).arrayToPartitions(elementArray, dSchema.getElementType(fieldName)));
			} else {
				if (dataElement == null) {
					dataElement = "0";
				}
				parts.addAll(dSchema.getPartitionerForElement(fieldName).toPartitions(dataElement.toString(), dSchema.getElementType(fieldName)));
			}
		}

		return parts;
	}

	/**
	 * Method to convert the given data element given by the MapWritable data element into the
	 * extracted BigInteger partitions based upon the given queryType
	 */
	public static List<Byte> partitionDataElement(MapWritable dataMap, QuerySchema qSchema, DataSchema dSchema, boolean embedSelector) throws PIRException {
		List<Byte> parts = new ArrayList<>();

		// Add the embedded selector to the parts
		if (embedSelector) {
			String selectorFieldName = qSchema.getSelectorName();
			String type = dSchema.getElementType(selectorFieldName);
			String selector = getSelectorByQueryType(dataMap, qSchema, dSchema);
			parts.addAll(embeddedSelectorToPartitions(selector, type, dSchema.getPartitionerForElement(selectorFieldName)));
		}

		// Add all appropriate data fields
		List<String> dataFieldsToExtract = qSchema.getElementNames();
		for (String fieldName : dataFieldsToExtract) {
			Object dataElement = null;
			if (dataMap.containsKey(dSchema.getTextName(fieldName))) {
				dataElement = dataMap.get(dSchema.getTextName(fieldName));
			}

			if (dSchema.isArrayElement(fieldName)) {
				List<String> elementArray = null;
				if (dataElement == null) {
					elementArray = Collections.singletonList("");
				} else if (dataElement instanceof WritableArrayWritable) {
					elementArray = Arrays.asList(((WritableArrayWritable) dataElement).toStrings());
				} else if (dataElement instanceof ArrayWritable) {
					elementArray = Arrays.asList(((ArrayWritable) dataElement).toStrings());
				}

				parts.addAll(dSchema.getPartitionerForElement(fieldName).arrayToPartitions(elementArray, dSchema.getElementType(fieldName)));
			} else {
				if (dataElement == null) {
					dataElement = "";
				} else if (dataElement instanceof Text) {
					dataElement = dataElement.toString();
				}
				parts.addAll(dSchema.getPartitionerForElement(fieldName).toPartitions(dataElement, dSchema.getElementType(fieldName)));
			}
		}

		return parts;
	}

	/**
	 * Method to convert the given data element given by the MapWritable data element into the
	 * extracted packed byte array based upon the given queryType
	 */
	public static Byte[] partitionDataElementAsBytes(MapWritable dataMap, QuerySchema qSchema, DataSchema dSchema, boolean embedSelector, int bytesPerPartition) throws PIRException {
		// XXX we assume each BigInteger returned by partitionDataElement only contains one byte of data
		List<Byte> bytesAsBI = partitionDataElement(dataMap, qSchema, dSchema, embedSelector);

	
		//Calculate how much padding to add to record to accommodate different dps sizes.
		int remainder = bytesAsBI.size() % bytesPerPartition;
		int addPadding = 0;
		if (remainder > 0) {
			addPadding = bytesPerPartition - remainder;
		}
		int overallSize = bytesAsBI.size() + addPadding;
		Byte[] packedBytes = new Byte[overallSize];
		for (int i = 0; i < bytesAsBI.size(); i++) {
			packedBytes[i] = bytesAsBI.get(i).byteValue();
		}
		
		//If needed pack with "0" bytes until full
		for (int i = bytesAsBI.size(); i < overallSize; i++ ) {
			packedBytes[i] = new Byte("0");
		}
		
		return packedBytes;
	}

	/**
	 * Method to convert the given selector into the extracted BigInteger partitions
	 */
	public static List<Byte> embeddedSelectorToPartitions(String selector, String type, DataPartitioner partitioner) throws PIRException {
		List<Byte> parts;

		int partitionBits = partitioner.getBits(type);
		if (partitionBits > 32) // hash and add 32-bit hash value to partitions
		{
			int hashedSelector = KeyedHash.hash("aux", 32, selector);
			parts = partitioner.toPartitions(hashedSelector, PrimitiveTypePartitioner.INT);
	       // logger.debug("selector {} hash {}", selector, hashedSelector);
		} else
		// if selector size <= 32 bits or is an IP, add actual selector
		{
			parts = partitioner.toPartitions(selector, type);
		}
		return parts;
	}

	/**
	 * Method get the embedded selector from a given selector
	 * 
	 */
	public static String getEmbeddedSelector(String selector, String type, DataPartitioner partitioner) throws PIRException {
		String embeddedSelector;

		int partitionBits = partitioner.getBits(type);
		if (partitionBits > 32) // hash and add 32-bit hash value to partitions
		{
			embeddedSelector = String.valueOf(KeyedHash.hash("aux", 32, selector));
		} else
		// if selector size <= 32 bits, add actual selector
		{
			embeddedSelector = selector;
		}

		return embeddedSelector;
	}

	/**
	 * Reconstructs the String version of the embedded selector from its partitions
	 */
	public static String getEmbeddedSelectorFromPartitions(List<Byte> parts, int partsIndex, String type, Object partitioner) throws PIRException {
		String embeddedSelector;

		int partitionBits = ((DataPartitioner) partitioner).getBits(type);
		if (partitionBits > 32) // the embedded selector will be the 32-bit hash value of the hit
								// selector
		{
			embeddedSelector = ((DataPartitioner) partitioner).fromPartitions(parts, partsIndex, PrimitiveTypePartitioner.INT).toString();
		} else
		// if selector size <= 32 bits or is an IP, the actual selector was embedded
		{
			embeddedSelector = ((DataPartitioner) partitioner).fromPartitions(parts, partsIndex, type).toString();
		}

		return embeddedSelector;
	}

	/**
	 * Pulls the correct selector from the MapWritable data element given the queryType
	 * <p>
	 * Pulls first element of array if element is an array type
	 */
	public static String getSelectorByQueryType(MapWritable dataMap, QuerySchema qSchema, DataSchema dSchema) {
		String selector;

		if (dataMap == null) throw new RuntimeException("dataMap is null");
		if (dSchema == null) throw new RuntimeException("dSchema is null");
		if (qSchema == null) throw new RuntimeException("qSchema is null");

		String fieldName = qSchema.getSelectorName();
		Text textName = dSchema.getTextName(fieldName);
		Writable writable = dataMap.get(textName);
		// logger.debug("fieldName={}, textName={}, writable={}", fieldName, textName, writable);

		if (dSchema.isArrayElement(fieldName)) {
			if (writable instanceof WritableArrayWritable) {
				String[] selectorArray = ((WritableArrayWritable) writable).toStrings();
				selector = selectorArray[0];
			} else {
				String[] elementArray = ((ArrayWritable) writable).toStrings();
				selector = elementArray[0];
			}
		} else {
			selector = writable.toString();
		}

		return selector;
	}

	/**
	 * Pulls the correct selector from the JSONObject data element given the queryType
	 * <p>
	 * Pulls first element of array if element is an array type
	 */
	public static String getSelectorByQueryTypeJSON(QuerySchema qSchema, JSONObject dataMap) {
		String selector;

		DataSchema dSchema = DataSchemaRegistry.get(qSchema.getDataSchemaName());
		String fieldName = qSchema.getSelectorName();

		try {
			if (dSchema.isArrayElement(fieldName)) {
				List<String> elementArray = StringUtils.jsonArrayStringToArrayList(dataMap.get(fieldName).toString());
				selector = elementArray.get(0);
			} else {
				selector = dataMap.get(fieldName).toString();
			}
		} catch (NullPointerException e) {
			return null;
		} catch (Exception e) {
			logger.warn("Exception extracting selector value for selector {} / Exception: {}", fieldName, e.getMessage()); 
			return null;
			
		}
		return selector;
	}

	// For debug
	private static void printParts(List<Byte> parts) {
		int i = 0;
		for (Byte part : parts) {
			logger.debug("parts(" + i + ") = " + part.intValue() + " parts bits = " + String.format("%02X", part.byteValue()));
			++i;
		}
	}
	
	public static int getBytesPerPartition(int dataPartitionBitSize) {
		// determine how many bytes per partition based on the dataPartitionBitSize
		// dataPartitionBitSize needs to be a multiple of 8 as we are using UTF-8 and we do not want to split a byte.
		int bytesPerPartition = 1;
		if (( dataPartitionBitSize % 8 ) == 0 ) {
			bytesPerPartition = dataPartitionBitSize / 8 ;
		}
		else {
			logger.error("dataPartitionBitSize must be a multiple of 8 !! {}", dataPartitionBitSize);
		}
        return bytesPerPartition;

	}
	
	public static List<Byte[]> createPartitions(List<Byte> inputData, int dataPartitionBitSize) {
		List<Byte[]> partitionedData = new ArrayList<Byte[]>();

	    int bytesPerPartition = getBytesPerPartition(dataPartitionBitSize);
	    
		if (bytesPerPartition > 1) {
			byte[] tempByteArray = new byte[bytesPerPartition];
			int j = 0;
			for (int i = 0; i < inputData.size(); i++) {
				if (j < bytesPerPartition) {
					tempByteArray[j] = inputData.get(i).byteValue();
				} else {
		            Byte[] returnByte = new Byte[tempByteArray.length];
		      		for (int ndx = 0; ndx < tempByteArray.length; ndx++)
				    {
				        returnByte[ndx] = Byte.valueOf(tempByteArray[ndx]);
				    }
					partitionedData.add(returnByte);
					j = 0;
					tempByteArray[j] = inputData.get(i).byteValue();
				}
				j++;
			}
			if (j <= bytesPerPartition ) {
				while (j < bytesPerPartition) {
					tempByteArray[j] = new Byte("0");
					j++;
				}
	            Byte[] returnByte = new Byte[tempByteArray.length];
	      		for (int i = 0; i < tempByteArray.length; i++)
			    {
			        returnByte[i] = Byte.valueOf(tempByteArray[i]);
			    }
				partitionedData.add(returnByte);
			}
		} else {  
			for (int i = 0; i < inputData.size(); i++) {
				Byte[] tempByteArray = new Byte[1];
				tempByteArray[0] = inputData.get(i).byteValue();
				partitionedData.add(tempByteArray);
			}
		}

		return partitionedData;
        
	}
}
