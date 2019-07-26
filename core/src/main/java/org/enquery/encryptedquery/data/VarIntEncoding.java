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

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.enquery.encryptedquery.data;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.commons.lang3.Validate;

/**
 * Implements variable length integer encodings from the Google Protocol Buffers specification:
 * https://developers.google.com/protocol-buffers/docs/encoding
 * 
 * The *Varint64 functions implements the base-128 Varint encoding and decoding for longs
 * in the range 0 .. 2^63-1.
 * 
 * The *VarintSigned64 functions can be applied for any long value, i.e. in the range -2^63 .. 2^63-1.
 * The "ZigZag" transformation is first applied before the Varint encoding scheme is used, in order to
 * minimize the encoding byte length for values of small absolute value.
 */
public class VarIntEncoding {

	final byte[] temp = new byte[10];
	
	public int encodingLengthNonnegative64(long n) {
		Validate.isTrue(n >= 0);
		return encodingLengthCommon64(n);
	}
	
	public int encodingLengthSigned64(long n) {
		long n2 = (n << 1) ^ (n >> 63);
		return encodingLengthCommon64(n2);
	}

	private int encodingLengthCommon64(long n) {
		if (n == 0) {
			return 1;
		}
		int count = 0;
		while (n != 0) {
			count += 1;
			n >>>= 7;
		}
		return count;
	}
	
	public byte[] encodeNonnegative64(long n) {
		ByteBuffer buffer = ByteBuffer.wrap(temp);
		encodeNonnegative64(buffer, n);
		return Arrays.copyOf(temp, buffer.position());
	}
	
	public byte[] encodeSigned64(long n) {
		ByteBuffer buffer = ByteBuffer.wrap(temp);
		encodeSigned64(buffer, n);
		return Arrays.copyOf(temp, buffer.position());
	}
	
	public void encodeNonnegative64(ByteBuffer buffer, long n) {
		Validate.isTrue(n >= 0);
		encodeCommon64(buffer, n);
	}
	
	public void encodeSigned64(ByteBuffer buffer, long n) {
		long n2 = (n << 1) ^ (n >> 63);
		encodeCommon64(buffer, n2);
	}
	
	private void encodeCommon64(ByteBuffer buffer, long n) {
		if (n == 0L) {
			buffer.put((byte)0x00);
		} else {
			while (n != 0) {
				// consume low 7 bits of n, then shift
				byte b = (byte) (n & 0x7FL);
				n >>>= 7;
				// if n is not zero yet, set "more" bit
				if (n != 0) {
					b |= (byte)0x80;
				}
				buffer.put(b);
			}
		}
	}

	public long decodeNonnegative64(byte[] buf) {
		return decodeNonnegative64(buf, 0);
	}
	
	public long decodeSigned64(byte[] buf) {
		return decodeSigned64(buf, 0);
	}

	public long decodeNonnegative64(byte[] buf, int start) {
		Validate.isTrue(start >= 0);
		Validate.isTrue(buf.length > start);
		ByteBuffer buffer = ByteBuffer.wrap(buf, start, buf.length-start);
		return decodeNonnegative64(buffer);
	}
	
	public long decodeSigned64(byte[] buf, int start) {
		Validate.isTrue(start >= 0);
		Validate.isTrue(buf.length > start);
		ByteBuffer buffer = ByteBuffer.wrap(buf, start, buf.length-start);
		return decodeSigned64(buffer);
	}

	public long decodeNonnegative64(ByteBuffer buffer) {
		return decodeCommon64(buffer, 9);
	}

	public long decodeSigned64(ByteBuffer buffer) {
		long n2 = decodeCommon64(buffer, 10);
		long n = (n2 >>> 1) ^ (((n2 & 1L) << 63) >> 63);
		return n;
	}
	
	private long decodeCommon64(ByteBuffer buffer, int maxSteps) {
		long result = 0;
		boolean more = false;
		for (int step = 0, shift = 0; step < maxSteps; step++, shift += 7) {
			byte b = buffer.get();
			more = (b & (byte)0x80) != 0;
			result += (long) (b & 0x7f) << shift;
			if (!more) {
				break;
			}
		}
		Validate.isTrue(!more);
		return result;
	}

	/**
	 * @param recordData
	 * @return
	 */
	public int readVarint64(ByteBuffer recordData) {
		// TODO Auto-generated method stub
		return 0;
	}
}
