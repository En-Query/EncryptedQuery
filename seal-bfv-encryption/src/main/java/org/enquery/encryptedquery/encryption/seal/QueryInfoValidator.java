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

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QueryInfo;

/**
 * Validates the Query Info to comply with this implementation limitations.
 */
public class QueryInfoValidator {

	static void validate(QueryInfo queryInfo){

		Validate.notNull(queryInfo);

		// everything depends on the hashBitSize;
		// on the Java side of EncryptedQuery, it's stored as 
		// a 32-bit signed integer, so it isn't allowed to be 
		// greater than 31;
		// and, since we're going to effectively round it up to its 
		// nearest multiple of 4, it actually can't be any
		// greater than 28
		Validate.inclusiveBetween(
			1, 
			28, 
			queryInfo.getHashBitSize(), 
			"Hash size (in bits) must be >= 1 && <= 28. Current value is %d.", 
			queryInfo.getHashBitSize());

		// round hashBitSize up to its nearest multiple of 4
		int roundedHashBitSize = queryInfo.getHashBitSize();
		if ((roundedHashBitSize % 4) != 0){
		  roundedHashBitSize = 4 * (((int)(roundedHashBitSize / 4)) + 1);
		}

		// given the hashBitSize, the number of selectors can't 
		// be greater than 2^{hashBitSize}, since distinct selectors	
		// are assumed (at this point in the protocol) to correspond
		// to distinct hashes 
		int maxNumSelectors = 8192;
		if ((1 << queryInfo.getHashBitSize()) < maxNumSelectors){
		  maxNumSelectors = 1 << queryInfo.getHashBitSize();
		}
		Validate.inclusiveBetween(
			1, 
			maxNumSelectors, 
			queryInfo.getNumSelectors(), 
			"Given the chosen hash size (i.e., %d bits), the number of selectors must be >= 1 && <= %d. Current value is %d.", 
			queryInfo.getHashBitSize(), 
			maxNumSelectors, 
			queryInfo.getNumSelectors());
	}
}
