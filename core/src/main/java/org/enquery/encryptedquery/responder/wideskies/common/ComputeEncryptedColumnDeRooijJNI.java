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

public class ComputeEncryptedColumnDeRooijJNI implements ComputeEncryptedColumn {
	private long hContext;

	private static final Logger logger = LoggerFactory.getLogger(ComputeEncryptedColumnDeRooijJNI.class);

	private native long derooijNew(byte[] NSquaredBytes, int maxRowIndex);

	private native void derooijSetQueryElement(long hContext, int rowIndex, byte[] queryElementBytes);

	private native void derooijInsertDataPart(long hContext, int rowIndex, int part);

	private native void derooijInsertDataPart2(long hContext, byte[] queryElementBytes, int part);

	private native byte[] derooijComputeColumnAndClearData(long hContext);

	private native void derooijClearData(long hContext);

	private native void derooijDelete(long hContext);

	public static void validateParameters(int maxRowIndex, int dataPartitionBitSize) {
		if (dataPartitionBitSize <= 0 || 24 < dataPartitionBitSize || (dataPartitionBitSize % 8) != 0) {
			throw new IllegalArgumentException("DeRooiJNI responder method requires dataPartitionBitSize to be 8, 16, or 24; " + dataPartitionBitSize + " given");
		}
	}

	@Override
	public void initialize(Map<Integer, BigInteger> queryElements, BigInteger NSquared, ModPowAbstraction modPowAbstraction, Map<String, String> config) {

		clearData();

		JNILoader.loadLibraries(config.get(ResponderProperties.JNI_LIBRARIES));

		Double mri = Math.pow(2, Integer.valueOf(config.get(ResponderProperties.HASH_BIT_SIZE)));
		int maxRowIndex = mri.intValue();

		this.hContext = derooijNew(NSquared.toByteArray(), maxRowIndex);
		if (0L == this.hContext) {
			throw new NullPointerException("failed to allocate context from native code");
		}

		if (queryElements != null) {
			for (int rowIndex = 0; rowIndex < maxRowIndex; rowIndex++) {
				derooijSetQueryElement(this.hContext, rowIndex, queryElements.get(rowIndex).toByteArray());
			}
		}
	}

	@Override
	public void insertDataPart(int rowIndex, BigInteger part) {
		derooijInsertDataPart(hContext, rowIndex, part.intValue());
	}

	@Override
	public void insertDataPart(BigInteger queryElement, BigInteger part) {
		derooijInsertDataPart2(hContext, queryElement.toByteArray(), part.intValue());
	}

	@Override
	public BigInteger computeColumnAndClearData() {
		byte[] bytes = derooijComputeColumnAndClearData(hContext);
		return new BigInteger(1, bytes);
	}

	@Override
	public void clearData() {
		if (hContext != 0L) {
			derooijClearData(hContext);
		}
	}

	@Override
	public void free() {
		if (hContext != 0L) {
			derooijDelete(hContext);
			hContext = 0;
		}
	}

	@Override
	protected void finalize() throws Throwable {
		free();
		super.finalize();
	}

	@Override
	public String name() {
		return "DeRooijJNI";
	}

}
