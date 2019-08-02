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
package org.enquery.encryptedquery.querier.it.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.entity.json.ResourceCollectionResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ResourcesRestServiceIT extends BaseRestServiceItest {

	@Configuration
	public Option[] configuration() {
		return new Option[] {baseOptions()};
	}

	@Override
	@Before
	public void init() throws Exception {
		super.init();
	}

	@Test
	public void list() throws Exception {

		ResourceCollectionResponse resources = invoke("/querier/api/rest/",
				200,
				DEFAULT_ACCEPT,
				resourcesResultMock,
				"direct:retrieve-resources")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(ResourceCollectionResponse.class);
		assertNotNull(resources);

		Map<String, String> map = new HashMap<>();
		for (Resource ep : resources.getData()) {
			map.put(ep.getId(), ep.getSelfUri());
		}
		assertEquals("/querier/api/rest/dataschemas", map.get("dataschema"));
		assertEquals("/querier/api/rest/offline", map.get("offline"));
	}
}
