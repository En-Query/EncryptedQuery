package org.enquery.encryptedquery.querier.data.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.jpa.Decryption;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;

public interface DecryptionRepository {

	Decryption find(int id);

	Decryption findForRetrieval(Retrieval result, int id);

	Collection<Decryption> listForRetrieval(Retrieval retrieval);

	Decryption add(Decryption d);

	Decryption update(Decryption d);

	void delete(Decryption d);

	void deleteAll();

	InputStream payloadInputStream(Decryption d) throws IOException;

	Decryption updatePayload(Decryption d, InputStream inputStream) throws IOException;

	Decryption updateWithError(Decryption d, Exception ex) throws IOException;
}
