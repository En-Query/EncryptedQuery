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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Writable;

/**
 *
 */
public class RowHashAndChunksWritable implements Writable {

	private RowHashAndChunks data;

	public RowHashAndChunksWritable() {}

	public RowHashAndChunksWritable(RowHashAndChunks data) {
		this.data = data;
	}

	public RowHashAndChunks get() {
		return this.data;
	}

	public void set(RowHashAndChunks data) {
		this.data = data;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.hadoop.io.Writable#write(java.io.DataOutput)
	 */
	@Override
	public void write(DataOutput out) throws IOException {
		Validate.notNull(data);
		out.writeInt(data.getRowHash());
		writeChunks(data.getChunks(), out);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.hadoop.io.Writable#readFields(java.io.DataInput)
	 */
	@Override
	public void readFields(DataInput in) throws IOException {
		data = new RowHashAndChunks();
		data.setRowHash(in.readInt());
		data.setChunks(readChunks(in));
	}

	private List<byte[]> readChunks(DataInput in) throws IOException {
		Set<Integer> nulls = new HashSet<>();
		int numberOfNulls = in.readInt();
		for (int i = 0; i < numberOfNulls; ++i) {
			nulls.add(in.readInt());
		}

		int numberOfValues = in.readInt();
		final List<byte[]> result = new ArrayList<>(numberOfNulls + numberOfValues);
		final BytesWritable value = new BytesWritable();
		for (int i = 0; i < numberOfNulls + numberOfValues; ++i) {
			if (nulls.contains(i)) {
				result.add(null);
			} else {
				value.readFields(in);
				result.add(value.copyBytes());
			}
		}

		return result;
	}

	/**
	 * @param list
	 * @param out
	 * @throws IOException
	 */
	private void writeChunks(List<byte[]> list, DataOutput out) throws IOException {

		// write the indexes of the null values in the cells array prefixed by the number of them
		Set<Integer> nulls = new HashSet<>();
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == null) nulls.add(i);
		}

		out.writeInt(nulls.size());
		for (Integer index : nulls) {
			out.writeInt(index);
		}

		final BytesWritable value = new BytesWritable();
		out.writeInt(list.size() - nulls.size());
		for (int i = 0; i < list.size(); i++) {
			byte[] bytes = list.get(i);
			if (bytes != null) {
				value.set(bytes, 0, bytes.length);
				value.write(out);
			}
		}
	}
}
