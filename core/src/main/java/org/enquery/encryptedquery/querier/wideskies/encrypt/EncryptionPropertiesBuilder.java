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

import java.util.Properties;

import org.enquery.encryptedquery.querier.QuerierProperties;
import org.enquery.encryptedquery.utils.PIRException;



/**
 * Holds the various parameters related to creating a {@link Querier}.
 *
 */
public class EncryptionPropertiesBuilder implements QuerierProperties {
	private final Properties properties;

	public static EncryptionPropertiesBuilder newBuilder() {
		return new EncryptionPropertiesBuilder();
	}

	private EncryptionPropertiesBuilder() {
		this.properties = new Properties();
		setGeneralDefaults(properties);
		setEncryptionDefaults(properties);
	}

	public EncryptionPropertiesBuilder bitSet(Integer bitSet) {
		if (bitSet != null) {
			properties.setProperty(BIT_SET, String.valueOf(bitSet));
		}
		return this;
	}

	public EncryptionPropertiesBuilder queryType(String queryType) {
		properties.setProperty(QUERYTYPE, queryType);
		return this;
	}

	public EncryptionPropertiesBuilder hashBitSize(Integer hashBitSize) {
		if (hashBitSize != null) {
			properties.setProperty(HASH_BIT_SIZE, String.valueOf(hashBitSize));
		}
		return this;
	}

	public EncryptionPropertiesBuilder dataChunkSize(Integer dataChunkSize) {
		if (dataChunkSize != null) {
			properties.setProperty(DATA_CHUNK_SIZE, String.valueOf(dataChunkSize));
		}
		return this;
	}

	public EncryptionPropertiesBuilder paillierBitSize(Integer paillierBitSize) {
		if (paillierBitSize != null) {
			properties.setProperty(PAILLIER_BIT_SIZE, String.valueOf(paillierBitSize));
		}
		return this;
	}

	public EncryptionPropertiesBuilder certainty(Integer certainty) {
		if (certainty != null) {
			properties.setProperty(CERTAINTY, String.valueOf(certainty));
		}
		return this;
	}

	public EncryptionPropertiesBuilder embedSelector(Boolean embedSelector) {
		if (embedSelector != null) {
			properties.setProperty(EMBEDSELECTOR, String.valueOf(embedSelector));
		}
		return this;
	}

	public Properties build() throws PIRException {
		validateQuerierEncryptionProperties(properties);
		return properties;
	}

	private void setGeneralDefaults(Properties properties) {
		if (!properties.containsKey(EMBEDQUERYSCHEMA)) {
			properties.setProperty(EMBEDQUERYSCHEMA, "true");
		}
		if (!properties.containsKey(NUMTHREADS)) {
			properties.setProperty(NUMTHREADS, String.valueOf(Runtime.getRuntime().availableProcessors()));
		}
	}

	private void setEncryptionDefaults(Properties properties) {
		conditionalSetDetault(properties, HASH_BIT_SIZE, "12");
		conditionalSetDetault(properties, DATA_CHUNK_SIZE, "1");
		conditionalSetDetault(properties, PAILLIER_BIT_SIZE, "3072");
		conditionalSetDetault(properties, CERTAINTY, "128");
		conditionalSetDetault(properties, EMBEDSELECTOR, "true");
		conditionalSetDetault(properties, BIT_SET, "-1");
		conditionalSetDetault(properties, ENCQUERYMETHOD, EncryptQuery.DEFAULT);
	}

	private void conditionalSetDetault(Properties properties, String key, String value) {
		if (!properties.containsKey(key)) {
			properties.setProperty(key, value);
		}
	}
}
