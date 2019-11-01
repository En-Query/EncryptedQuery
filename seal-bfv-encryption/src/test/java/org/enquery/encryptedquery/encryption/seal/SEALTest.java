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

import static org.junit.Assert.assertArrayEquals;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.PlainText;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SEALTest{

	private final Logger log = LoggerFactory.getLogger(SEALTest.class);
	private SEALCryptoScheme scheme;

	@Before
	public void prepare() throws Exception{
		log.info("SEAL-BFV: beginning prepare...");

		// this is again called in scheme.initialize(null) below
		//JNILoader.load();

		// construct scheme object
		scheme = new SEALCryptoScheme();

		// initialize contextptr
		scheme.initialize(null);

		log.info("SEAL-BFV: ending prepare...");
	}

	@After
	public void cleanup() throws Throwable{
		log.info("SEAL-BFV: beginning cleanup...");
		if (scheme != null) {
			scheme.close();
			scheme = null;
		}
		log.info("SEAL-BFV: ending cleanup...");
	}

	@Test
	public void happyPath() throws Exception{
		log.info("SEAL-BFV: beginning happyPath() unit test...");

		// construct a KeyPair object
		log.info("SEAL-BFV: generating KeyPair...");
		KeyPair keys = scheme.generateKeyPair();

		// construct a queryInfo object
		log.info("SEAL-BFV: generating queryInfo...");
		QueryInfo queryInfo = new QueryInfo();

		// include the publickey in the queryInfo object
		log.info("SEAL-BFV: setting publicKey in queryInfo...");
		queryInfo.setPublicKey(keys.getPublic());

		// construct a source of pseudorandom ints
		Random ranNum = new Random();

		// randomly choose and then set a hashBitSize in [10, 20]
		log.info("SEAL-BFV: (randomly) choosing and then setting (in queryInfo) hashBitSize...");
		queryInfo.setHashBitSize(10 + ranNum.nextInt(11));
		log.info("SEAL-BFV: queryInfo.getHashBitSize():              " + queryInfo.getHashBitSize());

		// round the chosen hashBitSize up to its nearest multiple of 4
		log.info("SEAL-BFV: rounding hashBitSize up to its nearest multiple of 4...");
		int roundedHashBitSize = queryInfo.getHashBitSize();
		if ((roundedHashBitSize % 4) != 0){
	  	  roundedHashBitSize = 4 * (((int)(roundedHashBitSize / 4)) + 1);
		}
		log.info("SEAL-BFV: roundedHashBitSize:                      " + roundedHashBitSize);

		// compute the number of rows in the database
        	int columnLength = 1 << queryInfo.getHashBitSize();

		// randomly choose and set the number of targeted selectors 
		// in [1, Min(8192, (1 << queryInfo.getHashBitSize()))]
		log.info("SEAL-BFV: (randomly) choosing and then setting (in queryInfo) numSelectors...");
		int maxNumSelectors = 8192;
		if ((1 << queryInfo.getHashBitSize()) < maxNumSelectors){
		  maxNumSelectors = 1 << queryInfo.getHashBitSize();
		}
		queryInfo.setNumSelectors(1 + ranNum.nextInt(maxNumSelectors));
		log.info("SEAL-BFV: queryInfo.getNumSelectors():             " + queryInfo.getNumSelectors());

		// generate, set and then view the plainQuery map, 
		// which consists precisely of those ordered pairs 
		// (Hash(T_v), v), where T_v is the v-th targeted selector;
		// Recall: T_v is, itself, a string, while v is an ordinal
		log.info("SEAL-BFV: Constructing plainQuery map...");
		Map<Integer, Integer> plainQuery = new TreeMap<>();
		for (int i = 0; i < (queryInfo.getNumSelectors()); i++){
		  Boolean uniqueHash = false;
		  while (!uniqueHash){
		    int ranHash = ranNum.nextInt(1 << queryInfo.getHashBitSize());
		    if (!(plainQuery.containsKey(ranHash))){
		      uniqueHash = true;
		      plainQuery.put(ranHash, i);
		    }
		  }
		}
		//log.info("SEAL-BFV: printing plainQuery map...");
		// view {(Hash(T_v), v) : 0 <= v < queryInfo.getNumSelectors()}
		//System.out.println(plainQuery);

		// for 0 <= a <= (sublen - 1), and for 0 <= p <= 3, 
		// queryElements(a + p * sublen) = an encryption of the 
		// plaintext polynomial got by inverse NTT transforming 
		// the plaintext element having unity in all NTT slots that 
		// correspond to targeted selectors T such that Hash(T) has 
		// the digit a in the p-th place of its development to the base sublen;
		// thus, the entire query consists precisely of the map 
		// queryElements together with the metadata queryInfo
		log.info("SEAL-BFV: Generating query vector...");
		Map<Integer, CipherText> queryElements = scheme.generateQueryVector(keys, queryInfo, plainQuery);

		// set the dataChunkSize in queryInfo
		int logNumSelectors = 0;
		while ((1 << logNumSelectors) < queryInfo.getNumSelectors()) {
			logNumSelectors++;
		}
		queryInfo.setDataChunkSize(1 << (2 + (13 - logNumSelectors)));
		log.info("SEAL-BFV: dataChunkSize set to " + queryInfo.getDataChunkSize());

		// the loadQuery method comes from the AbstractCryptoScheme class, 
		// which SEALCryptoScheme extends, and, which implements the CryptoScheme interface;
		// it furnishes a handle to the entire query, that is to say, 
		// to the queryInfo together with the queryElements
		log.info("SEAL-BFV: loading query...");
		byte[] queryHandle = scheme.loadQuery(queryInfo, queryElements);
		Validate.notNull(queryHandle);

		try {
			log.info("SEAL-BFV: Making column processor...");
			ColumnProcessor cp = scheme.makeColumnProcessor(queryHandle);

			// construct a source of pseudorandom bytes
			Random ranByte = new Random();

			// always will hold the current data chunk
			byte[] bytes = new byte[queryInfo.getDataChunkSize()];
			System.out.printf("responder getDataChunkSize: %d\n", queryInfo.getDataChunkSize());

			// used to save off data chunks corresponding to selectors
			byte[][] selBytes = new byte[queryInfo.getNumSelectors()][queryInfo.getDataChunkSize()];

			log.info("SEAL-BFV: Inserting data...");
			// for each possible hash value (i.e., database row)
			for (int i = 0; i < columnLength; i++){

			  // fill bytes with a randomly chosen data chunk
			  ranByte.nextBytes(bytes);

			  // store a copy of the data chunk if it 
			  // corresponds to a targeted selector
			  if (plainQuery.containsKey(i)){
			    selBytes[plainQuery.get(i)] = bytes.clone();
			  }
			
			  cp.insert(i, bytes);
			  
			  //if (i % 1000 == 0){
			  //  System.out.printf("cp inserted data for row %d\n", i);
			  //}
			}

			// compute the column sum
			log.info("SEAL-BFV: Computing the (encrypted) column sum...");
			CipherText cipher = cp.computeAndClear();

			// plainText.toBytes() is an (8*8192)-long byte array
			// it is a coefficient-wise little endian rep of plaintext poly
			// it is in poly domain, not in ntt domain, which is where the data chunks are seen
			log.info("SEAL-BFV: Decrypting the column sum...");
			PlainText plainText = scheme.decrypt(keys, cipher);

			
			//for (int i = 0; i< queryInfo.getNumSelectors(); i++){
			//  System.out.println(Arrays.toString(selBytes[i]));
			//  System.out.println(Arrays.toString(scheme.plainTextChunk(queryInfo, plainText, i)));
			//}

			// check for correctness
			log.info("SEAL-BFV: Checking for correctness...");
			for (int i = 0; i< queryInfo.getNumSelectors(); i++){
			  assertArrayEquals(selBytes[i], scheme.plainTextChunk(queryInfo, plainText, i));
			}

		} finally {
			log.info("SEAL-BFV: Unloading query...");
			scheme.unloadQuery(queryHandle);
		}
		log.info("SEAL-BFV: ending happyPath() unit test");
	}
}
