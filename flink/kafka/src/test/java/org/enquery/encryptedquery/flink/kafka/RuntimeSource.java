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
package org.enquery.encryptedquery.flink.kafka;

import org.apache.flink.streaming.api.functions.source.SourceFunction;

/**
 *
 */
public class RuntimeSource implements SourceFunction<String> {

	private static final long serialVersionUID = 896339099221645435L;
	private final long runtimeInSeconds;
	private volatile boolean isRunning = true;

	/**
	 * 
	 */
	public RuntimeSource(long runtimeInSeconds) {
		this.runtimeInSeconds = runtimeInSeconds;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.flink.streaming.api.functions.source.SourceFunction#run(org.apache.flink.streaming
	 * .api.functions.source.SourceFunction.SourceContext)
	 */
	@Override
	public void run(SourceContext<String> context) throws Exception {
		Thread.sleep(runtimeInSeconds * 1000);
		context.collect(null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.streaming.api.functions.source.SourceFunction#cancel()
	 */
	@Override
	public void cancel() {
		isRunning = false;
	}

}
