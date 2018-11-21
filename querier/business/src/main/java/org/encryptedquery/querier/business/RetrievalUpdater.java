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
package org.encryptedquery.querier.business;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.service.RetrievalRepository;
import org.enquery.encryptedquery.xml.transformation.ResultXMLExtractor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = RetrievalUpdater.class)
public class RetrievalUpdater {

	@Reference
	private RetrievalRepository retrievalRepo;
	@Reference
	private ExecutorService threadPool;

	public Retrieval updatePayload(Retrieval retrieval, InputStream is) throws IOException {
		try (ResultXMLExtractor extractor = new ResultXMLExtractor(threadPool)) {
			extractor.parse(is);
			return retrievalRepo.updatePayload(retrieval, extractor.getResponseInputStream());
		}
	}
}
