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
package org.enquery.encryptedquery.querier.wideskies.encrypt;

import java.math.BigInteger;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.encryption.PaillierEncryption;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runnable class for multithreaded PIR query generation
 */
class EncryptQueryBasicTask implements EncryptQueryTask {
	private static final Logger logger = LoggerFactory.getLogger(EncryptQueryBasicTask.class);

	private final int dataPartitionBitSize;
	private final int start; // start of computing range for the runnable
	private final int stop; // stop, inclusive, of the computing range for the runnable
	private final PaillierEncryption paillierEncryption;

	private final Paillier paillier;
	private final Map<Integer, Integer> selectorQueryVecMapping;

	public EncryptQueryBasicTask(PaillierEncryption paillierEncryption, Paillier paillier, Map<Integer, Integer> selectorQueryVecMapping, int dataPartitionBitSize, int start, int stop) {
		this.dataPartitionBitSize = dataPartitionBitSize;
		this.paillierEncryption = paillierEncryption;
		this.paillier = paillier;
		this.selectorQueryVecMapping = selectorQueryVecMapping;
		this.start = start;
		this.stop = stop;
		logger.debug("created task with start = " + start + " stop = " + stop);
	}

	@Override
	public SortedMap<Integer, BigInteger> call() throws PIRException {
		logger.debug("running task with start = " + start + " stop = " + stop);

		// holds the ordered encrypted values to pull after thread computation is complete
		SortedMap<Integer, BigInteger> encryptedValues = new TreeMap<>();
		for (int i = start; i <= stop; i++) {
			Integer selectorNum = selectorQueryVecMapping.get(i);
			BigInteger valToEnc = (selectorNum == null) ? BigInteger.ZERO : (BigInteger.valueOf(2)).pow(selectorNum * dataPartitionBitSize);
			BigInteger encVal = paillierEncryption.encrypt(paillier, valToEnc);// paillier.encrypt(valToEnc);
			encryptedValues.put(i, encVal);
			logger.debug("selectorNum = " + selectorNum + " valToEnc = " + valToEnc + " encVal = " + encVal);
		}

		return encryptedValues;
	}
}


/**
 * Factory class for creating runnables for multithreaded PIR query generation using the basic
 * method.
 */
public class EncryptQueryBasicTaskFactory implements EncryptQueryTaskFactory {
	private static final Logger logger = LoggerFactory.getLogger(EncryptQueryBasicTaskFactory.class);
	private final int dataPartitionBitSize;
	private final Paillier paillier;
	private final Map<Integer, Integer> selectorQueryVecMapping;
	private final PaillierEncryption paillierEncryption;

	public EncryptQueryBasicTaskFactory(PaillierEncryption paillierEncryption, int dataPartitionBitSize, Paillier paillier, Map<Integer, Integer> selectorQueryVecMapping) {
		logger.info("initializing EncryptQueryBasicTaskFactory instance");
		this.paillierEncryption = paillierEncryption;
		this.dataPartitionBitSize = dataPartitionBitSize;
		this.paillier = paillier;
		this.selectorQueryVecMapping = selectorQueryVecMapping;
	}

	public EncryptQueryTask createTask(int start, int stop) {
		return new EncryptQueryBasicTask(paillierEncryption, paillier, selectorQueryVecMapping, dataPartitionBitSize, start, stop);
	}
}
