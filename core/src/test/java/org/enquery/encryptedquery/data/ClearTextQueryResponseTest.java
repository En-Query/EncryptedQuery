/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under homomorphic encryption to securing the query and results set from database owner inspection. 
 * Copyright (C) 2018  EnQuery LLC 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.enquery.encryptedquery.data.ClearTextQueryResponse.Hits;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Record;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Selector;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearTextQueryResponseTest {

	private final Logger log = LoggerFactory.getLogger(ClearTextQueryResponseTest.class);

	@SuppressWarnings("unchecked")
	@Test
	public void test() {
		ClearTextQueryResponse ctr = new ClearTextQueryResponse("test query", "query id");
		Selector selector = new Selector("age");

		Hits hits = new Hits("selector value");

		Record record = new Record();
		record.add("children", "Zack");
		record.add("children", "Yvette");
		record.add("children", "");

		hits.add(record);

		selector.add(hits);
		ctr.add(selector);

		ctr.forEach(sel -> {
			assertEquals("age", sel.getName());
			sel.forEachHits(h -> h.forEachRecord(
					rec -> rec.forEachField(f -> {
						log.info("{}", f.toString());
						List<Object> found = (List<Object>) f.getValue();
						assertEquals("found: " + found, 3, found.size());
						assertTrue(found.contains("Zack"));
						assertTrue(found.contains("Yvette"));
						assertTrue(found.contains(""));
					})));
		});

	}

}
