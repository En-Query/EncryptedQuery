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
package org.enquery.encryptedquery.responder.wideskies.common;

import java.math.BigInteger;
import java.util.Map;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.jni.JNILoader;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComputeEncryptedColumnYaoJNI implements ComputeEncryptedColumn, ResponderProperties {
	// private static boolean libraryLoaded = false;
	private long hContext;

	private static final Logger logger = LoggerFactory.getLogger(ComputeEncryptedColumnYaoJNI.class);
	// private final Map<Integer,BigInteger> queryElements;

	private native long yaoNew(byte[] NSquaredBytes, int maxRowIndex, int b);

	private native void yaoSetQueryElement(long hContext, int rowIndex, byte[] queryElementBytes);

	private native void yaoInsertDataPart(long hContext, int rowIndex, int part);

	private native void yaoInsertDataPart2(long hContext, byte[] queryElementBytes, int part);

	private native byte[] yaoComputeColumnAndClearData(long hContext);

	private native void yaoClearData(long hContext);

	private native void yaoDelete(long hContext);

	public static void validateParameters(int dataPartitionBitSize) {
		if (dataPartitionBitSize <= 0 || 24 < dataPartitionBitSize || (dataPartitionBitSize % 8) != 0) {
			throw new IllegalArgumentException("YaoJNI responder method requires dataPartitionBitSize to be 8, 16, or 24; " + dataPartitionBitSize + " given");
		}
		if (dataPartitionBitSize > 16) {
			throw new IllegalArgumentException("YaoJNI responder method currently requires dataPartitionBitSize <= 16 to limit memory usage, " + dataPartitionBitSize + " given");
		}
	}

	@Override
	public void initialize(Map<Integer, BigInteger> queryElements, BigInteger NSquared, ModPowAbstraction modPowAbstraction, Map<String, String> config) {
		// public ComputeEncryptedColumnYaoJNI(Map<Integer, BigInteger> queryElements, BigInteger
		// NSquared, int maxRowIndex, int dataPartitionBitSize) {

		Double mri = Math.pow(2, Integer.valueOf(config.get(HASH_BIT_SIZE)));
		int maxRowIndex = mri.intValue();
		int dataPartitionBitSize = Integer.valueOf(config.get(DATA_PARTITION_BIT_SIZE));
		validateParameters(dataPartitionBitSize);

		JNILoader.loadLibrary(config.get(JNI_LIBRARIES));

		this.hContext = yaoNew(NSquared.toByteArray(), maxRowIndex, dataPartitionBitSize);
		if (0L == this.hContext) {
			throw new NullPointerException("failed to allocate context from native code");
		}
		if (queryElements != null) {
			for (int rowIndex = 0; rowIndex < maxRowIndex; rowIndex++) {
				yaoSetQueryElement(this.hContext, rowIndex, queryElements.get(rowIndex).toByteArray());
			}
		}
	}

	@Override
	public void insertDataPart(int rowIndex, BigInteger part) {
		// insertDataPart(queryElements.get(rowIndex), part);
		yaoInsertDataPart(hContext, rowIndex, part.intValue());
	}

	@Override
	public void insertDataPart(BigInteger queryElement, BigInteger part) {
		yaoInsertDataPart2(hContext, queryElement.toByteArray(), part.intValue());
	}

	@Override
	public BigInteger computeColumnAndClearData() {
		byte[] bytes = yaoComputeColumnAndClearData(hContext);
		return new BigInteger(1, bytes);
	}

	@Override
	public void clearData() {
		if (hContext != 0L) {
			yaoClearData(hContext);
		}
	}

	// TODO: how to have this done automatically on GC?
	@Override
	public void free() {
		yaoDelete(hContext);
		hContext = 0;
	}

	@Override
	protected void finalize() throws Throwable {
		yaoDelete(hContext);
		hContext = 0;
	}

	@Override
	public String name() {
		return "YaoJNI";
	}
}
