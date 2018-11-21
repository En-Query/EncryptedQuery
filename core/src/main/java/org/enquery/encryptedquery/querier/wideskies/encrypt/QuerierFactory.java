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
package org.enquery.encryptedquery.querier.wideskies.encrypt;

import java.math.BigInteger;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.encryption.PaillierEncryption;
import org.enquery.encryptedquery.querier.QuerierProperties;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.utils.PIRException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles encrypting a query and constructing a {@link Querier} given a
 * {@link EncryptionPropertiesBuilder}.
 *
 */
@Component(service = QuerierFactory.class)
public class QuerierFactory implements QuerierProperties {

	private final Logger logger = LoggerFactory.getLogger(QuerierFactory.class);

	@Reference
	private ModPowAbstraction modPowAbstraction;
	@Reference
	private PaillierEncryption paillierEncryption;
	@Reference
	private EncryptQuery encryptQuery;

	public QuerierFactory() {}

	public QuerierFactory(ModPowAbstraction modPowAbstraction, PaillierEncryption paillierEncryption, EncryptQuery encryptQuery) {
		Validate.notNull(modPowAbstraction);
		Validate.notNull(paillierEncryption);
		Validate.notNull(encryptQuery);

		this.modPowAbstraction = modPowAbstraction;
		this.paillierEncryption = paillierEncryption;
		this.encryptQuery = encryptQuery;
	}

	public ModPowAbstraction getModPowAbstraction() {
		return modPowAbstraction;
	}

	public void setModPowAbstraction(ModPowAbstraction modPowAbstraction) {
		this.modPowAbstraction = modPowAbstraction;
	}

	/**
	 * Generates a {@link Querier} containing the encrypted query.
	 *
	 * @param queryIdentifier A unique identifier for this query.
	 * @param selectors A list of query selectors.
	 * @param properties A list of properties specifying EncryptedQuery configuration options. Use
	 *        {@link EncryptionPropertiesBuilder} to construct this object.
	 * @return The encrypted query.
	 * @throws PIRException If the provided parameters violate one of the constraints of the
	 *         EncryptedQuery algorithm.
	 * @throws InterruptedException If the encryption process is interrupted.
	 */
	public Querier createQuerier(QuerySchema querySchema, UUID queryIdentifier, List<String> selectors, Properties properties) throws PIRException, InterruptedException {

		Validate.notNull(querySchema);
		Validate.notNull(queryIdentifier);
		Validate.notNull(selectors);
		Validate.isTrue(selectors.size() > 0);
		Validate.noNullElements(selectors);
		Validate.notNull(properties);
		querySchema.validate();
		validateQuerierEncryptionProperties(properties);
		Validate.notNull(paillierEncryption);

		int numSelectors = selectors.size();
		int hashBitSize = Integer.parseInt(properties.getProperty(HASH_BIT_SIZE, "12"));
		int bitSet = Integer.parseInt(properties.getProperty(BIT_SET, "32"));
		int dataPartitionBitSize = Integer.parseInt(properties.getProperty(DATA_PARTITION_BIT_SIZE, "8"));
		int paillierBitSize = Integer.parseInt(properties.getProperty(PAILLIER_BIT_SIZE, "3072"));
		int certainty = Integer.parseInt(properties.getProperty(CERTAINTY, "128"));
		boolean embedSelector = Boolean.valueOf(properties.getProperty(EMBEDSELECTOR, "true"));


		final Paillier paillier = paillierEncryption.make(paillierBitSize, certainty, bitSet);

		logger.debug("Paillier info: {}", paillier.toString());
		// Set the necessary QueryInfo and Paillier objects
		QueryInfo queryInfo = new QueryInfo();
		queryInfo.setIdentifier(queryIdentifier);
		queryInfo.setNumSelectors(numSelectors);
		queryInfo.setHashBitSize(hashBitSize);
		queryInfo.setDataPartitionBitSize(dataPartitionBitSize);
		queryInfo.setQueryType(querySchema.getName());
		queryInfo.setEmbedSelector(embedSelector);
		queryInfo.setQuerySchema(querySchema);

		// Check the number of selectors to ensure that 2^{numSelector*dataPartitionBitSize} < N
		// For example, if the highest bit is set, the largest value is
		// \floor{paillierBitSize/dataPartitionBitSize}
		int exp = numSelectors * dataPartitionBitSize;
		BigInteger val = (BigInteger.valueOf(2)).pow(exp);
		if (val.compareTo(paillier.getN()) != -1) {
			final String message = "The number of selectors = " + numSelectors + " must be such that " + "2^{numSelector*dataPartitionBitSize} < N = "
					+ paillier.getN().toString(2);
			throw new PIRException(message);
		}

		// Perform the encryption
		// EncryptQuery encryptQuery = new EncryptQuery(queryInfo, selectors, paillier, method,
		// modPowAbstraction);
		return encryptQuery.encrypt(queryInfo, selectors, paillier);
	}
}
