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
package org.enquery.encryptedquery.encryption;

/**
 * Computes a column incrementally: one or more insert calls, followed by a final compute.
 */
public interface ColumnProcessor {

	/**
	 * Insert a new data element.
	 * 
	 * @param rowIndex
	 * @param input the input buffer
	 */
	void insert(int rowIndex, byte[] input);

	/**
	 * Insert a new data element.
	 * 
	 * @param rowIndex
	 * @param input the input buffer
	 * @param inputOffset the offset in input where the input starts
	 * @param inputLen the input length
	 */
	void insert(int rowIndex, byte[] input, int inputOffset, int inputLen);

	/**
	 * Finish computation of the column and resets the internal data structures. The same instance
	 * can be reused after this call.
	 * 
	 * @return computed cipher text
	 */
	CipherText computeAndClear();
	
	/**
	 * Reset all inserted values.
	 */
	void clear();
}
