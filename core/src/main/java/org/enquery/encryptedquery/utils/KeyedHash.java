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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for the PIR keyed hash
 * <p>
 * Computes the hash as the bitwise AND of a mask and MD5(key || input). The MD5 algorithm is
 * assumed to be supported on all Java platforms.
 * 
 */
public class KeyedHash {
	private static final String HASH_METHOD = "MD5";
	private static final Logger logger = LoggerFactory.getLogger(KeyedHash.class);

	/**
	 * Hash method that uses the java String hashCode()
	 */
	public static int hash(String key, int bitSize, String input) {
		int fullHash = 0;
		try {
			MessageDigest md = MessageDigest.getInstance(HASH_METHOD);
			md.update(key.getBytes());
			byte[] array = md.digest(input.getBytes());

			fullHash = fromByteArray(array);
		} catch (NoSuchAlgorithmException e) {
			// This is not expected to happen, as every implementation of
			// the Java platform is required to implement MD5.
			logger.debug("failed to compute a hash of type " + HASH_METHOD);
			throw new UnsupportedOperationException("failed to compute a hash of type " + HASH_METHOD);
		}

		// Take only the lower bitSize-many bits of the resultant hash
		int bitLimitedHash = fullHash;
		if (bitSize < 32) {
			bitLimitedHash = (0xFFFFFFFF >>> (32 - bitSize)) & fullHash;
		}

		return bitLimitedHash;
	}

	private static int fromByteArray(byte[] bytes) {
		return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
	}
}
