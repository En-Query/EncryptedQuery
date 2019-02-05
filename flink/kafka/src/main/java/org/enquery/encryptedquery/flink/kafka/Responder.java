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

import java.nio.file.Files;

import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.types.Row;
import org.enquery.encryptedquery.flink.BaseQueryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Responder extends BaseQueryExecutor {

	static final private Logger log = LoggerFactory.getLogger(Responder.class);

	private String brokers;
	private String topic;
	private String groupId;
	private Boolean forceFromStart;
	private String offsetLocation;

	public String getBrokers() {
		return brokers;
	}

	public void setBrokers(String brokers) {
		this.brokers = brokers;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public Boolean getForceFromStart() {
		return forceFromStart;
	}

	public void setForceFromStart(Boolean forceFromStart) {
		this.forceFromStart = forceFromStart;
	}

	public String getOffsetLocation() {
		return offsetLocation;
	}

	public void setOffsetLocation(String offsetLocation) {
		this.offsetLocation = offsetLocation;
	}

	private SourceFunction<String> createStringConsumerForTopic(String topic, String kafkaAddress, String kafkaGroup) {

		TimedKafkaConsumer result = new TimedKafkaConsumer(topic,
				kafkaAddress,
				kafkaGroup,
				forceFromStart,
				runtimeSeconds,
				outputFileName);

		return result;
	}

	public void run() throws Exception {
		log.info("Starting Responder");

		jobName += "->" + outputFileName;
		final boolean debugging = log.isDebugEnabled();

		initializeCommon();
		initializeStreaming();

		// For streaming, output file name is just a directory containing each window results,
		// create it early
		Files.createDirectories(outputFileName);

		// final long startTime = System.currentTimeMillis();
		// set up the streaming execution environment
		final StreamExecutionEnvironment streamingEnv = StreamExecutionEnvironment.getExecutionEnvironment();
		streamingEnv.setStreamTimeCharacteristic(TimeCharacteristic.IngestionTime);

		DataStreamSource<String> source = streamingEnv.addSource(
				createStringConsumerForTopic(topic, brokers, groupId));


		int index = selectorFieldIndex;

		DataStream<Row> stream = source
				.map(new ParseJson(querySchema, rowTypeInfo))
				.filter(row -> {
					if (debugging) log.debug("Filtering row: {}", row);
					boolean present = row.getField(index) != null;
					if (debugging) log.debug("Selector field index {} present: {}", index, present);
					return present;
				});

		run(streamingEnv, stream);
	}
}
