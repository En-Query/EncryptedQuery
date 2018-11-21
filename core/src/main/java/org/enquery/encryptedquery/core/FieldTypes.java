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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.enquery.encryptedquery.utils.ConversionUtils;

public interface FieldTypes {
	String BYTE = "byte",
			SHORT = "short",
			INT = "int",
			LONG = "long",
			FLOAT = "float",
			DOUBLE = "double",
			CHAR = "char",
			STRING = "string",
			BYTEARRAY = "byteArray",
			IP4 = "ip4",
			IP6 = "ip6",
			ISO8601DATE = "ISO8601Date";


	default String asString(Object fieldValue, String type) {
		switch (type) {
			case BYTE:
				return new String(new byte[] {(byte) fieldValue});
			case SHORT:
				return Short.toString((short) fieldValue);
			case INT:
				return Integer.toString((int) fieldValue);
			case LONG:
				return Long.toString((long) fieldValue);
			case FLOAT:
				return Float.toString((float) fieldValue);
			case DOUBLE:
				return Double.toString((double) fieldValue);
			case CHAR:
				return Character.toString((char) fieldValue);
			case STRING:
				return (String) fieldValue;
			case IP4:
				return (String) fieldValue;
			case IP6:
				return (String) fieldValue;
			case ISO8601DATE:
				return (String) fieldValue;
			case BYTEARRAY:
				return ConversionUtils.byteArrayToHexString((byte[]) fieldValue);
		}
		throw new RuntimeException("type = " + type + " not recognized!");
	}

	default byte[] asByteArray(Object fieldValue, String type) {
		ByteBuffer result = null;
		switch (type) {
			case BYTE:
				result = ByteBuffer.allocate(1);
				result.put((byte) fieldValue);
				break;
			case SHORT:
				result = ByteBuffer.allocate(Short.BYTES);
				result.putShort((short) fieldValue);
				break;
			case INT:
				result = ByteBuffer.allocate(Integer.BYTES);
				result.putInt((int) fieldValue);
				break;
			case LONG:
				result = ByteBuffer.allocate(Long.BYTES);
				result.putLong((long) fieldValue);
				break;
			case FLOAT:
				result = ByteBuffer.allocate(Float.BYTES);
				result.putFloat((float) fieldValue);
				break;
			case DOUBLE:
				result = ByteBuffer.allocate(Double.BYTES);
				result.putDouble((double) fieldValue);
				break;
			case CHAR:
				result = ByteBuffer.allocate(1);
				result.putChar((char) fieldValue);
				break;
			case STRING:
				Charset charset = Charset.forName("UTF-8");
				result = ByteBuffer.wrap(((String) fieldValue).getBytes(charset));
				break;
			default:
				throw new RuntimeException("type = " + type + " not recognized!");
		}
		return result.array();
	}
}
