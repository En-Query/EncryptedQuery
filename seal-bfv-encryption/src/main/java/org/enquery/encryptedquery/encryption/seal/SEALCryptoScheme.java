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

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.PlainText;
import org.enquery.encryptedquery.encryption.impl.AbstractCryptoScheme;
import org.enquery.encryptedquery.encryption.impl.QueryData;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(property = "name=Seal-BFV")
public class SEALCryptoScheme extends AbstractCryptoScheme implements CryptoScheme {

	private final Logger log = LoggerFactory.getLogger(SEALCryptoScheme.class);
	static final String ALGORITHM = "SEAL-BFV";
	private static String DESCRIPTION = "Implements Lattice CryptoScheme using Brakerski-Fan-Vercauteren (SEAL).";
	private long contextptr;
	// private Map<String, String> config;

	@Override
	@Activate
	public void initialize(Map<String, String> map) throws Exception {
		JNILoader.load();

		contextptr = SEALCpp.createcontext();
		Validate.isTrue(contextptr != 0L);

		// config = new TreeMap<>();
		// if (map != null) {
		// config.putAll(map);
		// }
	}

	@Deactivate
	@Override
	public synchronized void close() {
		if (contextptr != 0L) {
			System.out.printf("    scheme.close(): contextptr is not 0L; destroying context object...\n");
			SEALCpp.destroycontext(contextptr);
			contextptr = 0L;
		} else {
			System.out.printf("    scheme.close(): contextptr already is 0L; no need to destroy\n");
		}
		super.close();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Iterable<Map.Entry<String, String>> configurationEntries() {
		return Collections.EMPTY_MAP.entrySet();
	}

	@Override
	public String name() {
		return ALGORITHM;
	}

	@Override
	public String description() {
		return DESCRIPTION;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#makeColumnProcessor(byte[])
	 */
	@Override
	public ColumnProcessor makeColumnProcessor(byte[] handle) {
		Validate.notNull(handle);
		final ExtQueryData queryData = (ExtQueryData) findQueryFromHandle(handle);
		return new SEALColumnProcessor(contextptr,
				queryData.getQueryInfo(),
				queryData.nativeQueryHandle,
				queryData.nativeSubqueryHandle);
	}

	@Override
	public KeyPair generateKeyPair() {
		/****************************************
		 * \ Future Implementation advice: I would recommend adding a native function that generates
		 * the relinearization keys here and uses it in the construction of the public key object \
		 ****************************************/
		long keyptr = SEALCpp.initkeygen(contextptr);
		Validate.isTrue(keyptr != 0L);
		try {
			PublicKey pk = new SEALPublicKey(SEALCpp.getpk(keyptr), SEALCpp.getrk(keyptr));
			PrivateKey sk = new SEALPrivateKey(SEALCpp.getsk(keyptr));
			return new KeyPair(pk, sk);
		} finally {
			if (keyptr != 0L) {
				System.out.printf("    scheme.generateKeyPair(): keyptr is not 0L; destroying keygen object...\n");
				SEALCpp.destroykeygen(keyptr);
				keyptr = 0L;
			} else {
				System.out.printf("    scheme.generateKeyPair(): keyptr already is 0L; no need to destroy\n");
			}
		}
	}

	@Override
	public PrivateKey privateKeyFromBytes(byte[] bytes) {
		Validate.notNull(bytes);
		return new SEALPrivateKey(bytes);
	}

	@Override
	public PublicKey publicKeyFromBytes(byte[] bytes) {
		Validate.notNull(bytes);
		return new SEALPublicKey(bytes);
	}

	@Override
	public CipherText cipherTextFromBytes(byte[] bytes) {
		Validate.notNull(bytes);
		return new SEALCipherText(bytes);
	}

	@Override
	public CipherText encryptionOfZero(PublicKey publicKey) {
		Validate.notNull(publicKey);
		long encptr = SEALCpp.createencryptor(contextptr, ((SEALPublicKey) publicKey).getEncoded());
		Validate.isTrue(encptr != 0L);
		try {
			return new SEALCipherText(SEALCpp.encryptzero(contextptr, encptr, 3));
		} finally {
			if (encptr != 0L) {
				System.out.printf("    scheme.encryptionOfZero(): encptr is not 0L; destroying encryptor object...\n");
				SEALCpp.destroyencryptor(encptr);
				encptr = 0L;
			} else {
				System.out.printf("    scheme.encryptionOfZero(): encptr already is 0L; no need to destroy\n");
			}
		}
	}

	@Override
	public PlainText decrypt(KeyPair keyPair, CipherText cipherText) {
		Validate.notNull(keyPair);
		Validate.notNull(cipherText);

		PrivateKey sk = keyPair.getPrivate();
		byte[] seck = sk.getEncoded();
		long decptr = SEALCpp.createdecryptor(contextptr, seck);
		Validate.isTrue(decptr != 0L);
		// System.out.printf("cipherText.toBytes().length: %d\n", cipherText.toBytes().length);
		try {
			return new SEALPlainText(SEALCpp.decrypt(contextptr, decptr, cipherText.toBytes()));
		} finally {
			if (decptr != 0L) {
				System.out.printf("    scheme.decrypt(): decptr is not 0L; destroying decryptor object...\n");
				SEALCpp.destroydecryptor(decptr);
				decptr = 0L;
			} else {
				System.out.printf("    scheme.decrypt(): decptr already is 0L; no need to destroy\n");
			}
		}
	}

	@Override
	public List<PlainText> decrypt(KeyPair keyPair, List<CipherText> list) {
		Validate.notNull(keyPair);
		Validate.notNull(list);

		PrivateKey sk = keyPair.getPrivate();
		long decptr = SEALCpp.createdecryptor(contextptr, sk.getEncoded());
		Validate.isTrue(decptr != 0L);
		try {
			ArrayList<PlainText> pt = new ArrayList<>();
			for (CipherText c : list) {
				pt.add(new SEALPlainText(SEALCpp.decrypt(contextptr, decptr, c.toBytes())));
			}
			return pt;
		} finally {
			if (decptr != 0L) {
				System.out.printf("    scheme.decrypt(): decptr is not 0L; destroying decryptor object...\n");
				SEALCpp.destroydecryptor(decptr);
				decptr = 0L;
			} else {
				System.out.printf("    scheme.decrypt(): decptr already is 0L; no need to destroy\n");
			}
		}
	}

	@Override
	public Stream<PlainText> decrypt(KeyPair keyPair, Stream<CipherText> stream) {
		Validate.notNull(keyPair);
		Validate.notNull(stream);

		return decrypt(keyPair, stream.collect(Collectors.toList())).stream();
	}

	@Override
	protected QueryData makeQueryData() {
		return new ExtQueryData();
	}

	/************************************************************************************************************************************/
	// Note: The parameter map, which takes Integers to Integers,
	// is reall plainquery; it consists precisely of those ordered pairs
	// (Hash(T_v), v), where T_v is the v-th targeted selector;
	// Recall: T_v is, itself, a string, while v is an ordinal
	@Override
	public Map<Integer, CipherText> generateQueryVector(KeyPair keyPair, QueryInfo queryInfo, Map<Integer, Integer> map) {
		Validate.notNull(keyPair);
		Validate.notNull(queryInfo);
		Validate.notNull(map);
		QueryInfoValidator.validate(queryInfo);

		/************************************************
		 * \ Future implementation advice: If you are using multiple lists of selectors you need to
		 * change something here. I would recommend concatenating the lists onto each other. \
		 ***********************************************/

		// for 0 <= a <= (sublen - 1), and for 0 <= p <= 3,
		// toreturn(a + p * sublen) = an encryption of the
		// plaintext polynomial got by inverse NTT transforming
		// the plaintext having unity in all NTT slots corresponding
		// targeted selectors T such that Hash(T) has digit a in the
		// p-th place of its development to the base sublen;
		// thus, aside from some metadata, toreturn is essentially
		// the query itself
		Map<Integer, CipherText> toreturn = new TreeMap<>();

		// silently round the hash size (in bits) up to its nearest
		// multiple of 4;
		// effectively, this injects the user-specified space of hashes
		// into a larger space having size divisible by 4, so that
		// hashes may be evenly split into four parts
		int roundedHashBitSize = queryInfo.getHashBitSize();
		if ((roundedHashBitSize % 4) != 0) {
			roundedHashBitSize = 4 * ((roundedHashBitSize / 4) + 1);
		}

		// compute the number of ciphertexts within
		// a single subquery (i.e., quarter-query);
		// TODO: sublen |--> quarterQueryLength
		int sublen = 1 << (roundedHashBitSize / 4);

		// compute the number of ciphertexts within a single query,
		// given that a query consists of 4 quarter-queries;
		// TODO: hashsize |--> fullQueryLength
		long hashsize = 4 * sublen;

		// the maximal (and optimal) data chunk size (in bytes) is
		// 4 * (8192 / roundedNumSelectors), where roundedNumSelectors
		// is got by rounding the number of selectors
		// up to its nearest power of 2; note that the factor of 4 in the
		// foregoing expression results from the fact that each plaintext
		// coefficient holds 4 bytes of data
		int logNumSelectors = 0;
		while ((1 << logNumSelectors) < queryInfo.getNumSelectors()) {
			logNumSelectors++;
		}
		//queryInfo.setDataChunkSize(1 << (2 + (13 - logNumSelectors)));

		// silently round the number of selectors up to its
		// nearest power of 2, so that the number of
		// plaintext coefficients per selector is a divisor
		// of the total poly degree (i.e., 8192)
		int numsel = 1 << logNumSelectors;

		// the byte array data[] will hold, in turn, a serialization
		// of an encryption of each of the (4 * sublen)-many plaintexts
		// underlying the ciphertexts constituting the query
		byte[] data;

		// create an encryptor, and then check it!
		//final long encryptr = SEALCpp.createencryptor(contextptr, keyPair.getPublic().getEncoded());
		long encryptr = SEALCpp.createencryptor(contextptr, keyPair.getPublic().getEncoded());
		Validate.isTrue(encryptr != 0L);

		// Would you mind keeping it down?
		// We're trying to fashion a query here...
		try {
			// Hash(T_{value}) = a_0 * sublen^0 + a_1 * sublen^1 + a_2 * sublen^2 + a_3 * sublen^3;
			// D = {0, ..., sublen - 1}, the set of digits to the base sublen
			// Domain(subquery) = (D + (0* sublen)) \cup (D + (1 * sublen))
			// \cup (D + (2 * sublen)) \cup (D + (3 * sublen));
			// Domain(subquery) = {0, ..., ((4 * sublen) - 1)};
			// at the finish of the following for loop,
			// for each place p \in {0, 1, 2, 3} and each digit a \in D,
			// subquery(a + p * sublen) = a list of those values v
			// for which the targeted selector T_v
			// has digit a in the p-th place in the development
			// of Hash(T_v) to the base sublen;
			// therefore, subquery(a + p * sublen) is the empty list, if,
			// and only if, no targeted selector T is such that
			// the development of Hash(T) to the base sublen
			// has the digit a in its p-th place;
			// TODO: subquery |--> digitToSelectorsMap
			Map<Integer, List<Integer>> subquery = new TreeMap<>();

			// for each targeted selector T_v (which is a string),
			// write entry_{T_v} = (Hash(T_v), v) = (key, value)
			// System.out.printf("Creating query map...\n");
			for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
				Integer key = entry.getKey(); // Hash(T_v)
				Integer value = entry.getValue(); // v
				// initialize tempkey to the full digest key = Hash(T_v)
				int tempkey = key.intValue();
				// for each of the four base-sublen digits in key = Hash(T_v)
				for (int i = 0; i < 4; i++) {
					// moved this shift to the end of the iteration:
					// tempkey /= sublen;

					// shift the i-th base-sublen digit (tempkey % sublen) in key
					// to the i-th multiple of sublen in Domain(subquery)
					int loc = (tempkey % sublen) + i * sublen;
					// if subquery is already defined at loc,
					// that is to say, if we've already found at least one
					// targeted selector having (tempkey % sublen) as the
					// i-th base-sublen digit in its hash,
					if (subquery.containsKey(loc)) {
						// then "add" value to the list subquery.get(loc),
						// that is to say, include the current selector's
						// index, or value, in the (non-empty) list of
						// indices, or values, corresponding to other selectors
						// such selectors
						subquery.get(loc).add(value);
					} else {
						// otherwise, define subquery at loc to be the single item list <v>
						List<Integer> toinsert = new ArrayList<>();
						toinsert.add(value);
						subquery.put(loc, toinsert);
					}
					// shift tempkey to the right by one place to the base sublen
					tempkey /= sublen;
				}
			}
			// System.out.printf("Creating query elements...\n");
			// for each i = a + p * sublen in Domain(subquery),
			for (int i = 0; i < hashsize; i++) {
				// if subquery(i = a + p * sublen) is empty,
				if (!subquery.containsKey(i)) {
					// then each NTT slot of each targeted selector should be zero
					// in the (a + p * sublen)-th element of the query,
					// so this element should be an encryption of the zero plaintext polynomial
					data = SEALCpp.encryptzero(contextptr, encryptr, 0);
				} else {
					// otherwise, the list subquery(a + p * sublen)
					// is non-empty, and the NTT slots corresponding
					// to each of the values in this list should be
					// set to unity, while all other NTT slots
					// should be set to zero;
					// get the values
					List<Integer> arr = subquery.get(i);
					// sort them inplace
					Collections.sort(arr);
					// convert ordered list to an array of integers,
					// so that, for example, toinsert[0] = the least value listed in arr
					int[] toinsert = arr.stream().mapToInt(Integer::valueOf).toArray();
					// pass the characteristic values in toinsert to encryptsel();
					// this method returns a serialization of an encryption of the plaintext
					// formed (in the NTT domain) by initializing to zero all NTT coeffs,
					// and then replacing with unity all those zeros corresponding to values
					// in toinsert
					data = SEALCpp.encryptsel(encryptr, contextptr, toinsert, numsel);
				}
				toreturn.put(i, new SEALCipherText(data));
				// System.out.printf(" created query element %d out of %d\n", i, hashsize);
			}
			return toreturn;
		} finally {
			if (encryptr != 0L) {
				System.out.printf("    scheme.generateQueryVector(): encryptr is not 0L; destroying encryptor object...\n");
				SEALCpp.destroyencryptor(encryptr);
				encryptr = 0L;
			} else {
				System.out.printf("    scheme.generateQueryVector(): encryptr already is 0L; no need to destroy\n");
			}
		}
	}

	/*************************************************************************************************************************************/

	@Override
	public CipherText computeCipherAdd(PublicKey publicKey, CipherText cipherText, CipherText cipherText1) {
		Validate.notNull(publicKey);
		Validate.notNull(cipherText);
		Validate.notNull(cipherText1);

		return new SEALCipherText(SEALCpp.cipheradd(contextptr, cipherText.toBytes(), cipherText1.toBytes()));
	}

	@Override
	public CipherText computeCipherPlainMultiply(PublicKey publicKey, CipherText cipherText, byte[] bytes) {
		Validate.notNull(publicKey);
		Validate.notNull(cipherText);
		Validate.notNull(bytes);

		return new SEALCipherText(SEALCpp.plainmul(contextptr, cipherText.toBytes(), bytes));
	}

	@Override
	public byte[] plainTextChunk(QueryInfo queryInfo, PlainText plainText, int i) {
		Validate.notNull(queryInfo);
		Validate.notNull(plainText);

		// silently round the number of selectors up
		// to its nearest power of 2
		int numsel = queryInfo.getNumSelectors();
		int lognumsel = 0;
		while ((1 << lognumsel) < numsel) {
			lognumsel++;
		}
		numsel = 1 << lognumsel;

		int chunksize = queryInfo.getDataChunkSize();
		// plainText.toBytes() is an 8*8192 long byte array representing
		// a polynomial domain plaintext with coefficients given in
		// little endian (byte) order;
		// extractplain() transforms the 8*8192 long byte array plainText.toBytes()
		// to its corresponding 8192 long uint64_t array, and then
		// forward ntts this result so that it can extract the slots corresponding
		// to the i-th selector
		return SEALCpp.extractplain(contextptr, numsel, chunksize, i, plainText.toBytes());
	}

	@Override
	protected void initQueryData(QueryData queryData, QueryInfo queryInfo, Map<Integer, CipherText> queryElements) {
		Validate.notNull(queryData);
		Validate.notNull(queryInfo);
		Validate.notNull(queryElements);

		super.initQueryData(queryData, queryInfo, queryElements);

		SEALPublicKey pk = (SEALPublicKey) queryInfo.getPublicKey();

		// silently round
		int roundedHashBitSize = queryInfo.getHashBitSize();
		if ((roundedHashBitSize % 4) != 0) {
			roundedHashBitSize = 4 * ((roundedHashBitSize / 4) + 1);
		}
		int bitlen = roundedHashBitSize;

		// compute the number of ciphertexts in a single query,
		// which is 4 * 2^(bitlen/4)
		int querylen = 4 << (bitlen / 4);

		// find a pointer to a sequence of querylen-many
		// ciphertexts, which shall ultimately correspond
		// to the elements of the query
		long querloc = SEALCpp.createquery(querylen);
		Validate.isTrue(querloc != 0L);

		// System.out.printf("Initializing query...\n");

		try {
			// find a pointer to a RelinKeys object
			long relinptr = SEALCpp.createrk(pk.getrk(), contextptr);
			Validate.isTrue(relinptr != 0L);

			try {
				for (int i = 0; i < querylen; i++) {
					SEALCpp.addquery(contextptr, querloc, i, queryElements.get(i).toBytes());
					// System.out.printf(" added query element %d of %d\n", i, querylen);
				}

				// find a pointer to an evaluator object
				long evalptr = SEALCpp.createevaluator(contextptr);
				Validate.isTrue(evalptr != 0L);

				try {
					// System.out.printf(" Creating subquery...\n");
					long subqloc = SEALCpp.createsubquery(evalptr, querloc, relinptr, bitlen);

					// System.out.printf(" pointing at the query elements...\n");
					// point to the 4 * 2^(bitlen/4) many query elements
					((ExtQueryData) queryData).nativeQueryHandle = querloc;

					// System.out.printf(" pointing at the subquery elements...\n");
					// point to the 2^(bitlen/2) many ntt-cached subquery elements
					((ExtQueryData) queryData).nativeSubqueryHandle = subqloc;

				} finally {
					if (evalptr != 0L) {
						System.out.printf("    scheme.initQueryData(): evalptr is not 0L; destroying evaluator object...\n");
						SEALCpp.destroyevaluator(evalptr);
						evalptr = 0L;
					} else {
						System.out.printf("    scheme.initQueryData(): evalptr already is 0L; no need to destroy\n");
					}
				}
			} finally {
				if (relinptr != 0L) {
					System.out.printf("    scheme.initQueryData(): relinptr is not 0L; destroying relinearization keys object...\n");
					SEALCpp.destroyrk(relinptr);
					relinptr = 0L;
				} else {
					System.out.printf("    scheme.initQueryData(): relinptr already is 0L; no need to destroy\n");
				}
			}
		} catch (Exception e) {
			if (querloc != 0L) {
				System.out.printf("    scheme.initQueryData(): querloc is not 0L; destroying query object...\n");
				SEALCpp.destroyquery(querloc);
				querloc = 0L;
			} else {
				System.out.printf("    scheme.initQueryData(): querloc already is 0L; no need to destroy\n");
			}
			throw e;
		}
	}

	@Override
	protected void cleanupQuery(QueryData queryData) {
		super.cleanupQuery(queryData);

		Validate.notNull(queryData);

		if (((ExtQueryData) queryData).nativeSubqueryHandle != 0L) {
			System.out.printf("    scheme.cleanupQuery(): subqueryptr is not 0L; destroying subquery object...\n");
			SEALCpp.destroysubquery(((ExtQueryData) queryData).nativeSubqueryHandle);
			((ExtQueryData) queryData).nativeSubqueryHandle = 0L;
		} else {
			System.out.printf("    scheme.cleanupQuery(): subqueryptr already is 0L; no need to destroy\n");
		}

		if (((ExtQueryData) queryData).nativeQueryHandle != 0L) {
			System.out.printf("    scheme.cleanupQuery(): queryptr is not 0L; destroying query object...\n");
			SEALCpp.destroyquery(((ExtQueryData) queryData).nativeQueryHandle);
			((ExtQueryData) queryData).nativeQueryHandle = 0L;
		} else {
			System.out.printf("    scheme.cleanupQuery(): queryptr already is 0L; no need to destroy\n");
		}

		log.info("SEAL-BFV: ending cleanupQuery...");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#maximumChunkSize(org.enquery.
	 * encryptedquery.data.QuerySchema, int)
	 */
	@Override
	public int maximumChunkSize(QuerySchema querySchema, int numberOfSelectors) {
		// the maximal (and optimal) data chunk size (in bytes) is
		// 4 * (8192 / roundedNumSelectors), where roundedNumSelectors
		// is got by rounding the number of selectors
		// up to its nearest power of 2; note that the factor of 4 in the
		// foregoing expression results from the fact that each plaintext
		// coefficient holds 4 bytes of data
		int logNumSelectors = 0;
		while ((1 << logNumSelectors) < numberOfSelectors) {
			logNumSelectors++;
		}
		// return 1 << (2 + 13 - logNumSelectors);
		return 4 * (8192 / (1 << logNumSelectors));
		// return 4 * (4096 / (1 << logNumSelectors));
	}

}
