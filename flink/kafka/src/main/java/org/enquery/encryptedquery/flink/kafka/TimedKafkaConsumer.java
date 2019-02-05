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
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.enquery.encryptedquery.flink.streaming.TimeBoundStoppableConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class TimedKafkaConsumer extends TimeBoundStoppableConsumer<String> {

	private static final long serialVersionUID = -1098744497121999913L;
	private static final Logger log = LoggerFactory.getLogger(TimedKafkaConsumer.class);

	private final String bootstrap_servers;
	private final String group_Id;
	private final String topic;
	private final Boolean startFromBeginning;
	private int recordCount = 0;

	public TimedKafkaConsumer(String topic,
			String bootstrapServers,
			String groupId,
			Boolean startFromBeginning,
			Long runtimeInSeconds,
			Path outputPath) {

		super(runtimeInSeconds, outputPath);

		this.bootstrap_servers = bootstrapServers;
		this.group_Id = groupId;
		this.topic = topic;
		this.startFromBeginning = startFromBeginning;

		log.info("Starting TimedKafkaConsumer with runtimeInSeconds={}", runtimeInSeconds);
	}

	private Consumer<Long, String> createConsumer(String topic, String bootstrap_servers, String group_Id) {
		final Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap_servers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, group_Id);
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

		// Create the consumer using props.
		final Consumer<Long, String> consumer = new KafkaConsumer<>(props);

		// TODO: Figure out how to set to read from latest or from beginning of kafka topic

		// Subscribe to the topic.
		consumer.subscribe(Collections.singletonList(topic));
		if (startFromBeginning) {
			log.info("Kafka Consumer Force from Beginning");
			consumer.seekToBeginning(consumer.assignment());
		}
		return consumer;
	}

	@Override
	public void run(SourceContext<String> ctx) throws Exception {
		beginRun();

		final Consumer<Long, String> enqueryKafkaConsumer = createConsumer(topic, bootstrap_servers, group_Id);
		try {
			while (canRun()) {
				final ConsumerRecords<Long, String> consumerRecords = enqueryKafkaConsumer.poll(Duration.ofMillis(1000));

				if (!canRun()) {
					break;
				}

				ingestRecords(ctx, consumerRecords);
				enqueryKafkaConsumer.commitAsync();
				log.info("Ingested {} records.", consumerRecords.count());
			}
		} finally {
			enqueryKafkaConsumer.close();
		}

		log.info("Read a total of {} records from Kafka", recordCount);

		endRun();
	}


	private void ingestRecords(SourceContext<String> ctx, ConsumerRecords<Long, String> consumerRecords) {
		Iterator<ConsumerRecord<Long, String>> iterator = consumerRecords.iterator();
		while (canRun() && iterator.hasNext()) {
			ConsumerRecord<Long, String> record = iterator.next();
			try {
				// this synchronized block ensures that state checkpointing,
				// internal state updates and emission of elements are an atomic operation
				synchronized (ctx.getCheckpointLock()) {
					ctx.collect(record.value());
				}
				recordCount++;
			} catch (Exception e) {
				log.error("Exception ingesting kafka record key {}:", record.key());
				log.error("   Record partition: {}", record.partition());
				log.error("   Record offset: {}", record.offset());
				log.error("   Record Value: {}", record.value());
				log.error("   Exception: {}", e.getMessage());
			}
		}
	}


}
