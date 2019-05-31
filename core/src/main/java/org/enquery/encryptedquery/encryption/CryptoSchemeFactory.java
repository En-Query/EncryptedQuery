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
package org.enquery.encryptedquery.encryption;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.CoreConfigurationProperties;

/**
 *
 */
public class CryptoSchemeFactory {

	public static CryptoScheme make(Map<String, String> config) throws Exception {
		return make(config, null);
	}

	/**
	 * @param runParameters
	 * @param executionService
	 * @return
	 * @throws Exception
	 */
	public static CryptoScheme make(Map<String, String> config, ExecutorService executionService) throws Exception {
		String cryptoClassName = config.get(CoreConfigurationProperties.CRYPTO_SCHEME_CLASS_NAME);
		Validate.notEmpty(cryptoClassName, "Missing or invalid property " + CoreConfigurationProperties.CRYPTO_SCHEME_CLASS_NAME);

		@SuppressWarnings("unchecked")
		Class<CryptoScheme> cryptoSchemeClass = (Class<CryptoScheme>) Class.forName(cryptoClassName);
		CryptoScheme crypto = cryptoSchemeClass.newInstance();
		if (crypto instanceof CryptoSchemeSpi) {
			CryptoSchemeSpi spi = (CryptoSchemeSpi) crypto;
			spi.setThreadPool(executionService);
		}
		crypto.initialize(config);
		return crypto;
	}
}
