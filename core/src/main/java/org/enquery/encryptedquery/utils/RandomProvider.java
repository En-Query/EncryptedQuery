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
package org.enquery.encryptedquery.utils;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that provides access to an existing SecureRandom object.
 * <p>
 * SECURE_RANDOM is a globally available SecureRandom instantiated based on the
 * "pallier.secureRandom.algorithm" and "pallier.secureRandom.provider" configuration variables.
 * <p>
 * This is safe because there is no way for a user to make the quality of the generated random worse
 * nor to reveal critical state of the PRNG.
 * <p>
 * The two methods that would appear to cause problems <i>but don't</i> are:
 * <ul>
 * <li>{@code setSeed} - setSeed doesn't replace the seed in the SecureRandom object but instead
 * "the given seed supplements, rather than replaces, the existing seed. Thus, repeated calls are
 * guaranteed never to reduce randomness".
 * <li>{@code getSeed} - getSeed doesn't return the seed of the SecureRandom object but returns new
 * seed material generated with the same seed generation algorithm used to create the instance.
 * <p>
 * </ul>
 */
@Component(service = RandomProvider.class)
public class RandomProvider {

	private static final Logger logger = LoggerFactory.getLogger(RandomProvider.class);

	public static String SECURE_RANDOM_ALG = "secureRandom.algorithm";
	public static String SECURE_RANDOM_PROVIDER = "secureRandom.provider";

	private SecureRandom secureRandom = new SecureRandom();

	@Activate
	public void initialize(Map<String, String> config) {
		try {
			String alg = config.get(SECURE_RANDOM_ALG);
			if (alg == null) {
				secureRandom = new SecureRandom();
			} else {
				String provider = config.get(SECURE_RANDOM_PROVIDER);
				secureRandom = (provider == null) ? SecureRandom.getInstance(alg) : SecureRandom.getInstance(alg, provider);
			}
			logger.info("Using secure random from " + secureRandom.getProvider().getName() + ":" + secureRandom.getAlgorithm());
		} catch (GeneralSecurityException e) {
			throw new RuntimeException("Unable to instantiate a SecureRandom object with the requested algorithm.", e);
		}
	}

	/**
	 * Return a globally available SecureRandom instantiated based on the
	 * "pallier.secureRandom.algorithm" and "pallier.secureRandom.provider" configuration variables.
	 * <p>
	 * This is safe because there is no way for a caller to make the quality of the generated random
	 * worse nor to reveal critical state of the PRNG.
	 * <p>
	 * The two methods that would appear to cause problems <i>but don't</i> are:
	 * <ul>
	 * <li>{@code setSeed} - setSeed doesn't replace the seed in the SecureRandom object but instead
	 * "the given seed supplements, rather than replaces, the existing seed. Thus, repeated calls
	 * are guaranteed never to reduce randomness".
	 * <li>{@code getSeed} - getSeed doesn't return the seed of the SecureRandom object but returns
	 * new seed material generated with the same seed generation algorithm used to create the
	 * instance.
	 * <p>
	 * </ul>
	 *
	 * @return The pre-existing SecureRandom object instantiated based on the
	 *         "pallier.secureRandom.algorithm" and "pallier.secureRandom.provider" configuration
	 *         variables.
	 */
	public Random getSecureRandom() {
		return secureRandom;
	}
}
