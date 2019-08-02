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

import java.util.List;

/**
 * Visitor to convert a FieldType value from Object to the appropriate data type
 */
public interface FieldTypeUntypedValueConverterVisitor {
	Byte visitByte(Object value);

	List<Byte> visitByteList(Object value);
	
	Boolean visitBoolean(Object value);

	List<Boolean> visitBooleanList(Object value);

	String visitISO8601Date(Object value);

	List<String> visitISO8601DateList(Object value);

	String visitIP6(Object value);

	List<String> visitIP6List(Object value);

	String visitIP4(Object value);

	List<String> visitIP4List(Object value);

	byte[] visitByteArray(Object value);

	List<byte[]> visitByteArrayList(Object value);

	Character visitChar(Object value);

	List<Character> visitCharList(Object value);

	Double visitDouble(Object value);

	List<Double> visitDoubleList(Object value);

	Float visitFloat(Object value);

	List<Float> visitFloatList(Object value);

	Long visitLong(Object value);

	List<Long> visitLongList(Object value);

	Short visitShort(Object value);

	List<Short> visitShortList(Object value);

	Integer visitInt(Object value);

	List<Integer> visitIntList(Object value);

	String visitString(Object value);

	List<String> visitStringList(Object value);
}
