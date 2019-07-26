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
package org.enquery.encryptedquery.flink.batch;

import java.util.function.BiConsumer;

import org.apache.commons.lang3.Validate;

/**
 * Specialized data structure mimicking a Queue of Columns where each column is made of row. Each
 * cell contains a byte[] which is the partition of the input data record
 */
public class Buffer {

	// private static final Logger log = LoggerFactory.getLogger(Buffer.class);


	public static class Column {
		// number of data elements (non null cells) in this column
		private int dataCount;
		// array of cells making this column (first index is the row number from 0 to 2^hashbitsize)
		private final byte[][] cells;
		// global number of this column
		private final long columnNumber;

		public Column(int hashBitSize, long columnNumber) {
			cells = new byte[1 << hashBitSize][];
			this.columnNumber = columnNumber;
		}

		/**
		 * @return
		 */
		public boolean isEmpty() {
			return dataCount <= 0;
		}

		public long getColumnNumber() {
			return columnNumber;
		}

		public void addData(int row, byte[] data) {
			cells[row] = data;
			dataCount++;
		}

		public int getDataCount() {
			return dataCount;
		}

        /**
		 * Apply an action to each row data, passing the row number and the non null data element
		 * 
		 * @param action
		 */
		public void forEachRow(BiConsumer<? super Integer, ? super byte[]> action) {
			for (int i = 0; i < cells.length; ++i) {
				byte[] cell = cells[i];
				if (cell != null) {
					action.accept(i, cell);
				}
			}
		}

	}

	private final int hashBitSize;
	private final int numberOfColumns;

	// array of columns, works as a circular queue, with `headIndex` pointing to
	// current queue head
	private final Buffer.Column[] columns;

	// counts the number of data elements per row
	private final int[] perRowDataCounts;

	// the index of current head in queue of columns backed by `columns`
	// it cycles from 0 to numberOfColumns-1 as columns are added/popped from the queue
	private int headIndex = 0;

	// track sequential column numbers
	private long nextColumnNumber;

	public Buffer(int numberOfColumns, int hashBitSize) {
		Validate.isTrue(numberOfColumns > 0, "numberOfColumns must be > 0. Actual: %d", numberOfColumns);
		this.hashBitSize = hashBitSize;
		this.numberOfColumns = numberOfColumns;
		columns = new Buffer.Column[numberOfColumns];
		perRowDataCounts = new int[1 << hashBitSize];
	}

	public void add(int row, byte[] data) {
		if (data == null) return;
		Validate.isTrue(spaceAvailable(row), "Space not available to perform Buffer.add row(%d)", row);
		final int rowDataCount = perRowDataCounts[row];
		final int index = (headIndex + rowDataCount) % numberOfColumns;

		Buffer.Column column = columns[index];
		if (column == null) {
			column = columns[index] = new Column(hashBitSize, nextColumnNumber++);
		}
		column.addData(row, data);
		perRowDataCounts[row]++;
	}

	/**
	 * Pop the next nont empty column. If empty, null is returned;
	 * 
	 * @return
	 */
	public Buffer.Column pop() {
		Buffer.Column result = peek();
		if (result == null) return null;

		// clear the head of the queue
		columns[headIndex] = null;

		// increment the head of the queue (circular array)
		++headIndex;
		if (headIndex >= columns.length) headIndex = 0;

		// update the per row data counts
		for (int i = 0; i < perRowDataCounts.length; ++i) {
			if (perRowDataCounts[i] > 0) perRowDataCounts[i]--;
		}

		// lastPoppedColumnNumber = result.getColumnNumber();
		return result;
	}

	/**
	 * @param row
	 * @return
	 */
	public boolean spaceAvailable(int row) {
		return (numberOfColumns - perRowDataCounts[row]) > 0;
	}

	/**
	 * Null if the column in the head of the queue is empty (i.e., it does not have any data)
	 * 
	 * @return
	 */
	public Buffer.Column peek() {
		Column column = columns[headIndex];
		if (column == null) return null;
		if (column.isEmpty()) return null;
		return column;
	}
}
