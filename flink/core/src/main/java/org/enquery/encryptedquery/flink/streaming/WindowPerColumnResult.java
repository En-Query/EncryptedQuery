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

import org.enquery.encryptedquery.encryption.CipherText;

/**
 * Result of processing a window keyed by the row hash
 */
public class WindowPerColumnResult implements Serializable {
	private static final long serialVersionUID = -2173476206286381184L;

	public long windowMinTimestamp;
	public long windowMaxTimestamp;
	public int col;
	public CipherText cipherText;

	/**
	 * Flink needs default constructor for serialization
	 */
	public WindowPerColumnResult() {}

	/**
	 * 
	 */
	public WindowPerColumnResult(//
			long windowMinTimestamp,
			long windowMaxTimestamp,
			int col,
			CipherText cipherText) //
	{
		this.windowMinTimestamp = windowMinTimestamp;
		this.windowMaxTimestamp = windowMaxTimestamp;
		this.col = col;
		this.cipherText = cipherText;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("WindowPerColumnResult [windowMaxTimestamp=").append(windowMaxTimestamp).append(", col=").append(col).append("]");
		return builder.toString();
	}

}
