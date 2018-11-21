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
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.enquery.encryptedquery.healthcheck.SubsystemHealthCheck;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class CamelHealthCheck implements SubsystemHealthCheck {

	private static Logger log = LoggerFactory.getLogger(CamelHealthCheck.class);
	// @Reference
	// private BundleContext bundleContext;

	@Reference(policyOption = ReferencePolicyOption.GREEDY)
	volatile private List<CamelContext> camelContextList;

	@SuppressWarnings("unchecked")
	private Set<String> required = Collections.EMPTY_SET;

	@Activate
	public void activate(Map<String, Object> config) {
		if (config == null) return;

		String[] tmp = (String[]) config.getOrDefault(".required.camel", new String[] {});
		if (tmp != null) {
			required = new HashSet<>(Arrays.asList(tmp));
		}
	}

	@Override
	public boolean isHealthy() {
		if (required.isEmpty()) return true;

		List<CamelContext> requiredContexts = camelContextList
				.stream()
				.filter(cc -> required.contains(cc.getName()))
				.collect(Collectors.toList());


		if (requiredContexts.size() != required.size()) {
			log.warn("Missing expected Camel contexts.");
			return false;
		}

		boolean result = true;
		for (CamelContext c : requiredContexts) {
			result = result && isActive(c);
		}
		return true;

	}

	private boolean isActive(CamelContext c) {
		return (c.getStatus() == ServiceStatus.Started);
	}

	@Override
	public String subsystemName() {
		return "camel";
	}
}

