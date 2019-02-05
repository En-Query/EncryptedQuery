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
import java.util.Map;

import org.enquery.encryptedquery.encryption.CipherText;

/**
 * The end result of processing a window before the result file is written.
 * 
 */
public class WindowFinalResult implements Serializable {

	private static final long serialVersionUID = 976200086635836834L;

	public long windowMinTimestamp;
	public long windowMaxTimestamp;
	public Map<Integer, CipherText> result;

	/**
	 * Flink needs default constructor for serialization
	 */
	public WindowFinalResult() {}

	/**
	 * 
	 */
	public WindowFinalResult(//
			long windowMinTimestamp,
			long windowMaxTimestamp,
			Map<Integer, CipherText> result) //
	{
		this.windowMinTimestamp = windowMinTimestamp;
		this.windowMaxTimestamp = windowMaxTimestamp;
		this.result = result;
	}

	public Map<Integer, CipherText> getResult() {
		return result;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("WindowFinalResult [windowMaxTimestamp=").append(windowMaxTimestamp).append("]");
		return builder.toString();
	}

}
