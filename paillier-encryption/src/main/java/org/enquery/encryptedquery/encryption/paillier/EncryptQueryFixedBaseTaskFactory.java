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
import org.enquery.encryptedquery.utils.MontgomeryReduction;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating runnables for multithreaded PIR query generation using the faster
 * fixed-base point method.
 */
public class EncryptQueryFixedBaseTaskFactory implements EncryptQueryTaskFactory {
	private static final Logger log = LoggerFactory.getLogger(EncryptQueryFixedBaseTaskFactory.class);

	private final MontgomeryReduction montPSquared;
	private final MontgomeryReduction montQSquared;
	// TODO: separate ones for p and q?
	private final int maxExponentBitLength;
	// TODO: make configurable
	private final int windowSize = 8;
	private final int numWindows;
	private final Random randomGenerator;
	private final Map<Integer, Integer> selectorQueryVecMapping;
	private int dataPartitionBitSize;
	private final BigInteger[][] pLUT;
	private final BigInteger[][] qLUT;
	private final PaillierPublicKey publicKey;
	private final PaillierPrivateKey privateKey;

	public EncryptQueryFixedBaseTaskFactory(//
			int dataPartitionBitSize,
			Map<Integer, Integer> selectorQueryVecMapping,
			Random randomGenerator,
			PaillierKeyPair keyPair) //
	{

		log.info("initializing EncryptQueryFixedBaseTaskFactory instance");

		Validate.notNull(selectorQueryVecMapping);
		Validate.notNull(randomGenerator);
		Validate.notNull(keyPair);

		this.publicKey = keyPair.getPub();
		this.privateKey = keyPair.getPriv();

		this.dataPartitionBitSize = dataPartitionBitSize;
		this.selectorQueryVecMapping = selectorQueryVecMapping;
		this.montPSquared = new MontgomeryReduction(privateKey.getPSquared());
		this.montQSquared = new MontgomeryReduction(privateKey.getQSquared());
		this.maxExponentBitLength = privateKey.getPMaxExponent().bitLength();
		if (this.maxExponentBitLength != privateKey.getQMaxExponent().bitLength()) {
			throw new IllegalArgumentException("pMaxExponent and qMaxExponent have different bit lengths");
		}
		this.numWindows = (maxExponentBitLength + windowSize - 1) / windowSize;
		this.randomGenerator = randomGenerator;
		pLUT = computeLookupTable(windowSize, numWindows, montPSquared, privateKey.getPBasePoint());
		qLUT = computeLookupTable(windowSize, numWindows, montQSquared, privateKey.getQBasePoint());
	}

	private BigInteger[][] computeLookupTable(int windowSize, int numWindows, MontgomeryReduction montPSquared, BigInteger basePoint) {
		int W = (1 << windowSize) - 1;
		BigInteger[][] lut = new BigInteger[numWindows][];
		BigInteger tmp = montPSquared.toMontgomery(basePoint);
		for (int win = 0; win < numWindows; win++) {
			lut[win] = new BigInteger[W];
			BigInteger g = tmp;
			for (int i = 0; i < W; i++) {
				lut[win][i] = tmp;
				tmp = montPSquared.montMultiply(tmp, g);
			}
		}
		return lut;
	}

	@Override
	public EncryptQueryFixedBaseTask createTask(int start, int stop) {
		return new EncryptQueryFixedBaseTask(start, stop);
	}

	/**
	 * Runnable class for multithreaded PIR query generation
	 */
	private class EncryptQueryFixedBaseTask implements EncryptQueryTask {

		// start of computing range for the runnable
		private final int start;
		// stop, inclusive, of the computing range for the runnable
		private final int stop;

		/**
		 * 
		 */
		public EncryptQueryFixedBaseTask(int start, int stop) {
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

				final PaillierCipherText cipherText = new PaillierCipherText(encVal);

				result.put(i, cipherText);

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
			return encryptWithLookupTables(bitIndex, rp, rq);
		}

		private BigInteger encryptWithLookupTables(int bitIndex, BigInteger rp, BigInteger rq) {
			BigInteger pAnswer = encryptWithLookupTable(bitIndex, rp, pLUT, montPSquared);
			BigInteger qAnswer = encryptWithLookupTable(bitIndex, rq, qLUT, montQSquared);
			pAnswer = montPSquared.fromMontgomery(pAnswer);
			qAnswer = montQSquared.fromMontgomery(qAnswer);
			BigInteger answer = privateKey.getCrtNSquared().combine(pAnswer, qAnswer, publicKey.getNSquared());

			if (bitIndex >= 0) {
				BigInteger tmp = publicKey.getN().shiftLeft(bitIndex).add(BigInteger.ONE).mod(publicKey.getNSquared());
				answer = answer.multiply(tmp).mod(publicKey.getNSquared());
			}

			return answer;
		}

		private BigInteger encryptWithLookupTable(int bitIndex, BigInteger r, BigInteger[][] lut, MontgomeryReduction mont) {
			byte[] rbytes = r.toByteArray();
			int windowsPerByte = 8 / windowSize;
			int winMask = (1 << windowSize) - 1;
			int numWindows = rbytes.length * windowsPerByte;
			if (numWindows > lut.length) {
				numWindows = lut.length;
			}
			BigInteger ans = mont.getMontOne();
			boolean done = false;
			int win = 0;
			for (int j = 0; j < rbytes.length; j++) {
				int byteIndex = rbytes.length - 1 - j;
				int byteValue = rbytes[byteIndex] & 0xff;
				for (int i = 0; i < windowsPerByte; i++) {
					int winValue = byteValue & winMask;
					byteValue >>= windowSize;
					if (winValue != 0) {
						ans = mont.montMultiply(ans, lut[win][winValue - 1]);
					}
					win += 1;
					if (win >= numWindows) {
						done = true;
						break;
					}
				}
				if (done) {
					break;
				}
			}

			return ans;
		}
	}
}
