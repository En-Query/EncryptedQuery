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
package org.enquery.encryptedquery.xml;

import java.math.BigDecimal;

import javax.xml.namespace.QName;

/**
 * Current XSD versions supported. Need to match the version attribute in the corresponding schemas
 */
public interface Versions {
	String SCHEMA_VERSION_ATTRIB = "schemaVersion";
	QName VERSION_ATTRIB = new QName(SCHEMA_VERSION_ATTRIB);

	String EXECUTION_RESOURCE = "2.0";
	BigDecimal EXECUTION_RESOURCE_BI = new BigDecimal(EXECUTION_RESOURCE);

	String QUERY = "2.0";
	BigDecimal QUERY_BI = new BigDecimal(QUERY);

	String QUERY_KEY = "2.0";
	BigDecimal QUERY_KEY_BI = new BigDecimal(QUERY_KEY);

	String RESPONSE = "2.0";
	BigDecimal RESPONSE_BI = new BigDecimal(RESPONSE);

	String EXECUTION = "2.0";
	BigDecimal EXECUTION_BI = new BigDecimal(EXECUTION);
}
