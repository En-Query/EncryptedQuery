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
package org.enquery.encryptedquery.flink.streaming;

import java.io.Serializable;

import org.enquery.encryptedquery.flink.Buffer;

/**
 * Result of processing a window keyed by the row hash
 */
public class WindowAndColumn implements Serializable {
	private static final long serialVersionUID = -2173476206286381184L;

	public long windowMinTimestamp;
	public long windowMaxTimestamp;
	public long colNum;
	public Buffer.Column column;
	public boolean isEof;
	// private int windowParallelim;


	/**
	 * Flink needs default constructor for serialization
	 */
	public WindowAndColumn() {}

	/**
	 * 
	 */
	public WindowAndColumn(//
			long windowMinTimestamp,
			long windowMaxTimestamp,
			long col,
			Buffer.Column column) //
	{
		this.windowMinTimestamp = windowMinTimestamp;
		this.windowMaxTimestamp = windowMaxTimestamp;
		this.colNum = col;
		this.column = column;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("WindowPerRowHashResult [col=").append(colNum).append(", windowMaxTimestamp=").append(windowMaxTimestamp).append("]");
		return builder.toString();
	}

	// public int getWindowParallelim() {
	// return windowParallelim;
	// }
	//
	// public void setWindowParallelim(int windowParallelim) {
	// this.windowParallelim = windowParallelim;
	// }
}
