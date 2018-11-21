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
package org.enquery.encryptedquery.pig.udfs;

import org.enquery.encryptedquery.core.FieldTypes;

public interface PigTypes extends FieldTypes {

	default String pigToPirDataType(byte pigDataType) {
		switch (pigDataType) {
			case org.apache.pig.data.DataType.DATETIME:
				return STRING;
			case org.apache.pig.data.DataType.INTEGER:
				return INT;
			case org.apache.pig.data.DataType.LONG:
				return LONG;
			case org.apache.pig.data.DataType.FLOAT:
				return FLOAT;
			case org.apache.pig.data.DataType.DOUBLE:
				return DOUBLE;
			case org.apache.pig.data.DataType.CHARARRAY:
				return STRING;
			case org.apache.pig.data.DataType.BYTEARRAY:
				return STRING;
		}
		throw new RuntimeException("type = '" + pigDataType + "' not recognized!");
	}
}
