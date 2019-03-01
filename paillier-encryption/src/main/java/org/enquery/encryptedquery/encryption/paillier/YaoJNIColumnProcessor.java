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
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;

public class YaoJNIColumnProcessor implements ColumnProcessor {

	private final Map<Integer, CipherText> queryElements;
	private final PaillierPublicKey publicKey;
	// private final ModPowAbstraction modPowAbstraction;
	private final long hContext;

	private native long yaoNew(byte[] NSquaredBytes, int maxRowIndex, int b);

	private native void yaoSetQueryElement(long hContext, int rowIndex, byte[] queryElementBytes);

	private native void yaoInsertDataPart(long hContext, int rowIndex, int part);

	private native void yaoInsertDataPart2(long hContext, byte[] queryElementBytes, int part);

	private native byte[] yaoComputeColumnAndClearData(long hContext);

	private native void yaoClearData(long hContext);

	private native void yaoDelete(long hContext);

	/**
	 * 
	 */
	public YaoJNIColumnProcessor(//
			PaillierPublicKey publicKey,
			Map<Integer, CipherText> queryElements,
			boolean useMontgomery,
			QueryInfo queryInfo,
			Map<String, String> config) {

		Validate.notNull(publicKey);
		Validate.notNull(queryElements);
		Validate.notNull(config);

		// this.modPowAbstraction = modPowAbstraction;
		this.queryElements = queryElements;
		this.publicKey = publicKey;

		// public YaoJNIColumnProcessor(Map<Integer, BigInteger> queryElements, BigInteger
		// publicKey.getNSquared(), int maxRowIndex, int dataPartitionBitSize) {

		final int dataChunkSize = queryInfo.getDataChunkSize();

		// Double mri = Math.pow(2, Integer.valueOf(config.get(HASH_BIT_SIZE)));
		// Double mri = Math.pow(2, queryInfo.getHashBitSize());
		// int maxRowIndex = mri.intValue();
		final int maxRowIndex = 1 << queryInfo.getHashBitSize();
		validateParameters(dataChunkSize);

		JNILoader.load();
		// JNILoader.loadLibrary(config.get(PaillierProperties.JNI_LIBRARIES));

		this.hContext = yaoNew(publicKey.getNSquared().toByteArray(), maxRowIndex, 8 * dataChunkSize);
		if (0L == this.hContext) {
			throw new NullPointerException("failed to allocate context from native code");
		}

		for (int rowIndex = 0; rowIndex < maxRowIndex; rowIndex++) {
			BigInteger element = ((PaillierCipherText) queryElements.get(rowIndex)).getValue();
			yaoSetQueryElement(this.hContext, rowIndex, element.toByteArray());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#insert(int, byte[])
	 */
	@Override
	public void insert(int rowIndex, byte[] input) {
		// TODO: review data to big int conversion
		BigInteger part = new BigInteger(1, input);
		yaoInsertDataPart(hContext, rowIndex, part.intValue());
	}

	@Override
	protected void finalize() throws Throwable {
		if (hContext != 0L) {
			yaoDelete(hContext);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#insert(int, byte[], int, int)
	 */
	@Override
	public void insert(int rowIndex, byte[] input, int inputOffset, int inputLen) {
		// TODO add new method in C library to avoid byte[] copy
		ByteBuffer bb = ByteBuffer.wrap(input, inputOffset, inputLen);
		byte[] b = new byte[bb.remaining()];
		bb.get(b);
		insert(rowIndex, b);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#compute()
	 */
	@Override
	public CipherText compute() {
		final byte[] bytes = yaoComputeColumnAndClearData(hContext);
		return new PaillierCipherText(new BigInteger(1, bytes));
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#clear()
	 */
	@Override
	public void clear() {
		if (hContext != 0L) {
			yaoClearData(hContext);
		}
	}

	private void validateParameters(int dataChunkSize) {
		Validate.isTrue(dataChunkSize >= 0, "dataChunkSize must be a positive number of bytes");
		Validate.isTrue(dataChunkSize <= 2, "YaoJNI responder method currently requires dataChunkSize <= 2 to limit memory usage");
	}
}
