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
package org.enquery.encryptedquery.encryption.seal;

// this class is used to interface with the JNI using static methods only accessible to classes in
// this package
public final class SEALCpp {
	private SEALCpp() {}

	// create a keygen object and returns a pointer to it
	protected static native long initkeygen(long contextptr);

	// get the pk from the keygen object
	protected static native byte[] getpk(long keyptr);

	// get the rk from the keygen object
	protected static native byte[] getrk(long keyptr);

	// get the sk from the keygen object
	protected static native byte[] getsk(long keyptr);

	// get the relinearization key from the keygen object
	// private static native byte[] getrelinkey(long keyptr);
	// destroy the keygen object
	protected static native void destroykeygen(long keyptr);

	// add two ciphertexts, this method should not be used if you want to do many summations
	protected static native byte[] cipheradd(long conptr, byte[] lhs, byte[] rhs);

	// multiply a ciphertext by a plaintext, this method should not be used if you want to do many
	// multiplications
	protected static native byte[] plainmul(long conptr, byte[] cipher, byte[] plain);

	// creates an encryptor object and returns a pointer to it
	protected static native long createencryptor(long conptr, byte[] pk);

	// destroys an encryptor object
	protected static native void destroyencryptor(long encptr);

	// returns an encryption of zero as a byte array
	protected static native byte[] encryptzero(long conptr, long encptr, int parameternum);

	// return an encryption of the desired selector
	protected static native byte[] encryptsel(long encptr, long conptr, int[] indicies, int numsel);

	// creates a decryptor object and returns a pointer to it
	protected static native long createdecryptor(long conptr, byte[] sk);

	// destroys a decryptor object
	protected static native void destroydecryptor(long decptr);

	// decrypts a ciphertext and returns the underlying plaintext as bytes
	protected static native byte[] decrypt(long conptr, long decptr, byte[] cipher);

	// currently does a multiply accumulate operation on the given ciphertext and stores it in the
	// accumulator
	// if you are trying to do processing after inserting all database entries you should change
	// what this does/replace it
	protected native static void insert(int index1, int index2, byte[] bytes, long contextptr, long ciphptr, long accptr, long nttptr, long invnttptr, long numsel);

	// returns the accumulator. As above if you want to do computation after inserting all database
	// entries, change this
	protected native static byte[] getaccum(long conptr, byte[] publicKey, long accptr, long rkptr, long queryptr, long bitlen);

	// resets the accumulator object
	protected native static void resetaccum(long conptr, long accptr, int bitlen);

	// creates an evaluator object and returns a pointer to it
	protected native static long createevaluator(long conptr);

	// destroys an evaluator object
	protected native static void destroyevaluator(long evalptr);

	// destroys the accumulator
	protected native static void destroyaccum(long accumptr, int bitlen);

	// destroys the query
	protected native static void destroyquery(long queryptr);

	// creates the accumulator and returns a pointer to it
	protected native static long createaccum(long conptr, int bitlength);

	// destroys the context object
	protected native static void destroycontext(long conptr);

	// creates a query and returns a pointer to it
	protected native static long createquery(int numterms);

	// adds a query element to the query
	protected native static void addquery(long conptr, long queryptr, int index, byte[] cipher);

	// creates a context onject and returns a pointer to it
	protected native static long createcontext();

	// create a table for going from the ntt domain to the plaintext domain
	// and returns a pointer to the table
	protected native static long createinvntt(long conptr, long numsel);

	// create a table for going from the plaintext domain to the ntt crt domain
	// and returns a pointer to the table
	protected native static long createntt(long conptr, long numsel);

	// destroys ntt tables
	protected native static void destroyntt(long nttptr);

	// put data chunk in original domain
        protected native static byte[] extractplain(long conptr, long numsel, long chunksize, int index, byte[] plain);

	// create subquery list
	protected native static long createsubquery(long evalptr, long queryptr, long rkptr, int bitlen);

	// destroy subquery list
	protected native static void destroysubquery(long subqueryptr);

	// stores the relinearization keys in the memory accessable to c++
	protected native static long createrk(byte[] rk, long conptr);

	// destroys the rk
	protected native static void destroyrk(long rkptr);
}
