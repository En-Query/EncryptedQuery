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
package org.enquery.encryptedquery.encryption.impl;

import java.math.BigInteger;
import java.util.Map;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import com.squareup.jnagmp.Gmp;

/**
 * This class is designed to offer a one-stop-shop for invoking the desired version of modPow
 */
@Component(property = "library=gmp", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class ModPowAbstractionGMPImpl implements ModPowAbstraction {

	private boolean useGMPConstantTimeMethods;

	@Activate
	void activate(Map<String, String> config) {
		useGMPConstantTimeMethods = Boolean.parseBoolean(config.getOrDefault(ResponderProperties.PAILLIER_GMP_CONSTANT_TIME_MODE, "false"));
	}

	@Override
	public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
		BigInteger result;

		if (useGMPConstantTimeMethods) {
			// Use GMP and use the "timing attack resistant" method
			// The timing attack resistance slows down performance and is not necessarily proven
			// to block timing attacks.
			// Before getting concerned, please carefully consider your threat model
			// and if you really believe that you may need it
			result = Gmp.modPowSecure(base, exponent, modulus);
		} else {
			// The word "insecure" here does not imply any actual, direct insecurity.
			// It denotes that this function runs as fast as possible without trying to
			// counteract timing attacks. This is probably what you want unless you have a
			// compelling reason why you believe that this environment is safe enough to house
			// your keys but doesn't protect you from another entity on the machine watching
			// how long the program runs.
			result = Gmp.modPowInsecure(base, exponent, modulus);
		}
		return result;
	}

}
