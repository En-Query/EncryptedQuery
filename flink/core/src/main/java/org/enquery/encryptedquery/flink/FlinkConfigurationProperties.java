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
package org.enquery.encryptedquery.flink;

import org.enquery.encryptedquery.responder.ResponderProperties;

/**
 * Properties constants for the Stand Alone Executor
 */
public interface FlinkConfigurationProperties extends ResponderProperties {
	String MAX_HITS_PER_SELECTOR = "maxHitsPerSelector";
	String COLUMN_ENCRYPTION_PARTITION_COUNT = "column.encryption.partition.count";
	String WINDOW_LENGTH = "stream.window.length";
	String WINDOW_ITERATIONS = "stream.window.iterations";
}
