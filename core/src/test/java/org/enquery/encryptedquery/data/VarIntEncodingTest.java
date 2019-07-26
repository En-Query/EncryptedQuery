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

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.apache.commons.codec.binary.Hex;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class VarIntEncodingTest {

	private final Logger log = LoggerFactory.getLogger(VarIntEncodingTest.class);

	class TestCase {
		long value;
		int length;

		TestCase(long value, int length) {
			this.value = value;
			this.length = length;
		}
	}

	TestCase[] nonnegTestCases = {
			new TestCase(8L, 1),
			new TestCase(127L, 1),
			new TestCase(128L, 2),
			new TestCase(130L, 2),
			new TestCase(256L, 2),
			new TestCase(63635L, 3),
			new TestCase(0L, 1),
			new TestCase(Long.MAX_VALUE, 9),
	};

	TestCase[] signedTestCases = {
			new TestCase(0L, 1),
			new TestCase(-1L, 1),
			new TestCase(1L, 1),
			new TestCase(-2L, 1),
			new TestCase(-63L, 1),
			new TestCase(63L, 1),
			new TestCase(-64L, 1),
			new TestCase(64L, 2),
			new TestCase(-8191L, 2),
			new TestCase(8191L, 2),
			new TestCase(-8192L, 2),
			new TestCase(8192L, 3),
			new TestCase(Long.MAX_VALUE, 10),
			new TestCase(-Long.MAX_VALUE - 1, 10),
	};

	@Test
	public void nonnegativeTest() {
		for (TestCase testCase : nonnegTestCases) {
			if (testCase.value < 0L) {
				continue;
			}
			VarIntEncoding partitioner = new VarIntEncoding();
			byte[] val = partitioner.encodeNonnegative64(testCase.value);
			log.info("Result: {}", Hex.encodeHexString(val));
			assertEquals(testCase.length, val.length);
			assertEquals(testCase.length, partitioner.encodingLengthNonnegative64(testCase.value));
			assertEquals(testCase.value, partitioner.decodeNonnegative64(val));
		}
	}

	@Test
	public void byteBufferNonnegativeTest() {
		for (TestCase testCase : nonnegTestCases) {
			VarIntEncoding partitioner = new VarIntEncoding();
			ByteBuffer buffer = ByteBuffer.allocate(100);
			buffer.putLong(0xdeadbeefL);
			partitioner.encodeNonnegative64(buffer, testCase.value);
			buffer.putLong(0x01234567L);
			buffer.flip();
			long longValue = buffer.getLong();
			assertEquals(longValue, 0xdeadbeefL);
			long varintValue = partitioner.decodeNonnegative64(buffer);
			assertEquals(varintValue, testCase.value);
			longValue = buffer.getLong();
			assertEquals(longValue, 0x01234567L);
		}
	}

	@Test
	public void signedTest() {
		for (TestCase testCase : signedTestCases) {
			VarIntEncoding encoder = new VarIntEncoding();
			byte[] val = encoder.encodeSigned64(testCase.value);
			assertEquals(testCase.length, val.length);
			assertEquals(testCase.length, encoder.encodingLengthSigned64(testCase.value));
			assertEquals(testCase.value, encoder.decodeSigned64(val));
		}
	}

	@Test
	public void byteBufferSignedTest() {
		for (TestCase testCase : signedTestCases) {
			VarIntEncoding partitioner = new VarIntEncoding();
			ByteBuffer buffer = ByteBuffer.allocate(100);
			buffer.putLong(0xdeadbeefL);
			partitioner.encodeSigned64(buffer, testCase.value);
			buffer.putLong(0x01234567L);
			buffer.flip();
			long longValue = buffer.getLong();
			assertEquals(longValue, 0xdeadbeefL);
			long varintValue = partitioner.decodeSigned64(buffer);
			assertEquals(varintValue, testCase.value);
			longValue = buffer.getLong();
			assertEquals(longValue, 0x01234567L);
		}
	}
}
