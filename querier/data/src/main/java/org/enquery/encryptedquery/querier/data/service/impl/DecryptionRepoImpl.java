package org.enquery.encryptedquery.querier.data.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.Decryption;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.service.BlobRepository;
import org.enquery.encryptedquery.querier.data.service.DecryptionRepository;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.enquery.encryptedquery.querier.data.transformation.URIUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public class DecryptionRepoImpl implements DecryptionRepository {

	private static final String RESPONSE_FILE_NAME = "clear-response.xml";

	@Reference(target = "(osgi.unit.name=querierPersistenUnit)")
	private JpaTemplate jpa;
	@Reference
	private BlobRepository blobRepo;

	@Reference(target = "(type=blob)")
	private ResourceUriRegistry blobLocation;

	@Override
	public Decryption find(int id) {
		return jpa.txExpr(TransactionType.Supports, em -> em.find(Decryption.class, id));
	}

	@Override
	public Decryption findForRetrieval(Retrieval retrieval, int id) {
		Validate.notNull(retrieval);

		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery(
						"   Select d From Decryption d "
								+ " Join   d.retrieval r "
								+ " Where  r = :retrieval  "
								+ " And    d.id = :id",
						Decryption.class)
						.setParameter("retrieval", em.find(Retrieval.class, retrieval.getId()))
						.setParameter("id", id)
						.setHint("javax.persistence.fetchgraph", em.getEntityGraph(Decryption.ALL_ENTITY_GRAPH))
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}

	@Override
	public Collection<Decryption> listForRetrieval(Retrieval result) {
		Validate.notNull(result);

		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select d From Decryption d Join d.retrieval r Where r = :retrieval ",
						Decryption.class)
						.setParameter("retrieval", em.find(Retrieval.class, result.getId()))
						.getResultList());
	}

	@Override
	public Decryption add(Decryption r) {
		Validate.notNull(r);
		jpa.tx(em -> em.persist(r));
		return r;
	}

	@Override
	public Decryption update(Decryption r) {
		Validate.notNull(r);
		return jpa.txExpr(TransactionType.Required, em -> em.merge(r));
	}

	@Override
	public void delete(Decryption retrieval) {
		Validate.notNull(retrieval);
		jpa.tx(em -> {
			Decryption r = find(retrieval.getId());
			if (r != null) {
				em.remove(r);
				deleteDecryptionBlobs(r);
			}
		});
	}

	private void deleteDecryptionBlobs(Decryption r) {
		try {
			if (r.getPayloadUri() != null) {
				blobRepo.delete(new URL(r.getPayloadUri()));
			}
		} catch (MalformedURLException e) {
			throw new RuntimeException("Error deleting Decryption payload blob.", e);
		}
	}

	@Override
	public void deleteAll() {
		jpa.tx(em -> {
			em.createQuery("Select d From Decryption d", Decryption.class)
					.getResultList()
					.forEach(r -> {
						em.remove(r);
						deleteDecryptionBlobs(r);
					});
		});
	}

	@Override
	public InputStream payloadInputStream(Decryption retrieval) throws IOException {
		Validate.notNull(retrieval);
		Decryption r = jpa.txExpr(TransactionType.Supports,
				em -> em.find(Decryption.class, retrieval.getId()));

		Validate.notNull(r, "Decryption with id %d not found.", retrieval.getId());

		String url = r.getPayloadUri();
		if (url == null) return null;
		return blobRepo.inputStream(new URL(url));
	}

	@Override
	public Decryption updatePayload(Decryption d, InputStream inputStream) throws IOException {
		return jpa.txExpr(
				TransactionType.Required,
				em -> {
					Decryption r = em.find(Decryption.class, d.getId());
					Validate.notNull(r);

					URL url = makeUrl(r);
					blobRepo.save(inputStream, url);

					r.setPayloadUri(url.toString());
					return update(r);
				});
	}

	private URL makeUrl(Decryption d) {
		Validate.notNull(d);
		Validate.notNull(d.getRetrieval());
		try {
			String url = URIUtils.concat(blobLocation.decryptionUri(d), RESPONSE_FILE_NAME).toString();
			return new URL(url);
		} catch (MalformedURLException e) {
			throw new RuntimeException("Error making payload URL for retrieval: " + d, e);
		}
	}

	@Override
	public Decryption updateWithError(Decryption d, Exception exception) throws IOException {
		Validate.notNull(d);
		Validate.notNull(exception);

		try (OutputStream os = new ByteArrayOutputStream();
				PrintStream ps = new PrintStream(os);) {
			exception.printStackTrace(ps);
			d.setErrorMessage(os.toString());
		}

		d.setPayloadUri(null);
		return update(d);
	}
}
