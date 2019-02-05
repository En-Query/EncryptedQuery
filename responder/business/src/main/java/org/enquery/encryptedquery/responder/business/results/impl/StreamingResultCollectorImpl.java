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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.business.results.StreamingResultCollector;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.ResultRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation for StreamingResultCollector
 */
@Component
public class StreamingResultCollectorImpl implements StreamingResultCollector {

	private static final Logger log = LoggerFactory.getLogger(StreamingResultCollectorImpl.class);

	@Reference
	private ResultRepository resultRepository;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.business.results.StreamingResultCollector#collect(org.
	 * enquery.encryptedquery.responder.data.entity.Execution)
	 */
	@Override
	public void collect(Execution execution) throws IOException {
		Validate.notNull(execution);
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

		if (!Files.isDirectory(path)) {
			log.warn("Execution '{}' output '{}' is not a directory.", execution, path);
			return;
		}

		Files.walkFileTree(path, new ResponseFileVisitor(info -> {
			try {
				try (InputStream inputStream = Files.newInputStream(info.path)) {
					resultRepository.add(execution, inputStream);
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
