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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.enquery.encryptedquery.healthcheck.SubsystemHealthCheck;
import org.enquery.encryptedquery.healthcheck.SystemHealthCheck;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class AggregatedHealthCheck implements SystemHealthCheck {

	private static Logger log = LoggerFactory.getLogger(AggregatedHealthCheck.class);

	@Reference(policyOption = ReferencePolicyOption.GREEDY)
	volatile private List<SubsystemHealthCheck> subsystems;

	@Override
	public boolean isHealthy() {
		boolean debugging = log.isDebugEnabled();

		boolean result = true;
		int cnt = 0;
		Map<String, Boolean> statuses = healthStatus();
		for (Entry<String, Boolean> entry : statuses.entrySet()) {

			if (debugging) {
				log.debug("Subsystem '{}' health status is '{}'.", entry.getKey(), entry.getValue());
			}

			result = result && entry.getValue();
			cnt++;
		}

		if (cnt == 0) {
			log.warn("No health check subsystems found.");
			return false;
		}

		return result;
	}

	@Override
	public Map<String, Boolean> healthStatus() {
		Map<String, Boolean> result = new HashMap<>();

		if (subsystems == null) return result;

		for (SubsystemHealthCheck subsystem : subsystems) {
			result.put(subsystem.subsystemName(), subsystem.isHealthy());
		}
		return result;
	}
}

