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

import java.time.Instant;
import java.util.List;

/**
 * Visitor to convert a FieldType value to E
 * 
 * @param <E> Data type of return values.
 */
public interface FieldTypeValueConverterVisitor<E> {
	E visitByte(Byte value);

	E visitByteList(List<Byte> value);

	E visitBoolean(Boolean value);

	E visitBooleanList(List<Boolean> value);

	E visitISO8601Date(Instant value);

	E visitISO8601DateList(List<Instant> value);

	E visitIP6(String value);

	E visitIP6List(List<String> value);

	E visitIP4(String value);

	E visitIP4List(List<String> value);

	E visitByteArray(byte[] value);

	E visitByteArrayList(List<byte[]> value);

	E visitChar(Character value);

	E visitCharList(List<Character> value);

	E visitDouble(Double value);

	E visitDoubleList(List<Double> value);

	E visitFloat(Float value);

	E visitFloatList(List<Float> value);

	E visitLong(Long value);

	E visitLongList(List<Long> value);

	E visitShort(Short value);

	E visitShortList(List<Short> value);

	E visitInt(Integer value);

	E visitIntList(List<Integer> value);

	E visitString(String value);

	E visitStringList(List<String> value);
}
