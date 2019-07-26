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

import java.io.Serializable;
import java.util.List;

public class QueueRecord implements Serializable {

	private static final long serialVersionUID = 1L;

	// Hash of Selector
	private int rowIndex;
	// Selector Value
	private String selector;
	private boolean isEndOfFile;
	private List<byte[]> dataChunks;
	private boolean addedToBuffer;
	private long windowMinTimestamp;
	private long windowMaxTimestamp;
	private long totalRecordCount;

	public long getWindowMinTimestamp() {
		return windowMinTimestamp;
	}

	public void setWindowMinTimestamp(long windowMinTimestamp) {
		this.windowMinTimestamp = windowMinTimestamp;
	}

	public long getWindowMaxTimestamp() {
		return windowMaxTimestamp;
	}

	public void setWindowMaxTimestamp(long windowMaxTimestamp) {
		this.windowMaxTimestamp = windowMaxTimestamp;
	}

	public QueueRecord() {}

	// public QueueRecord(int rowIndex, String selector, List<byte[]> dataChunks) {
	// this.rowIndex = rowIndex;
	// this.selector = selector;
	// this.dataChunks = dataChunks;
	// }

	public int getRowIndex() {
		return rowIndex;
	}

	public String getSelector() {
		return selector;
	}

	@Override
	public String toString() {
		StringBuilder output = new StringBuilder();
		output.append("\n  Selector: " + selector + "\n");
		output.append("  Hash(rowIndex): " + rowIndex + "\n");
		output.append("  Column count: " + dataChunks.size() + "\n");
		return output.toString();
	}

	public List<byte[]> getHitValPartitions() {
		return dataChunks;
	}

	public void setHitValPartitions(List<byte[]> hitValPartitions) {
		this.dataChunks = hitValPartitions;
	}

	public void setRowIndex(int rowIndex) {
		this.rowIndex = rowIndex;
	}

	public void setSelector(String selector) {
		this.selector = selector;
	}

	public boolean isAddedToBuffer() {
		return addedToBuffer;
	}

	public void setAddedToBuffer(boolean addedToBuffer) {
		this.addedToBuffer = addedToBuffer;
	}

	/**
	 * clear the data chunks and selector
	 */
	public void clear() {
		dataChunks.clear();
		selector = null;
	}

	public boolean isEndOfFile() {
		return isEndOfFile;
	}

	public void setIsEndOfFile(boolean isEndOfFile) {
		this.isEndOfFile = isEndOfFile;
	}

	public List<byte[]> getDataChunks() {
		return dataChunks;
	}

	public void setDataChunks(List<byte[]> dataChunks) {
		this.dataChunks = dataChunks;
	}

	public void setEndOfFile(boolean isEndOfFile) {
		this.isEndOfFile = isEndOfFile;
	}

	public long getTotalRecordCount() {
		return totalRecordCount;
	}

	public void setTotalRecordCount(long totalRecordCount) {
		this.totalRecordCount = totalRecordCount;
	}
}
