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
package org.enquery.encryptedquery.encryption.paillier;

import java.math.BigInteger;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating Callables for multithreaded PIR query generation using the faster
 * fixed-base point method plus JNI acceleration.
 */
public class EncryptQueryFixedBaseWithJNITaskFactory implements EncryptQueryTaskFactory {

	private static final Logger log = LoggerFactory.getLogger(EncryptQueryFixedBaseWithJNITaskFactory.class);

	// private final Paillier paillier;
	// TODO: separate ones for p and q?
	private final int maxExponentBitLength;
	// TODO: make configurable
	private final int windowSize = 8;
	private final int numWindows;
	private final Random randomGenerator;
	private final int dataPartitionBitSize;
	private final Map<Integer, Integer> selectorQueryVecMapping;
	// opaque handle to context in native code
	private long hContext = 0;
	private PaillierPublicKey publicKey;
	private PaillierPrivateKey privateKey;

	public EncryptQueryFixedBaseWithJNITaskFactory(//
			int dataPartitionBitSize,
			Map<Integer, Integer> selectorQueryVecMapping,
			Random randomGenerator,
			PaillierKeyPair keyPair) {

		log.info("initializing EncryptQueryFixedBaseWithJNITaskFactory instance");

		Validate.notNull(selectorQueryVecMapping);
		Validate.notNull(randomGenerator);
		Validate.notNull(keyPair);

		this.publicKey = keyPair.getPub();
		this.privateKey = keyPair.getPriv();

		this.dataPartitionBitSize = dataPartitionBitSize;
		this.selectorQueryVecMapping = selectorQueryVecMapping;
		this.maxExponentBitLength = privateKey.getPMaxExponent().bitLength();
		if (this.maxExponentBitLength != privateKey.getQMaxExponent().bitLength()) {
			throw new IllegalArgumentException("pMaxExponent and qMaxExponent have different bit lengths");
		}
		this.numWindows = (maxExponentBitLength + windowSize - 1) / windowSize;
		this.randomGenerator = randomGenerator;
		this.hContext = initializeNativeCode();
		if (0 == this.hContext) {
			throw new NullPointerException("failed to allocate context from native code");
		}
	}

	private native long newContext(byte[] pBytes,
			byte[] genPSquaredBytes,
			byte[] qBytes,
			byte[] genQSquaredBytes,
			int primeBitLength,
			int windowSize,
			int numWindows);

	private native byte[] encrypt(long hContext, int bitIndex, byte[] rpBytes, byte[] rqBytes);


	private long initializeNativeCode() {
		return newContext(privateKey.getP().toByteArray(),
				privateKey.getPBasePoint().toByteArray(),
				privateKey.getQ().toByteArray(),
				privateKey.getQBasePoint().toByteArray(),
				-1,
				windowSize,
				numWindows);
	}

	@Override
	public EncryptQueryFixedBaseWithJNITask createTask(int start, int stop) {
		return new EncryptQueryFixedBaseWithJNITask(start, stop);
	}


	/**
	 * Runnable class for multithreaded PIR query generation
	 */
	private class EncryptQueryFixedBaseWithJNITask implements EncryptQueryTask {

		// start of computing range for the runnable
		private final int start;
		// stop, inclusive, of the computing range for the runnable
		private final int stop;

		/**
		 * 
		 */
		public EncryptQueryFixedBaseWithJNITask(int start, int stop) {
			this.start = start;
			this.stop = stop;
		}

		@Override
		public Map<Integer, CipherText> call() throws PIRException {
			final boolean debugging = log.isDebugEnabled();

			final Map<Integer, CipherText> result = new TreeMap<>();
			for (int i = start; i <= stop; i++) {
				final Integer selectorNum = selectorQueryVecMapping.get(i);
				final int bitIndex = (selectorNum == null) ? -1 : selectorNum * dataPartitionBitSize;
				final BigInteger encVal = encrypt(bitIndex);
				final PaillierCipherText ciphertext = new PaillierCipherText(encVal);
				result.put(i, ciphertext);

				if (debugging) {
					log.debug("selectorNum = {}, bitIndex = {},  encVal = {}",
							selectorNum,
							bitIndex,
							encVal);
				}
			}

			return result;
		}

		private BigInteger encrypt(int bitIndex) {
			BigInteger rp, rq;
			do {
				rp = new BigInteger(maxExponentBitLength, randomGenerator);
			} while (rp.compareTo(privateKey.getPMaxExponent()) >= 0 || rp.equals(BigInteger.ZERO));
			do {
				rq = new BigInteger(maxExponentBitLength, randomGenerator);
			} while (rq.compareTo(privateKey.getQMaxExponent()) >= 0 || rq.equals(BigInteger.ZERO));

			byte[] bytes = EncryptQueryFixedBaseWithJNITaskFactory.this
					.encrypt(hContext, bitIndex, rp.toByteArray(), rq.toByteArray());

			return new BigInteger(1, bytes);
		}
	}
}
