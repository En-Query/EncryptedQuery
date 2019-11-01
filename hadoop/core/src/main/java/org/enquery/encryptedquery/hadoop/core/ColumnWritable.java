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
package org.enquery.encryptedquery.hadoop.core;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Writable;
import org.enquery.encryptedquery.hadoop.core.Buffer.Column;

/**
 *
 */
public class ColumnWritable implements Writable {

	private Column col;

	public ColumnWritable() {}

	public ColumnWritable(Column col) {
		this.col = col;
	}

	public Column get() {
		return this.col;
	}

	public void set(Column col) {
		this.col = col;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.hadoop.io.Writable#write(java.io.DataOutput)
	 */
	@Override
	public void write(DataOutput out) throws IOException {
		Validate.notNull(col);
		out.writeLong(col.getColumnNumber());
		out.writeInt(col.getDataCount());
		writeCells(col.getCells(), out);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.hadoop.io.Writable#readFields(java.io.DataInput)
	 */
	@Override
	public void readFields(DataInput in) throws IOException {
		long columnNumber = in.readLong();
		int dataCount = in.readInt();
		byte[][] cells = readCells(in);

		col = new Column();
		col.setColumnNumber(columnNumber);
		col.setDataCount(dataCount);
		col.setCells(cells);
	}

	private byte[][] readCells(DataInput in) throws IOException {
		Set<Integer> nulls = new HashSet<>();
		int numberOfNulls = in.readInt();
		for (int i = 0; i < numberOfNulls; ++i) {
			nulls.add(in.readInt());
		}

		int numberOfValues = in.readInt();
		final byte[][] result = new byte[numberOfValues + numberOfNulls][];
		final BytesWritable value = new BytesWritable();
		for (int i = 0; i < result.length; ++i) {
			if (!nulls.contains(i)) {
				value.readFields(in);
				result[i] = value.copyBytes();
			}
		}

		return result;
	}

	/**
	 * @param cells
	 * @param out
	 * @throws IOException
	 */
	private void writeCells(byte[][] cells, DataOutput out) throws IOException {

		// write the indexes of the null values in the cells array prefixed by the number of them
		Set<Integer> nulls = new HashSet<>();
		for (int i = 0; i < cells.length; i++) {
			if (cells[i] == null) nulls.add(i);
		}

		out.writeInt(nulls.size());
		for (Integer index : nulls) {
			out.writeInt(index);
		}

		final BytesWritable value = new BytesWritable();
		out.writeInt(cells.length - nulls.size());
		for (int i = 0; i < cells.length; i++) {
			if (cells[i] != null) {
				value.set(cells[i], 0, cells[i].length);
				value.write(out);
			}
		}
	}
}
