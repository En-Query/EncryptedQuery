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

public class ConversionUtils {

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
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(bos);) {
			oos.writeObject(obj);
			oos.flush();
			return bos.toByteArray();
		} catch (Exception e) {
			throw new RuntimeException("Error converting object to byte array.", e);
		}
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

}
