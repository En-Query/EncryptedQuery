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
package org.enquery.encryptedquery.querier;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.enquery.encryptedquery.core.CoreConfigurationProperties;
import org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptQuery;
import org.enquery.encryptedquery.utils.PIRException;

/**
 * Properties constants for the Querier
 */
public interface QuerierProperties extends CoreConfigurationProperties {

	String QUERYTYPE = "querier.queryType";
	String NUMTHREADS = "querier.numThreads";
	String METHOD = "query.encryption.method";

	String EMBEDSELECTOR = "querier.embedSelector";
	String EMBEDQUERYSCHEMA = "pir.embedQuerySchema";
	String ENCQUERYMETHOD = "pir.encryptQueryMethod";

	List<String> PROPSLIST = Arrays.asList(
			QUERYTYPE,
			NUMTHREADS,
			EMBEDQUERYSCHEMA,
			HASH_BIT_SIZE,
			DATA_PARTITION_BIT_SIZE,
			PAILLIER_BIT_SIZE,
			BIT_SET,
			CERTAINTY,
			EMBEDSELECTOR,
			PAILLIER_SECURE_RANDOM_ALG,
			ENCQUERYMETHOD,
			PAILLIER_SECURE_RANDOM_PROVIDER);

	default void validateQuerierEncryptionProperties(Properties properties) throws PIRException {
		// Parse encryption properties
		if (!properties.containsKey(QUERYTYPE)) {
			throw new PIRException("Encryption properties not valid. Missing property " + QUERYTYPE);
		}

		if (!properties.containsKey(HASH_BIT_SIZE)) {
			throw new PIRException("Encryption properties not valid. For action='encrypt': Must have the option " + HASH_BIT_SIZE);
		}

		if (!properties.containsKey(DATA_PARTITION_BIT_SIZE)) {
			throw new PIRException("Encryption properties not valid. For action='encrypt': Must have the option " + DATA_PARTITION_BIT_SIZE);
		}

		if (!properties.containsKey(PAILLIER_BIT_SIZE)) {
			throw new PIRException("Encryption properties not valid. For action='encrypt': Must have the option " + PAILLIER_BIT_SIZE);
		}

		if (!properties.containsKey(CERTAINTY)) {
			throw new PIRException("Encryption properties not valid. For action='encrypt': Must have the option " + CERTAINTY);
		}

		if (properties.containsKey(ENCQUERYMETHOD)) {
			String method = properties.getProperty(ENCQUERYMETHOD);
			if (!EncryptQuery.METHODS.contains(method)) {
				throw new PIRException("Encryption properties not valid. Unrecognized 'pir.encryptQueryMethod' property with value '" + method + "'.");
			}
		}

		int dataPartitionBitSize = Integer.parseInt(properties.getProperty(DATA_PARTITION_BIT_SIZE));
		if (dataPartitionBitSize > 3072) {
			throw new PIRException("Encryption properties not valid. " + DATA_PARTITION_BIT_SIZE + " must be less than 3072.");
		}

		if ((dataPartitionBitSize % 8) != 0) {
			throw new PIRException("Encryption properties not valid. " + DATA_PARTITION_BIT_SIZE + "  must be a multiple of 8.");
		}
	}

}
