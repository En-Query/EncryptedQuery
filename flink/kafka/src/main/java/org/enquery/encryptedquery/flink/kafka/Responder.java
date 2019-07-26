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

import java.io.IOException;

import org.apache.commons.lang3.Validate;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.enquery.encryptedquery.flink.BaseQueryExecutor;
import org.enquery.encryptedquery.flink.kafka.TimedKafkaConsumer.StartOffset;
import org.enquery.encryptedquery.flink.streaming.InputRecord;
import org.enquery.encryptedquery.flink.streaming.TimeBoundStoppableConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Responder extends BaseQueryExecutor {

	static final private Logger log = LoggerFactory.getLogger(Responder.class);

	private String brokers;
	private String topic;
	private StartOffset startOffset;
	private Integer emissionRatePerSecond;

	public int getEmissionRatePerSecond() {
		return emissionRatePerSecond;
	}

	public void setEmissionRatePerSecond(int emissionRatePerSecond) {
		this.emissionRatePerSecond = emissionRatePerSecond;
	}

	void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
	}

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

	public StartOffset getStartOffset() {
		return startOffset;
	}

	public void setStartOffset(StartOffset startOffset) {
		this.startOffset = startOffset;
	}

	public void run() throws Exception {
		log.info("Starting Responder");

		initializeCommon();
		initializeStreaming();

		// group id is the query id, which is a GUID
		// this will allow the same query to execute multiple times
		// each time consuming messages starting from previous execution offset
		// Because it is a GUID, it also protects from multiple clients picking
		// the same group id, and accidentally missing records if 'fromLatestCommit' option
		// is selected
		TimeBoundStoppableConsumer consumer = new TimedKafkaConsumer(topic,
				brokers,
				query.getQueryInfo().getIdentifier(),
				startOffset,
				maxTimestamp,
				outputFileName,
				emissionRatePerSecond,
				Time.seconds(windowSizeInSeconds));


		runWithSource(consumer);
	}

	public void runWithSource(TimeBoundStoppableConsumer consumer) throws Exception, IOException {
		runWithSourceAndEnvironment(consumer, StreamExecutionEnvironment.getExecutionEnvironment());
	}

	public void runWithSourceAndEnvironment(TimeBoundStoppableConsumer consumer, final StreamExecutionEnvironment env) throws Exception, IOException {
		Validate.notNull(consumer);
		Validate.notNull(env);

		initializeCommon();
		initializeStreaming();

		// For streaming, output file name is just a directory containing each window results,
		// create it early
		// Files.createDirectories(outputFileName);

		// ingestion time is the source of time
		env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime); // TimeCharacteristic.IngestionTime);
		DataStreamSource<InputRecord> source = env.addSource(consumer);

		run(env, source
				.name("Kafka source with time limit").map(new ParseJson(dataSchema)).name("ParseJson"));
	}

}
