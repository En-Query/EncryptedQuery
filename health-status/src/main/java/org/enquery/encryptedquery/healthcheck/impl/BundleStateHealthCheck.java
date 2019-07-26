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
package org.enquery.encryptedquery.healthcheck.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.karaf.bundle.core.BundleInfo;
import org.apache.karaf.bundle.core.BundleService;
import org.apache.karaf.bundle.core.BundleState;
import org.enquery.encryptedquery.healthcheck.SubsystemHealthCheck;
import org.osgi.framework.Bundle;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class BundleStateHealthCheck implements SubsystemHealthCheck {

	private static Logger log = LoggerFactory.getLogger(BundleStateHealthCheck.class);

	@Reference
	private BundleService bundleService;

	@SuppressWarnings("unchecked")
	private Set<String> ignored = Collections.EMPTY_SET;
	private final BundleState[] allowedStateArray = new BundleState[] {BundleState.Active};
	private final Set<BundleState> allowedStates = new HashSet<>(Arrays.asList(allowedStateArray));

	@Activate
	public void activate(Map<String, Object> config) {
		if (config == null) return;
		String[] tmp = (String[]) config.get(".ignore.bundle");
		if (tmp == null) return;
		ignored = new HashSet<>(Arrays.asList(tmp));
	}

	@Override
	public boolean isHealthy() {

		List<Bundle> bundles = bundleService.selectBundles("0", Collections.emptyList(), true);
		for (Bundle bundle : bundles) {
			// ignore bundle if configured
			final String bundleName = bundle.getSymbolicName();
			if (ignored.contains(bundleName)) continue;

			final BundleInfo info = this.bundleService.getInfo(bundle);
			BundleState state = info.getState();
			if (!allowedStates.contains(state)) {
				log.warn("System not healthy due to bundle '{}' being in state {}.  The expected states are {}.", bundleName, state, allowedStates);
				return false;
			}
		}

		return true;
	}

	@Override
	public String subsystemName() {
		return "bundles";
	}
}

