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

import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.enquery.encryptedquery.core.FieldTypes;

public interface FlinkTypes extends FieldTypes {

	default TypeInformation<?> pirTypeToFlinkType(String type) {
		TypeInformation<?> result = null;
		switch (type) {
			case BYTE:
				result = BasicTypeInfo.BYTE_TYPE_INFO;
				break;
			case SHORT:
				result = BasicTypeInfo.SHORT_TYPE_INFO;
				break;
			case INT:
				result = BasicTypeInfo.INT_TYPE_INFO;
				break;
			case LONG:
				result = BasicTypeInfo.LONG_TYPE_INFO;
				break;
			case FLOAT:
				result = BasicTypeInfo.FLOAT_TYPE_INFO;
				break;
			case DOUBLE:
				result = BasicTypeInfo.DOUBLE_TYPE_INFO;
				break;
			case CHAR:
				result = BasicTypeInfo.CHAR_TYPE_INFO;
				break;
			case STRING:
			case ISO8601DATE:
			case IP4:
			case BYTEARRAY:
			case IP6:
				result = BasicTypeInfo.STRING_TYPE_INFO;
				break;
			default:
				throw new RuntimeException("type = '" + type + "' not recognized!");
		}
		return result;
	}
}
