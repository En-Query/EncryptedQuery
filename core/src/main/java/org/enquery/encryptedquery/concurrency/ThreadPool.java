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
package org.enquery.encryptedquery.concurrency;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.Validate;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component
public class ThreadPool implements ExecutorService {

	public static final String CORE_POOL_SIZE = "core.pool.size";
	public static final String MAX_TASK_QUEUE_SIZE = "max.task.queue.size";
	public static final String SHUTDOWN_WAIT_TIME_SECONDS = "shutdown.wait.time.seconds";
	public static final String KEEP_ALIVE_TIME_SECONDS = "keep.alive.time.seconds";
	public static final String MAX_POOL_SIZE = "max.pool.size";

	public static final String[] CONFIURATION_KEYS = {CORE_POOL_SIZE,
			MAX_TASK_QUEUE_SIZE,
			SHUTDOWN_WAIT_TIME_SECONDS,
			KEEP_ALIVE_TIME_SECONDS,
			MAX_POOL_SIZE
	};

	private static final Long DEFAULT_SHUTSHOWN_WAIT_TIME = TimeUnit.MINUTES.toSeconds(5);
	private static final Integer DEFAULT_TASK_QUEUE_SIZE = 10 * 1024;
	private ExecutorService es;
	private long shutdownWaitTimeInSeconds = DEFAULT_SHUTSHOWN_WAIT_TIME;

	@Activate
	public void initialize(Map<String, String> config) {
		Validate.notNull(config);
		int corePoolSize = Integer.parseInt(config.getOrDefault(CORE_POOL_SIZE, "16"));
		int maximumPoolSize = Integer.parseInt(config.getOrDefault(MAX_POOL_SIZE, "64"));
		long keepAliveTimeInSeconds = Long.parseLong(config.getOrDefault(KEEP_ALIVE_TIME_SECONDS, "30"));
		shutdownWaitTimeInSeconds = Long.parseLong(config.getOrDefault(SHUTDOWN_WAIT_TIME_SECONDS, DEFAULT_SHUTSHOWN_WAIT_TIME.toString()));
		int maxTaskQueueSize = Integer.parseInt(config.getOrDefault(MAX_TASK_QUEUE_SIZE, DEFAULT_TASK_QUEUE_SIZE.toString()));
		es = new ThreadPoolExecutor(
				corePoolSize,
				maximumPoolSize,
				keepAliveTimeInSeconds,
				TimeUnit.SECONDS,
				new ArrayBlockingQueue<Runnable>(maxTaskQueueSize));
	}

	@Deactivate
	public void deactivate() throws InterruptedException {
		if (es != null) {
			es.shutdown();
			boolean terminated = es.awaitTermination(shutdownWaitTimeInSeconds, TimeUnit.SECONDS);
			if (!terminated) {
				es.shutdownNow();
			}
		}
	}


	@Override
	public void execute(Runnable command) {
		es.execute(command);
	}

	@Override
	public void shutdown() {
		es.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		return es.shutdownNow();
	}

	@Override
	public boolean isShutdown() {
		return es.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return es.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return es.awaitTermination(timeout, unit);
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return es.submit(task);
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		return es.submit(task, result);
	}

	@Override
	public Future<?> submit(Runnable task) {
		return es.submit(task);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		return es.invokeAll(tasks);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
		return es.invokeAll(tasks, timeout, unit);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		return es.invokeAny(tasks);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return es.invokeAny(tasks, timeout, unit);
	}
}
