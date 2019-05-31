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
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.PlainText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is the Java interface to a GPU-accelerated decryptor for Paillier ciphertexts.
 */
public class GPUDecryptor implements AutoCloseable {

	private final Logger log = LoggerFactory.getLogger(GPUDecryptor.class);

	/*
	 * The native library will process this many ciphertexts at a time. Best when this is a multiple
	 * of the library's internal batch size. Doesn't need to be too large.
	 */
	public static final int BATCH_SIZE = 4096;

	private PaillierPrivateKey priv;
	private int plainTextByteSize;
	private int cipherTextByteSize;
	private byte[] zeros;

	private String gpuLibDecryptorPath;
	private long handle;

	private native long createContext(byte[] pBytes, byte[] qBytes, byte[] p1Bytes, byte[] q1Bytes);

	private native boolean destroyContext(long handle);

	private native boolean decryptBatch(long handle, ByteBuffer iobuf, int batchSize);

	public GPUDecryptor(PrivateKey priv, Map<String, String> config) {
		// get private key parameters
		this.priv = PaillierPrivateKey.from(priv);
		this.plainTextByteSize = (this.priv.getModulusBitSize() + 7) / 8;
		this.cipherTextByteSize = 2 * this.plainTextByteSize;
		this.zeros = new byte[cipherTextByteSize];
		log.info("plainTextByteSize = " + this.plainTextByteSize);
		log.info("cipherTextByteSize = " + this.cipherTextByteSize);
		BigInteger p = this.priv.getP();
		BigInteger q = this.priv.getQ();
		BigInteger p1 = this.priv.getPMaxExponent();
		BigInteger q1 = this.priv.getQMaxExponent();

		// call native code to create a decryption context
		log.info("calling createContext()");
		this.handle = createContext(p.toByteArray(), q.toByteArray(), p1.toByteArray(), q1.toByteArray());
		log.info("GPU decryptor handle:  {}", this.handle);
		Validate.isTrue(this.handle != 0);
	}

	/*
	 * Passes up to BATCH_SIZE ciphertexts to the native code at a time. The data is passed via an
	 * NIO direct buffer. Each successive ciphertext is represented as C bytes in the buffer
	 * starting at the beginning, where C is the ciphertext size in bytes. The native library should
	 * write the resulting plaintexts as successive chunks of P bytes at the start of the same
	 * buffer, where P is the plaintext byte size, overwriting some of the original input data. The
	 * byte order is big endian.
	 */
	public List<PlainText> decrypt(List<CipherText> cc) {
		List<PlainText> results = new ArrayList<>();
		ByteBuffer iobuf = ByteBuffer.allocateDirect(BATCH_SIZE * cipherTextByteSize);
		// ValidateTrue(cipherTextByteSize >= plainTextByteSize);
		for (int from = 0; from < cc.size(); from += BATCH_SIZE) {
			int to = Math.min(from + BATCH_SIZE, cc.size());
			int batchSize = to - from;
			List<CipherText> batch = cc.subList(from, to);
			iobuf.clear();
			serializeCipherTexts(iobuf, batch);
			boolean success = decryptBatch(handle, iobuf, batchSize);
			Validate.isTrue(success, "decryption failed");
			iobuf.flip();
			deserializePlainTexts(results, iobuf, batchSize);
		}
		return results;
	}

	// assumes iobuf is at the beginning in write mode
	private void serializeCipherTexts(ByteBuffer iobuf, List<CipherText> sublist) {
		for (CipherText c : sublist) {
			PaillierCipherText ct = PaillierCipherText.from(c);
			byte[] ctBytes = ct.getValue().toByteArray();
			int padding, offset, length;
			if (ctBytes.length > cipherTextByteSize) {
				padding = 0;
				offset = ctBytes.length - cipherTextByteSize;
				length = cipherTextByteSize;
			} else {
				padding = cipherTextByteSize - ctBytes.length;
				offset = 0;
				length = ctBytes.length;
			}
			if (padding > 0) {
				iobuf.put(zeros, 0, padding);
			}
			if (length > 0) {
				iobuf.put(ctBytes, offset, length);
			}
		}
	}

	// assumes iobuf is at the beginning in read mode
	private void deserializePlainTexts(List<PlainText> results, ByteBuffer iobuf, int batchSize) {
		byte[] ptBytes = new byte[plainTextByteSize];
		for (int i = 0; i < batchSize; i++) {
			iobuf.get(ptBytes);
			PlainText pt = new PaillierPlainText(new BigInteger(1, ptBytes));
			results.add(pt);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		log.info("finalize() called");
		close();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.AutoCloseable#close()
	 */
	@Override
	public void close() throws Exception {
		log.info("close() called");
		if (handle != 0L) {
			Validate.isTrue(destroyContext(handle), "Error destroying GPUDecryptor context.");
			handle = 0L;
		}

	}

}
