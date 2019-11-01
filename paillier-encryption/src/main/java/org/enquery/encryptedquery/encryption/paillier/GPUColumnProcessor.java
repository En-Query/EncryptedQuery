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

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GPUColumnProcessor implements ColumnProcessor {
	private final Logger log = LoggerFactory.getLogger(GPUColumnProcessor.class);

	private final long hQuery;
	private long hColproc;

	native long createColproc(long hQuery);

	native boolean colprocInsertChunk(long hColproc, int rowIndex, int chunk);

	native byte[] colprocComputeAndClear(long hColproc);

	native boolean colprocClear(long hColproc);

	native boolean colprocRemove(long hColproc);

	GPUColumnProcessor(long hQuery) {
		this.hQuery = hQuery;
		Validate.isTrue(hQuery != 0L);
		hColproc = createColproc(hQuery);
		Validate.isTrue(hColproc != 0L);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#insert(int, byte[])
	 */
	@Override
	public void insert(int rowIndex, byte[] input) {
		Validate.isTrue(hColproc != 0L);
		// TODO: review data to big int conversion
		BigInteger part = new BigInteger(1, input);
		colprocInsertChunk(hColproc, rowIndex, part.intValue());
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
	public CipherText computeAndClear() {
		Validate.isTrue(hColproc != 0L);
		final byte[] bytes = colprocComputeAndClear(hColproc);
		Validate.notNull(bytes, "Native call `colprocComputeAndClear` returned null.");
		return new PaillierCipherText(new BigInteger(1, bytes));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#clear()
	 */
	@Override
	public void clear() {
		if (hColproc != 0L) {
			colprocClear(hColproc);
		}
	}

	public void remove() {
		if (hColproc != 0L) {
			colprocRemove(hColproc);
			hColproc = 0L;
		}
	}

	@Override
	protected void finalize() throws Throwable {
		remove();
	}
}
