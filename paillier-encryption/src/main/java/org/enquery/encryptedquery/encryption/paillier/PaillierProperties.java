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
package org.enquery.encryptedquery.encryption.paillier;

/**
 * Properties constants for the Responder
 */
public interface PaillierProperties {
	String ENCRYPT_QUERY_TASK_COUNT = "paillier.encrypt.query.task.count";
	String ENCRYPT_QUERY_METHOD = "paillier.encrypt.query.method";
	String MODULUS_BIT_SIZE = "paillier.modulus.bit.size";
	String COLUMN_PROCESSOR = "paillier.column.processor";
	String PRIME_CERTAINTY = "paillier.prime.certainty";
	String SECURE_RANDOM_ALG = "paillier.secureRandom.algorithm";
	String SECURE_RANDOM_PROVIDER = "paillier.secureRandom.provider";
	String MOD_POW_CLASS_NAME = "paillier.mod.pow.class.name";
	String CORE_POOL_SIZE = "paillier.thread.pool.core.size";
	String MAX_TASK_QUEUE_SIZE = "paillier.thread.pool.max.task.queue.size";
	String SHUTDOWN_WAIT_TIME_SECONDS = "paillier.thread.pool.shutdown.wait.time.seconds";
	String KEEP_ALIVE_TIME_SECONDS = "paillier.thread.pool.keep.alive.time.seconds";
	String MAX_POOL_SIZE = "paillier.thread.pool.max.pool.size";

	String[] PROPERTIES = {ENCRYPT_QUERY_TASK_COUNT,
			ENCRYPT_QUERY_METHOD,
			MODULUS_BIT_SIZE,
			COLUMN_PROCESSOR,
			PRIME_CERTAINTY,
			SECURE_RANDOM_ALG,
			SECURE_RANDOM_PROVIDER,
			MOD_POW_CLASS_NAME,
			CORE_POOL_SIZE,
			MAX_TASK_QUEUE_SIZE,
			SHUTDOWN_WAIT_TIME_SECONDS,
			KEEP_ALIVE_TIME_SECONDS,
			MAX_POOL_SIZE,};
}

