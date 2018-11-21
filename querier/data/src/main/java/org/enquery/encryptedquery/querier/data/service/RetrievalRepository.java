package org.enquery.encryptedquery.querier.data.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;

public interface RetrievalRepository {

	Retrieval find(int id);

	Retrieval findForResult(Result result, int id);

	Collection<Retrieval> listForResult(Result result);

	Retrieval add(Retrieval r);

	Retrieval update(Retrieval r);

	void delete(Retrieval r);

	void deleteAll();

	InputStream payloadInputStream(Retrieval r) throws IOException;

	Retrieval updatePayload(Retrieval r, InputStream inputStream) throws IOException;

	Retrieval updateWithError(Retrieval r, Exception ex) throws IOException;
}
