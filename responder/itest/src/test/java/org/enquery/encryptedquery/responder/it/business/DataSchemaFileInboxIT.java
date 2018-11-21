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
package org.enquery.encryptedquery.responder.it.business;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSchemaField;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.it.AbstractResponderItest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DataSchemaFileInboxIT extends AbstractResponderItest {

	public static final Logger log = LoggerFactory.getLogger(DataSchemaFileInboxIT.class);

	@Inject
	private DataSchemaService dataSchemaService;

	// force this test to wait until the business 'data-import' Camel context
	@Inject
	@Filter(timeout = 60_000, value = "(camel.context.name=data-import)")
	private CamelContext restContext;

	// @Before
	// public void init() throws SQLException {
	// truncateTables();
	// }

	@Configuration
	public Option[] configuration() {
		return ArrayUtils.addAll(super.baseOptions(), CoreOptions.options(
				editConfigurationFilePut("etc/encrypted.query.responder.business.cfg",
						"inbox.dir",
						INBOX_DIR)));
	}

	@Test
	public void fileIngested() throws Exception {
		assertEquals(0, dataSchemaService.list().size());
		final String name = "simple-schema";
		installDataSchema(name + ".xml");

		DataSchema ds = dataSchemaService.findByName(name);
		assertNotNull(ds);
		assertEquals(name, ds.getName());
		assertEquals(1, ds.getFields().size());
		DataSchemaField field = ds.getFields().get(0);
		assertNotNull(field);
		assertEquals(ds, field.getDataSchema());
		assertEquals("FieldName", field.getFieldName());
		assertEquals("Integer", field.getDataType());
		assertEquals(false, field.getIsArray());
		// assertEquals("default-partitioner", field.getPartitioner());
	}

	@Test
	public void invalidFileRejected() throws Exception {
		assertEquals(0, dataSchemaService.list().size());
		File inbox = new File(INBOX_DIR);
		final String name = "bad-data-schema";
		final String fileName = name + ".xml";

		byte[] bytes = IOUtils.resourceToByteArray("/schemas/" + fileName, this.getClass().getClassLoader());
		saveToFile(bytes, inbox, fileName);
		waitForFileCreated(Paths.get(INBOX_DIR, ".failed", fileName).toFile());
		assertTrue(!Files.exists(Paths.get(INBOX_DIR, ".processed", fileName)));
		DataSchema ds = dataSchemaService.findByName(name);
		assertNull(ds);
	}
}
