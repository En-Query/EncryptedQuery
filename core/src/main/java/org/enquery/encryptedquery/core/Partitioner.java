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
package org.enquery.encryptedquery.core;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.utils.ConversionUtils;
import org.enquery.encryptedquery.utils.ISO8601DateParser;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Partitioner implements FieldTypes {

	private static final Logger logger = LoggerFactory.getLogger(Partitioner.class);

	private DataSchema dataSchema;

	public Partitioner() {
		logger.debug("Partitioner Created");
	}

	public void collectParts(List<Byte> parts, Object fieldValue, String dataType, QuerySchemaElement field) throws PIRException {
		Validate.notNull(parts);
		Validate.notNull(fieldValue);
		Validate.notNull(field);

		parts.addAll(fieldToBytes(fieldValue, dataType, field));
		// toPartitions(parts, fieldValue, field);

		// byte[] bytes = new byte[0];
		// switch (fieldType) {
		// case INT:
		// bytes = ConversionUtils.intToBytes((Integer) fieldValue);
		// break;
		// case LONG:
		// bytes = ConversionUtils.longToBytes((Long) fieldValue);
		// break;
		// case FLOAT:
		// bytes = ConversionUtils.intToBytes(Float.floatToRawIntBits((Float) fieldValue));
		// break;
		// case DOUBLE:
		// bytes = ConversionUtils.longToBytes(Double.doubleToRawLongBits((Double) fieldValue));
		// break;
		// case STRING:
		// try {
		// byte[] stringBytes = ((String) fieldValue).getBytes("UTF-8");
		// collectPartsFromByteArray(parts, stringBytes);
		// } catch (UnsupportedEncodingException e) {
		// // UTF-8 is a required encoding.
		// throw new RuntimeException(e);
		// }
		// break;
		// default:
		// throw new RuntimeException("type = " + fieldType + " not recognized!");
		// }
		//
		// // Add any bytes to parts list.
		// for (byte b : bytes) {
		// // Make sure that BigInteger treats the byte as 'unsigned' literal
		// parts.add(BigInteger.valueOf((long) b & 0xFF));
		// }
	}

	/**
	 * Get the number of bytes used by the element.
	 * 
	 * @param element
	 * @param dataLength
	 * @return
	 * @throws PIRException
	 */
	public int getByteSize(QuerySchemaElement element, int dataLength) throws PIRException {
		DataSchemaElement dataSchemaElement = dataSchema.elementByName(element.getName());

		return getBitSize(element, dataLength, dataSchemaElement.getDataType()) / 8;
	}

	/**
	 * Get the number of bytes used by the element.
	 * 
	 * @param element
	 * @param dataLength
	 * @return
	 * @throws PIRException
	 */
	public int getByteSize(QuerySchemaElement element, int dataLength, String dataType) throws PIRException {
		return getBitSize(element, dataLength, dataType) / 8;
	}

	/**
	 * Get the bit size of the allowed primitive java types. For Variable length Strings and byte
	 * arrays this will be the smaller of the dataLenth or the element.getSize + the addition of the
	 * length byte(s)
	 */
	public int getBitSize(QuerySchemaElement element, int dataLength, String dataType) throws PIRException {
		int bits;

		switch (dataType) {
			case BYTE:
				bits = Byte.SIZE;
				break;
			case SHORT:
				bits = Short.SIZE;
				break;
			case INT:
				bits = Integer.SIZE;
				break;
			case LONG:
				bits = Long.SIZE;
				break;
			case FLOAT:
				bits = Float.SIZE;
				break;
			case DOUBLE:
				bits = Double.SIZE;
				break;
			case CHAR:
				bits = Character.SIZE;
				break;
			case STRING:
			case BYTEARRAY:
				// For Fixed size elements set to the size given in the data schema
				if (element.getLengthType().equalsIgnoreCase("fixed")) {
					bits = element.getSize() * 8;
				} else {
					// For Variable sized elements set the size to the max size of the data itself
					// or size given in data schema
					if (element.getSize() > dataLength) {
						bits = dataLength * 8;
					} else {
						bits = element.getSize() * 8;
					}
					// Add the number of bytes to the size for variable length elements
					if (element.getSize() < 255) {
						bits += 8;
					} else if (element.getSize() < 65535) {
						bits += 16;
					}
				}
				break;
			case IP4:
				bits = Integer.SIZE;
				break;
			case IP6:
				bits = 128;
				break;
			case ISO8601DATE:
				bits = Long.SIZE;
				break;
			default:
				throw new PIRException("data type = " + dataType + " not recognized!");
		}
		return bits;
	}


	/**
	 * Add the size element to the beginning of Variable length fields. Fields with a MaxSize of
	 * less than 255 will use 1 byte Fields with a MaxSize of less than 65536 will use 2 bytes.
	 * 
	 * @param parts
	 * @param elementSize
	 * @param maxSize
	 * @return
	 * @throws PIRException
	 */
	private int addSizeBytes(List<Byte> parts, int elementSize, int dataSize) throws PIRException {
		int startIndex = 1;
		int newByteLength = 0;
		byte[] byteElementData = new byte[2];
		if (elementSize < 255) {
			newByteLength = dataSize - 255;
			byteElementData = ConversionUtils.shortToBytes((short) newByteLength);
			parts.add(byteElementData[1]);
			startIndex = 1;
		} else if (elementSize < 65536) {
			newByteLength = dataSize - 32768;
			byteElementData = ConversionUtils.shortToBytes((short) newByteLength);
			parts.add(byteElementData[0]);
			parts.add(byteElementData[1]);
			startIndex = 2;
			// logger.info("Adding Size byte {}, {} to 1st 2 bytes of part",
			// String.format("%02x", elementData[0]), String.format("%02x",
			// elementData[1]) );
		} else {
			throw new PIRException("addSizeBytes: elementSize: " + elementSize + " cannot be greater than 65536.  dataSize is: " + dataSize);
		}
		return startIndex;
	}

	/**
	 * Converts a field value into its byte array representation
	 * 
	 * @param obj
	 * @param dataType
	 * @param element
	 * @return
	 * @throws PIRException
	 */
	public List<Byte> fieldToBytes(Object obj, String dataType, QuerySchemaElement element) throws PIRException {
		List<Byte> parts = new ArrayList<>();
		byte[] bytes = new byte[0];
		byte[] bytesToProcess;
		int startIndex = 0;
		switch (dataType) {
			case BYTE:
				byte value = obj instanceof String ? Byte.parseByte((String) obj) : (byte) obj;
				bytes = new byte[] {value};
				break;
			case CHAR:
				char cvalue = obj instanceof String ? ((String) obj).charAt(0) : (char) obj;
				bytes = ConversionUtils.shortToBytes((short) cvalue);
				break;
			case SHORT:
				short svalue = obj instanceof String ? Short.parseShort((String) obj) : (short) obj;
				bytes = ConversionUtils.shortToBytes(svalue);
				break;
			case INT:
				bytes = extractIntegerBytes(obj);
				break;
			case LONG:
				long lvalue = obj instanceof String ? Long.parseLong((String) obj) : (long) obj;
				bytes = ConversionUtils.longToBytes(lvalue);
				break;
			case FLOAT:
				float fvalue = obj instanceof String ? Float.parseFloat((String) obj) : (float) obj;
				bytes = ConversionUtils.intToBytes(Float.floatToRawIntBits(fvalue));
				break;
			case DOUBLE:
				double dvalue = obj instanceof String ? Double.parseDouble((String) obj) : (double) obj;
				bytes = ConversionUtils.longToBytes(Double.doubleToRawLongBits(dvalue));
				break;
			case STRING:
				try {
					bytesToProcess = ((String) obj).getBytes("UTF-8");
				} catch (UnsupportedEncodingException e) {
					// UTF-8 is a required encoding.
					throw new RuntimeException(e);
				}

				int dataSize = getByteSize(element, bytesToProcess.length, dataType);

				// For Variable length fields, add the size as the 1st or 1st 2 bytes.
				if (element.getLengthType().equalsIgnoreCase("variable")) {
					startIndex = addSizeBytes(parts, element.getSize(), dataSize);
				}

				for (int i = 0; i < (dataSize - startIndex); ++i) {
					if (i < bytesToProcess.length) {
						parts.add(bytesToProcess[i]);
					} else {
						parts.add((byte) 32);
					}
				}
				break;
			case BYTEARRAY:
				bytesToProcess = ConversionUtils.objectToByteArray(obj);
				// Get the length of the data to process
				int byteMaxSize = getByteSize(element, bytesToProcess.length, dataType);

				// For Variable length fields, add the size as the 1st or 1st 2 bytes.
				if (element.getLengthType().equalsIgnoreCase("variable")) {
					startIndex = addSizeBytes(parts, element.getSize(), byteMaxSize);
				}

				for (int i = 0; i < (byteMaxSize - startIndex); ++i) {
					if (i < bytesToProcess.length) {
						parts.add(bytesToProcess[i]);
					} else {
						parts.add((byte) 32);
					}
				}
				break;
			case IP4:
			InetAddress ipv4 = null;
			try {
				ipv4 = InetAddress.getByName((String) obj);
			} catch (UnknownHostException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
	        if (ipv4 != null) {
			bytes = ipv4.getAddress();
				//IP v4 addresses are 4 bytes long
				Validate.isTrue(bytes.length == 4);
	        } 
				break;
			case IP6:
			InetAddress ipv6 = null;
			try {
				ipv6 = InetAddress.getByName((String) obj);
			} catch (UnknownHostException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			if (ipv6 != null) {
				bytes = ipv6.getAddress();
				Validate.isTrue(bytes.length == 16);
			}
				// TODO Add IP6 Coding
				break;
			case ISO8601DATE:
				long dateLongFormat;
				dateLongFormat = ISO8601DateParser.getLongDate((String) obj);
				bytes = ConversionUtils.longToBytes(dateLongFormat);
				break;
			default:
				throw new PIRException("data type = " + dataType + " not recognized!");
		}

		// Add any bytes to parts list.
		for (byte b : bytes) {
			parts.add(b);
		}
		// logger.info("Parts size {} for element {}", parts.size(), element.getName());
		// for (int i = 0; i < parts.size(); i++) {
		// logger.info("Part {} - value {}", i, parts.get(i).toString(16));
		// }

		return parts;
	}

	private byte[] extractIntegerBytes(Object obj) {
		byte[] bytes;
		int ivalue = obj instanceof String ? Integer.parseInt((String) obj) : (int) obj;
		bytes = ConversionUtils.intToBytes(ivalue);
		return bytes;
	}

	/**
	 * Reconstructs the object from the partitions
	 */
	public Object fieldDataFromPartitionedBytes(List<Byte> parts, int partsIndex, String dataType,
			QuerySchemaElement element) throws PIRException {
		Object part = null;

		// logger.info("Element: {} / Type: {} / Length Type: {} / Max Size: {} / parts
		// size: {} /
		// partsIndex: {}",
		// element.getName(), element.getLengthType(), element.getDataType(),
		// element.getSize(),
		// parts.size(), partsIndex);
		switch (dataType) {
		case BYTE:
			part = parts.get(partsIndex).byteValue();
			break;
		case SHORT: {
			byte[] bytes = partsToBytesByte(parts, partsIndex, element, dataType);
			part = ConversionUtils.bytesToShort(bytes);
			break;
		}
		case INT: {
			byte[] bytes = partsToBytesByte(parts, partsIndex, element, dataType);
			part = ConversionUtils.bytesToInt(bytes);
			break;
		}
		case LONG: {
			byte[] bytes = partsToBytesByte(parts, partsIndex, element, dataType);
			part = ConversionUtils.bytesToLong(bytes);
			break;
		}
		case FLOAT: {
			byte[] bytes = partsToBytesByte(parts, partsIndex, element, dataType);
			part = Float.intBitsToFloat(ConversionUtils.bytesToInt(bytes));
			break;
		}
		case DOUBLE: {
			byte[] bytes = partsToBytesByte(parts, partsIndex, element, dataType);
			part = Double.longBitsToDouble(ConversionUtils.bytesToLong(bytes));
			break;
		}
		case CHAR: {
			byte[] bytes = partsToBytesByte(parts, partsIndex, element, dataType);
			part = (char) ConversionUtils.bytesToShort(bytes);
			break;
		}
		case STRING: {
			// logger.info("Processing {} / Type {} /size {} / lengthType {}",
			// element.getName(), element.getDataType(), element.getSize(),
			// element.getLengthType());
			byte[] bytes = partsToBytesByte(parts, partsIndex, element, dataType);
			try {
				// This should remove 0 padding added for partitioning underflowing strings.
				part = new String(bytes, "UTF-8").trim();
			} catch (UnsupportedEncodingException e) {
				// UTF-8 is a required encoding.
				throw new RuntimeException(e);
			}
			break;
		}
		case BYTEARRAY: {
			// logger.info("Processing {} / Type {} /size {} / lengthType {}",
			// element.getName(), element.getDataType(), element.getSize(),
			// element.getLengthType());
			byte[] bytes = partsToBytesByte(parts, partsIndex, element, dataType);
			part = bytes;
			break;
		}
		case IP4:
			byte[] bytes = partsToBytesByte(parts, partsIndex, element, dataType);
			try {
				part = InetAddress.getByAddress(bytes).getHostAddress();
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				throw new RuntimeException(e);
			}
			// if (partsIndex + 3 > parts.size()) {
			// throw new PIRException("Index overflow for field: " + element.getName() + "
			// Starting at position: " + (partsIndex) +
			// " max parts Size: " + parts.size());
			// }
			// part = parts.get(partsIndex).toString() + "." + parts.get(partsIndex +
			// 1).toString() + "." + parts.get(partsIndex + 2).toString() + "."
			// + parts.get(partsIndex + 3).toString();

			break;
		case IP6:
			bytes = partsToBytesByte(parts, partsIndex, element, dataType);
			try {
				part = InetAddress.getByAddress(bytes).getHostAddress();
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				throw new RuntimeException(e);
			}
			break;
		case ISO8601DATE:
			if (partsIndex > parts.size()) {
				throw new PIRException("Index overflow for field: " + element.getName() + " Starting at position: "
						+ (partsIndex) + " max parts Size: " + parts.size());
			}
			bytes = partsToBytesByte(parts, partsIndex, element, dataType);
			long dateLongFormat = ConversionUtils.bytesToLong(bytes);

			part = ISO8601DateParser.fromLongDate(dateLongFormat);
			break;
		default:
			throw new PIRException("dataType = " + dataType + " not recognized!");
		}

		return part;

	}


	/**
	 * Convert the List of BigIntegers into a byte array. For String and ByteArray data Types the
	 * size of the data is stored in the 1st couple of bytes.
	 * 
	 * @param parts
	 * @param partsIndex
	 * @param element
	 * @return
	 * @throws PIRException
	 */
	private byte[] partsToBytesByte(List<Byte> parts, int partsIndex, QuerySchemaElement element, String dataType) throws PIRException {

		byte[] dataSize = new byte[2];
		int numParts = 0;
		int numberToSkip = 0;

		// For String and Byte Array data types, extract the size of the data based on the 1st or
		// 1st 2 bytes.
		if (element.getLengthType().equalsIgnoreCase("variable")
				&& (dataType.equalsIgnoreCase(STRING) || dataType.equalsIgnoreCase(BYTEARRAY))) {
			if (element.getSize() < 255) {
				if (parts.get(partsIndex).byteValue() == 0x00) {
					dataSize[1] = (byte) 0x00;
				} else {
					dataSize[1] = (byte) 0xff;
				}
				dataSize[0] = parts.get(partsIndex).byteValue();
				numParts = ByteBuffer.wrap(dataSize).order(ByteOrder.LITTLE_ENDIAN).getShort() + 255;
				numberToSkip = 1;
				// logger.info("Variable Length of data in byte form = {}", String.format("%02x",
				// dataSize[0]));
			} else if (element.getSize() < 65536) {
				dataSize[0] = parts.get(partsIndex).byteValue();
				dataSize[1] = parts.get(partsIndex + 1).byteValue();
				numParts = ByteBuffer.wrap(new byte[] {dataSize[0], dataSize[1]}).getShort() + 32768;
				numberToSkip = 2;
				// logger.info("Variable Length of data in byte form = {}",
				// Hex.encodeHexString(dataSize));
			} else {
				throw new PIRException("Size cannot exceed 65,535 !");
			}
		} else {
			numParts = getByteSize(element, 0, dataType);
		}
		byte[] result = new byte[numParts];
		// logger.info("Element {} / Size of Array {} / length of string element {} / starting at
		// partsIndex {}", element.getName(), parts.size(), numParts, partsIndex);
		// Since we do not know when the end of data for this rowIndex is, there may be extra 0x00
		// elements in the parts. In that case the numParts may be larger
		// then the actual number of parts so return now.
		if (numParts > parts.size()) {
			// throw new PIRException("Query Element ("+element.getName() + ") Size of data (" + numParts + ") exceeds part size (" + parts.size() + ") !") ;
			logger.warn("Query Element {} Size of Data {} exceeds part size {} minus number to skip {}", element.getName(), numParts, parts.size(), numberToSkip);
			return result;
		}
		for (int i = 0; i < (numParts - numberToSkip); ++i) {
			if ((partsIndex + i + numberToSkip) >= parts.size()) {
				// throw new PIRException("Index overflow for field: " + element.getName() + "
				// Starting at position: " + (partsIndex + i + numberToSkip) +
				// " for " + numParts + " parts Size: " + parts.size());
				logger.warn("Index Overflow for field {} i={} Starting Position={} numParts={} numberToSkip={} parts size={}",
						element.getName(), i, partsIndex + i + numberToSkip, numParts, numberToSkip, parts.size());
				break;
			} else {
				result[i] = parts.get(partsIndex + i + numberToSkip).byteValue();
			}
		}
		return result;
	}


	/**
	 * Returns a List of Byte[]. The Byte array size is the number of bytes respresented by the
	 * dataPartitionBitSize.
	 * 
	 * @param inputData
	 * @param dataPartitionBitSize
	 * @return
	 * @throws PIRException
	 */
	public List<byte[]> createPartitions(List<Byte> inputData, int dataChunkSize) throws PIRException {
		List<byte[]> partitionedData = new ArrayList<>();

		// int bytesPerPartition = getBytesPerPartition(dataPartitionBitSize);

		if (dataChunkSize > 1) {
			byte[] tempByteArray = new byte[dataChunkSize];
			int j = 0;
			for (int i = 0; i < inputData.size(); i++) {
				if (j < dataChunkSize) {
					tempByteArray[j] = inputData.get(i).byteValue();
				} else {
					byte[] returnByte = new byte[tempByteArray.length];
					for (int ndx = 0; ndx < tempByteArray.length; ndx++) {
						returnByte[ndx] = Byte.valueOf(tempByteArray[ndx]);
					}
					partitionedData.add(returnByte);
					j = 0;
					tempByteArray[j] = inputData.get(i).byteValue();
				}
				j++;
			}
			if (j <= dataChunkSize) {
				while (j < dataChunkSize) {
					tempByteArray[j] = new Byte("0");
					j++;
				}
				byte[] returnByte = new byte[tempByteArray.length];
				for (int i = 0; i < tempByteArray.length; i++) {
					returnByte[i] = tempByteArray[i];
				}
				partitionedData.add(returnByte);
			}
		} else {
			for (int i = 0; i < inputData.size(); i++) {
				byte[] tempByteArray = new byte[1];
				tempByteArray[0] = inputData.get(i).byteValue();
				partitionedData.add(tempByteArray);
			}
		}

		return partitionedData;

	}

}
