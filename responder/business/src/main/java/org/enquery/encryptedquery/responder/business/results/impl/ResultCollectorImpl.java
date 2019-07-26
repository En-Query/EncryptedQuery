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
package org.enquery.encryptedquery.responder.business.results.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.business.execution.ExecutionLock;
import org.enquery.encryptedquery.responder.business.results.ResultCollector;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.ResultRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation for ResultCollector
 */
@Component
public class ResultCollectorImpl implements ResultCollector {

	private static final Logger log = LoggerFactory.getLogger(ResultCollectorImpl.class);

	@Reference
	private ResultRepository resultRepository;
	@Reference
	private ExecutionLock executionLock;


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.responder.business.results.ResultCollector#collect(org.
	 * enquery.encryptedquery.responder.data.entity.Execution)
	 */
	@Override
	public void collect(Execution execution) throws IOException {
		Validate.notNull(execution);

		try {
			executionLock.lock(execution);
		} catch (InterruptedException e) {
			// the application may be shutting down as we wait for the lock
			log.warn("Interrupted while waiting for a lock on execution {}", execution);
			return;
		}
		log.warn("Acquired a lock on execution {}", execution);
		try {
			final String filePathStr = execution.getOutputFilePath();
			if (filePathStr == null) {
				log.warn("Execution does not have an output file path value: {}", execution);
				return;
			}

			Path path = Paths.get(filePathStr);
			if (!Files.exists(path)) {
				log.warn("Execution '{}' output '{}' does not exists", execution, path);
				return;
			}

			if (Files.isDirectory(path)) {
				log.info("Execution '{}' output '{}' is a directory.", execution, path);
				collectStreaming(execution, path);
			} else {
				log.info("Execution '{}' output '{}' is a file.", execution, path);
				collectBatch(execution, path);
			}
		} finally {
			executionLock.unlock(execution);
			log.warn("Released a lock on execution {}", execution);
		}
	}

	/**
	 * @param execution
	 * @param path
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private void collectBatch(Execution execution, Path path) throws FileNotFoundException, IOException {
		log.info("Saving result '{}' to execution {}.", path, execution);

		Validate.notNull(execution);
		Validate.notNull(execution.getStartTime());
		Validate.notNull(execution.getEndTime());
		Validate.notNull(path);
		Validate.isTrue(Files.exists(path));

		try (InputStream inputStream = new FileInputStream(path.toFile())) {
			resultRepository.add(execution,
					inputStream,
					execution.getStartTime().toInstant(),
					execution.getEndTime().toInstant());
		}
		// Delete the response file after storing it in the repository
		try {
			Files.delete(path);
		} catch (Exception e) {
			log.warn("Unable to delete response file: " + path, e);
		}

	}

	/**
	 * @param execution
	 * @param path
	 * @throws IOException
	 */
	private void collectStreaming(Execution execution, Path path) throws IOException {
		Files.walkFileTree(path, new ResponseFileVisitor(info -> {
			try {
				try (InputStream inputStream = Files.newInputStream(info.path)) {
					resultRepository.add(execution, inputStream, info.startTime, info.endTime);
				}
			} catch (IOException e) {
				log.error("Error storing streaming response file: " + info.path,
						e);
				return false;
			}
			return true;
		}));
	}

}
