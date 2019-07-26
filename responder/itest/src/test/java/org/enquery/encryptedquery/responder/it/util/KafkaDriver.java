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
package org.enquery.encryptedquery.responder.it.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.propagateSystemProperty;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaDriver {

	/**
	 * 
	 */
	private static final String TOPIC = "test";
	public static final Logger log = LoggerFactory.getLogger(KafkaDriver.class);
	private Process zookeeperProcess;
	private Process kafkaProcess;
	private ExecutorService threadPool = Executors.newCachedThreadPool();

	public void init() throws Exception {
		configureKafka();
		configureZookeeper();
		runZookeeper();
		Thread.sleep(5_000);
		runKafka();
		Thread.sleep(5_000);
		createTestTopic();
	}

	public void cleanup() throws IOException, InterruptedException {
		stopKafka();
		stopZookeeper();
	}

	public Option[] configuration() {
		return CoreOptions.options(
				wrappedBundle(
						mavenBundle()
								.groupId("org.apache.kafka")
								.artifactId("kafka-clients")
								.versionAsInProject()),

				propagateSystemProperty("kafka.install.dir"));
	}



	/**
	 * @throws IOException
	 * 
	 */
	private void configureZookeeper() throws IOException {
		String kafkaInstallDir = System.getProperty("kafka.install.dir");
		Path path = Paths.get(kafkaInstallDir, "config", "zookeeper.properties");
		Properties p = new Properties();
		try (InputStream is = Files.newInputStream(path)) {
			p.load(is);
		}
		Path dataPath = Paths.get(kafkaInstallDir, "data", "zookeeper");
		p.put("dataDir", dataPath.toString());
		p.put("clientPortAddress", "localhost");
		try (OutputStream os = Files.newOutputStream(path)) {
			p.store(os, "");
		}

		FileUtils.deleteDirectory(dataPath.toFile());
	}

	/**
	 * @throws IOException
	 * 
	 */
	private void configureKafka() throws IOException {
		String kafkaInstallDir = System.getProperty("kafka.install.dir");
		Path path = Paths.get(kafkaInstallDir, "config", "server.properties");
		Properties p = new Properties();
		try (InputStream is = Files.newInputStream(path)) {
			p.load(is);
		}
		Path dataPath = Paths.get(kafkaInstallDir, "data", "kafka");
		p.put("log.dirs", dataPath.toString());
		p.put("listeners", "PLAINTEXT://localhost:9092");
		p.put("delete.topic.enable", "true");
		try (OutputStream os = Files.newOutputStream(path)) {
			p.store(os, "");
		}

		FileUtils.deleteDirectory(dataPath.toFile());
	}

	/**
	 * @throws IOException
	 * @throws InterruptedException
	 * 
	 */
	private void runKafka() throws IOException, InterruptedException {
		// bin/kafka-server-start.sh config/server.properties
		List<String> arguments = new ArrayList<>();
		Path programPath = Paths.get(System.getProperty("kafka.install.dir"), "bin", "kafka-server-start.sh");
		arguments.add(programPath.toString());
		arguments.add("config/server.properties");

		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		processBuilder.directory(new File(System.getProperty("kafka.install.dir")));
		processBuilder.redirectErrorStream(true);

		log.info("Launch kafka with arguments: " + arguments);
		kafkaProcess = processBuilder.start();
	}


	/**
	 * @throws IOException
	 * @throws InterruptedException
	 * 
	 */
	private void runZookeeper() throws IOException, InterruptedException {
		// bin/zookeeper-server-start.sh config/zookeeper.properties
		List<String> arguments = new ArrayList<>();
		Path programPath = Paths.get(System.getProperty("kafka.install.dir"),
				"bin",
				"zookeeper-server-start.sh");
		arguments.add(programPath.toString());
		arguments.add("config/zookeeper.properties");

		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		processBuilder.directory(new File(System.getProperty("kafka.install.dir")));
		processBuilder.redirectErrorStream(true);

		log.info("Launch kafka with arguments: " + arguments);
		zookeeperProcess = processBuilder.start();
	}

	/**
	 * @throws IOException
	 * @throws InterruptedException
	 * 
	 */
	private void stopZookeeper() throws IOException, InterruptedException {
		endProcess(zookeeperProcess);
	}

	private void endProcess(Process p) throws InterruptedException {
		if (p == null) return;
		try {
			p.destroy();
			int i = 10;
			while (i-- > 0 && p.isAlive())
				Thread.sleep(1000);

			if (p.isAlive()) {
				p.destroyForcibly();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @throws IOException
	 * @throws InterruptedException
	 * 
	 */
	private void stopKafka() throws IOException, InterruptedException {
		endProcess(kafkaProcess);
	}

	public void send(Path file) throws IOException {
		long[] count = {0};
		// Thread.currentThread().setContextClassLoader(null);
		try (KafkaProducer<Long, String> producer = new KafkaProducer<>(createProducerConfig())) {
			Files.lines(file).forEach(line -> {
				ProducerRecord<Long, String> producerRecord = new ProducerRecord<>(TOPIC, count[0], line);
				producer.send(producerRecord);
				++count[0];
			});
			producer.flush();
		}
		log.info("Sent {} records from file {}", count[0], file);
	}

	private Properties createProducerConfig() {
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
		props.put("acks", "all");
		props.put("retries", 0);
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		return props;
	}

	/**
	 * @throws IOException
	 * @throws InterruptedException
	 * 
	 */
	private void createTestTopic() throws IOException, InterruptedException {
		// bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1
		// --partitions 1 --topic test
		List<String> arguments = new ArrayList<>();
		Path programPath = Paths.get(System.getProperty("kafka.install.dir"), "bin", "kafka-topics.sh");
		arguments.add(programPath.toString());
		arguments.add("--create");
		arguments.add("--force");
		arguments.add("--zookeeper");
		arguments.add("localhost:2181");
		arguments.add("--replication-factor");
		arguments.add("1");
		arguments.add("--partitions");
		arguments.add("2");
		arguments.add("--topic");
		arguments.add(TOPIC);

		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		processBuilder.directory(new File(System.getProperty("kafka.install.dir")));
		processBuilder.redirectErrorStream(true);

		log.info("Creating topic: " + arguments);
		Process p = processBuilder.start();
		// capture and log child process output in separate thread
		threadPool.submit(new ChildProcessLogger(p.getInputStream(), log));
		assertTrue(p.waitFor(30, TimeUnit.SECONDS));
		assertEquals(0, p.exitValue());
	}

}
