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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeRooijJNIColumnProcessor implements ColumnProcessor {
	private static final Logger log = LoggerFactory.getLogger(DeRooijJNIColumnProcessor.class);

	private final long hContext;
	private final PaillierPublicKey publicKey;

	private native long derooijNew(byte[] NSquaredBytes, int maxRowIndex);

	private native void derooijSetQueryElement(long hContext, int rowIndex, byte[] queryElementBytes);

	private native void derooijInsertDataPartBytes(long hContext, int rowIndex, byte[] partBytes, int inputOffset, int inputLen);
	
	private native byte[] derooijComputeColumnAndClearData(long hContext);

	private native void derooijClearData(long hContext);

	private native void derooijDelete(long hContext);

	public DeRooijJNIColumnProcessor(//
			PaillierPublicKey publicKey,
			Map<Integer, CipherText> queryElements,
			QueryInfo queryInfo,
			Map<String, String> config) {

		Validate.notNull(publicKey);
		Validate.notNull(queryElements);
		Validate.notNull(config);

		this.publicKey = publicKey;

		JNILoader.load();

		final int maxRowIndex = 1 << queryInfo.getHashBitSize();

		this.hContext = derooijNew(publicKey.getNSquared().toByteArray(), maxRowIndex);
		if (0L == this.hContext) {
			throw new NullPointerException("failed to allocate context from native code");
		}

		for (int rowIndex = 0; rowIndex < maxRowIndex; rowIndex++) {
			BigInteger element = ((PaillierCipherText) queryElements.get(rowIndex)).getValue();
			derooijSetQueryElement(this.hContext, rowIndex, element.toByteArray());
		}

		log.info("Instantiated DeRooijJNIColumnProcessor.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#insert(int, byte[])
	 */
	@Override
	public void insert(int rowIndex, byte[] input) {
		// TODO: review data to big int conversion
		insert(rowIndex, input, 0, input.length);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#compute()
	 */
	@Override
	public CipherText compute() {
		byte[] bytes = derooijComputeColumnAndClearData(hContext);
		return new PaillierCipherText(new BigInteger(1, bytes));
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#insert(int, byte[], int, int)
	 */
	@Override
	public void insert(int rowIndex, byte[] input, int inputOffset, int inputLen) {
		derooijInsertDataPartBytes(hContext, rowIndex, input, inputOffset, inputLen);		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#clear()
	 */
	@Override
	public void clear() {
		if (hContext != 0L) {
			derooijClearData(hContext);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		if (hContext != 0L) {
			derooijDelete(hContext);
		}
		super.finalize();
	}

}
