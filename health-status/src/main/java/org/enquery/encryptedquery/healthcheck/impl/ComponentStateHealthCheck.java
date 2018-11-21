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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.enquery.encryptedquery.healthcheck.SubsystemHealthCheck;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class ComponentStateHealthCheck implements SubsystemHealthCheck {

	private static Logger log = LoggerFactory.getLogger(ComponentStateHealthCheck.class);

	@Reference
	private ServiceComponentRuntime scr;

	@SuppressWarnings("unchecked")
	private Set<String> ignored = Collections.EMPTY_SET;

	@Activate
	public void activate(Map<String, Object> config) {
		if (config == null) return;
		String[] tmp = (String[]) config.getOrDefault(".ignore.component", new String[] {});
		if (tmp == null) return;
		ignored = new HashSet<>(Arrays.asList(tmp));
	}

	@Override
	public boolean isHealthy() {
		Collection<ComponentDescriptionDTO> descriptions = scr.getComponentDescriptionDTOs();
		for (final ComponentDescriptionDTO descDTO : descriptions) {
			// not enabled components ignored
			if (!scr.isComponentEnabled(descDTO)) continue;

			// ignore components configured to be ignored
			final String componentName = descDTO.name;
			if (ignored.contains(componentName)) continue;

			final Collection<ComponentConfigurationDTO> configs = scr.getComponentConfigurationDTOs(descDTO);
			ComponentConfigurationDTO configDTO = null;
			if (!configs.isEmpty()) {
				configDTO = configs.iterator().next();
			}

			final boolean active = isActive(configDTO);
			if (!active) {
				log.warn("System not healthy due to component '{}' being not active. Current state: {}.", componentName, (configDTO == null) ? "null" : configDTO.state);
				return false;
			}
		}
		return true;
	}

	private boolean isActive(ComponentConfigurationDTO configuration) {
		if (configuration == null) return false;

		if (log.isDebugEnabled()) log.debug("Component '{}' state {}", configuration.description.name, configuration.state);
		if (configuration.unsatisfiedReferences != null && configuration.unsatisfiedReferences.length > 0) return false;

		return ComponentConfigurationDTO.ACTIVE == configuration.state ||
				ComponentConfigurationDTO.SATISFIED == configuration.state;
	}

	@Override
	public String subsystemName() {
		return "components";
	}
}

