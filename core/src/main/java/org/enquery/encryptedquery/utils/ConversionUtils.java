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
package org.enquery.encryptedquery.utils;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConversionUtils {

	private static final Logger logger = LoggerFactory.getLogger(ConversionUtils.class);

	// public static short bytesToShort(byte[] bytes) {
	// return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
	// }
	//
	// public static byte[] shortToByteArray(short value) {
	//
	// byte[] bytes = new byte[2];
	// ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
	// buffer.putShort(value);
	// return buffer.array();
	// }

	public static byte[] shortToBytes(short value) {
		return new byte[] {(byte) (value >> 8), (byte) value};
	}

	public static short bytesToShort(byte[] bytes) {
		return (short) (bytes[0] << 8 | bytes[1] & 0xff);
	}

	// toByteArray and toObject are taken from: http://tinyurl.com/69h8l7x
	/**
	 * Convert a byte array Object into a byte array
	 * 
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
			throw new RuntimeException("Error converting object to byte array.", e);
		}
		return bytes;
	}

	/**
	 * Convert from a BigInteger to byte[]
	 * 
	 * @param bigInteger
	 * @param byteArraySize
	 * @return
	 */
	public static byte[] bigIntegerToByteArray(final BigInteger bigInteger, int byteArraySize) {

		byte[] bytes = bigInteger.toByteArray();
		byte[] tempBytes = null;
		byte[] returnBytes = new byte[byteArraySize];

		// bigInteger.toByteArray returns a two's compliment. We need an unsigned value
		if (bytes[0] == 0) {
			tempBytes = Arrays.copyOfRange(bytes, 1, bytes.length);
		} else {
			tempBytes = bytes;
		}
		// If the Biginteger is zero, we still need to return a byte array of zeros!
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
	 * 
	 * @param byte[]
	 * @return String
	 */
	public static String byteArrayToHexString(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	/**
	 * Convert a String value into a byte array
	 * 
	 * @param s String
	 * @return byte[]
	 */
	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
					+ Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	public static byte[] intToBytes(int value) {
		return new byte[] {(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
	}

	public static int bytesToInt(byte[] bytes) {
		return (bytes[0] << 24) | (bytes[1] & 0xff) << 16 | (bytes[2] & 0xff) << 8 | (bytes[3] & 0xff);
	}

	public static byte[] longToBytes(long value) {
		return new byte[] {(byte) (value >> 56), (byte) (value >> 48), (byte) (value >> 40), (byte) (value >> 32), (byte) (value >> 24), (byte) (value >> 16),
				(byte) (value >> 8), (byte) value};
	}

	public static long bytesToLong(byte[] bytes) {
		return (long) bytes[0] << 56 | ((long) bytes[1] & 0xff) << 48 | ((long) bytes[2] & 0xff) << 40 | ((long) bytes[3] & 0xff) << 32
				| ((long) bytes[4] & 0xff) << 24 | ((long) bytes[5] & 0xff) << 16 | ((long) bytes[6] & 0xff) << 8 | (long) bytes[7] & 0xff;
	}

	/**
	 * Convert Byte[] object to byte[]
	 * 
	 * @param oBytes
	 * @return byte[]
	 */
	public static byte[] toPrimitives(Byte[] oBytes) {

		byte[] bytes = new byte[oBytes.length];
		for (int i = 0; i < oBytes.length; i++) {
			bytes[i] = oBytes[i];
		}
		return bytes;

	}
}
