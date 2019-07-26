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

import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.concurrent.TimedSemaphore;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.enquery.encryptedquery.flink.streaming.InputRecord;
import org.enquery.encryptedquery.flink.streaming.TimeBoundStoppableConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class TimedKafkaConsumer extends TimeBoundStoppableConsumer {

	private static final long serialVersionUID = -1098744497121999913L;
	public static final Logger log = LoggerFactory.getLogger(TimedKafkaConsumer.class);

	public static enum StartOffset {
		fromEarliest, fromLatest, fromLatestCommit
	};

	private final String bootstrap_servers;
	private final String groupId;
	private final String topic;
	private final StartOffset startOffset;
	private final Integer emissionRatePerSecond;
	private Properties properties;
	private Consumer<Long, String> consumer;


	// The semaphore for limiting database load.
	private transient TimedSemaphore emissionSemaphore;
	private transient int recordCount = 0;


	public TimedKafkaConsumer(String topic,
			String bootstrapServers,
			String groupId,
			StartOffset startOffset,
			Long maxTimestamp,
			Path outputPath,
			Integer emissionRatePerSecond,
			Time windowSize) {

		super(maxTimestamp, outputPath, windowSize);

		this.bootstrap_servers = bootstrapServers;
		this.groupId = groupId;
		this.topic = topic;
		this.startOffset = startOffset;
		this.emissionRatePerSecond = emissionRatePerSecond;

		log.info("Created TimedKafkaConsumer with maxTimestamp={}, topic={}, groupId={}, startOffset={}, bootstrap_servers={}, emissionRatePerSecond={}",
				maxTimestamp,
				topic,
				groupId,
				startOffset,
				bootstrap_servers,
				emissionRatePerSecond);
	}

	private Properties makeProperties() {
		final Properties result = new Properties();
		result.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap_servers);
		result.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		result.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
		result.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		result.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

		// control initial offset if this the first time this group id consumes
		if (startOffset == StartOffset.fromLatest) {
			result.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
		} else {
			result.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		}

		// Set client ID, (only useful for logging, metrics, etc.) use this Flink task name and the
		// parallel subtask index
		RuntimeContext runtimeContext = getRuntimeContext();
		result.put(ConsumerConfig.CLIENT_ID_CONFIG, String.format("%s#%d)", runtimeContext.getTaskName(), runtimeContext.getIndexOfThisSubtask()));
		return result;
	}


	@Override
	public void open(Configuration configuration) throws Exception {
		properties = makeProperties();

		// Create the consumer using props.
		consumer = new KafkaConsumer<>(properties);

		// Subscribe to the topic.
		List<TopicPartition> partitions = consumer.partitionsFor(topic)
				.stream()
				.map(p -> new TopicPartition(p.topic(), p.partition()))
				.collect(Collectors.toList());

		log.info("Partitions: {}", partitions);

		consumer.assign(partitions);
		// fromLatestCommit is the default
		if (startOffset == StartOffset.fromEarliest) {
			consumer.seekToBeginning(partitions);
		} else if (startOffset == StartOffset.fromLatest) {
			consumer.seekToEnd(partitions);
		}

		if (emissionRatePerSecond != null) {
			emissionSemaphore = new TimedSemaphore(1, TimeUnit.SECONDS, emissionRatePerSecond);
		}
	}

	@Override
	public void close() throws Exception {
		if (consumer != null) {
			consumer.close();
			consumer = null;
		}
		if (emissionSemaphore != null) {
			emissionSemaphore.shutdown();
			emissionSemaphore = null;
		}
	}


	@Override
	public void run(SourceContext<InputRecord> ctx) throws Exception {
		beginRun();
		try {
			while (canRun()) {
				ctx.markAsTemporarilyIdle();
				final ConsumerRecords<Long, String> consumerRecords = consumer.poll(Duration.ofMillis(1000));

				if (!canRun()) {
					break;
				}

				ingestRecords(ctx, consumerRecords);
				// consumer.commitAsync();

				if (log.isDebugEnabled() && consumerRecords.count() > 0) {
					log.debug("Emitted {} records.", consumerRecords.count());
				}
			}

			log.info("Emitted a total of {} records.", recordCount);
		} catch (Exception e) {
			log.error("Unexpected error encountered", e);
			setAsFailed();
		} finally {
			endRun(ctx);
		}
	}


	private void ingestRecords(SourceContext<InputRecord> ctx, ConsumerRecords<Long, String> consumerRecords) throws Exception {
		Iterator<ConsumerRecord<Long, String>> iterator = consumerRecords.iterator();
		while (canRun() && iterator.hasNext()) {
			ConsumerRecord<Long, String> record = iterator.next();
			try {
				if (emissionSemaphore != null) emissionSemaphore.acquire();

				// final long timestamp = calcEventTimestamp();
				synchronized (ctx.getCheckpointLock()) {
					collect(ctx, record.value(), System.currentTimeMillis());
					consumer.commitSync();
				}
				recordCount++;
			} catch (Exception e) {
				// records may be malformed, so skip to the next record
				if (log.isWarnEnabled()) {
					log.warn("Skipping record due to error.\n"
							+ "Record key: {}\n"
							+ "Record partition: {}\n"
							+ "Record offset: {}\n"
							+ "Record value:  {}\n"
							+ "Exception: ",
							record.key(),
							record.partition(),
							record.offset(),
							record.value(),
							e);
				}
			}
		}
	}

}
