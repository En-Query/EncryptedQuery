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

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

/**
 * This class is designed to offer a one-stop-shop for invoking the desired version of modPow
 */
@Component(property = "library=java", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class ModPowAbstractionJavaImpl implements ModPowAbstraction {

	@Override
	public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
		return base.modPow(exponent, modulus);
	}
}
