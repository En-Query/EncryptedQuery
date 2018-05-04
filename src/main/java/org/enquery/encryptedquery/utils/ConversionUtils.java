
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
package org.enquery.encryptedquery.utils;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConversionUtils {

	private static final Logger logger = LoggerFactory.getLogger(ConversionUtils.class);

	public static short bytesToShort(byte[] bytes) {
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
	}

	public static byte[] shortToByteArray(short value) {

		byte[] bytes = new byte[2];
		ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
		buffer.putShort(value);
		return buffer.array();
	}

	// toByteArray and toObject are taken from: http://tinyurl.com/69h8l7x
	/**
	 * Convert a byte array Object into a byte array
	 * @param obj
	 * @return byte[]
	 */
	public static byte[] objectToByteArray(Object obj) {
		byte[] bytes = null;
		ByteArrayOutputStream bos = null;
		ObjectOutputStream oos = null;
		try {
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(obj);
			oos.flush();
			bytes = bos.toByteArray();
			if (oos != null) {
				oos.close();
			}
			if (bos != null) {
				bos.close();
			}
		} catch (Exception e) {
			logger.error("Error converting object to byte Array: ", e.getMessage());
		}
		return bytes;
	}

	public static byte[] bigIntegerToByteArray(final BigInteger bigInteger, int byteArraySize) {

		byte[] bytes = bigInteger.toByteArray();
		byte[] tempBytes = null;
		byte[] returnBytes = new byte[byteArraySize];

		// bigInteger.toByteArray returns a two's compliment.  We need an unsigned value
		if (bytes[0] == 0) {
			tempBytes = Arrays.copyOfRange(bytes, 1, bytes.length);
		} else {
			tempBytes = bytes;
		}
		//If the Biginteger is zero, we still need to return a byte array of zeros!
		int sizeDifference = byteArraySize - tempBytes.length;
		if (sizeDifference > 0) {
			System.arraycopy(tempBytes, 0, returnBytes, sizeDifference, tempBytes.length);

		} else {
			returnBytes = tempBytes;
		}

		return returnBytes;

	}

	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
	/**
	 * Convert a byte[] into a Hex String
	 * @param byte[]
	 * @return String
	 */
	public static String byteArrayToHexString(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for ( int j = 0; j < bytes.length; j++ ) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	/**
	 * Convert a String value into a byte array
	 * @param s String
	 * @return byte[]
	 */
	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
					+ Character.digit(s.charAt(i+1), 16));
		}
		return data;
	}
}
