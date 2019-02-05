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

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

/**
 *
 */
@Component
public class CryptoSchemeRegistryImpl implements CryptoSchemeRegistry {
	// dynamically updated list of CryptoSchemes currently available
	private Collection<CryptoScheme> schemes = new CopyOnWriteArrayList<>();

	@Reference(cardinality = ReferenceCardinality.MULTIPLE,
			policy = ReferencePolicy.DYNAMIC,
			policyOption = ReferencePolicyOption.GREEDY,
			unbind = "removeScheme")
	void addScheme(CryptoScheme scheme) {
		schemes.add(scheme);
	}

	void removeScheme(CryptoScheme scheme) {
		schemes.remove(scheme);
	}

	@Override
	public CryptoScheme cryptoSchemeByName(String schemeId) {
		return schemes.stream()
				.filter(s -> schemeId.equals(s.name()))
				.findFirst()
				.orElse(null);
	}

}
