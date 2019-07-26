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
package org.enquery.encryptedquery.core;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;

public class Partitioner {

	/**
	 * @param encoded
	 * @param dataChunkSize
	 * @return
	 */
	public List<byte[]> createPartitions(ByteBuffer buffer, int dataChunkSize) {
		Validate.notNull(buffer);
		Validate.isTrue(dataChunkSize > 0);

		final List<byte[]> result = new ArrayList<>(buffer.remaining() / dataChunkSize + 1);
		if (buffer.remaining() <= 0) return result;

		byte[] tempByteArray = null;
		while (buffer.remaining() > 0) {
			tempByteArray = new byte[dataChunkSize];
			int count = Math.min(buffer.remaining(), dataChunkSize);
			buffer.get(tempByteArray, 0, count);
			result.add(tempByteArray);
		}

		return result;
	}

}
