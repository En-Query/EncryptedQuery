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
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.service.BlobRepository;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.enquery.encryptedquery.querier.data.service.RetrievalRepository;
import org.enquery.encryptedquery.querier.data.transformation.URIUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public class RetrievalRepoImpl implements RetrievalRepository {

	private static final String RESPONSE_FILE_NAME = "response.xml";

	@Reference(target = "(osgi.unit.name=querierPersistenUnit)")
	private JpaTemplate jpa;
	@Reference
	private BlobRepository blobRepo;

	@Reference(target = "(type=blob)")
	private ResourceUriRegistry blobLocation;

	@Override
	public Retrieval find(int id) {
		return jpa.txExpr(TransactionType.Supports, em -> em.find(Retrieval.class, id));
	}

	@Override
	public Retrieval findForResult(Result result, int id) {
		Validate.notNull(result);
		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery(
						"   Select r From Retrieval r "
								+ " Join r.result res "
								+ " Where  res = :result  "
								+ " And    r.id = :id",
						Retrieval.class)
						.setParameter("result", em.find(Result.class, result.getId()))
						.setParameter("id", id)
						.setHint("javax.persistence.fetchgraph", em.getEntityGraph(Retrieval.ALL_ENTITY_GRAPH))
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}

	@Override
	public Collection<Retrieval> listForResult(Result result) {
		Validate.notNull(result);

		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select r From Retrieval r Join r.result res Where res = :result  ",
						Retrieval.class)
						.setParameter("result", em.find(Result.class, result.getId()))
						.getResultList());
	}

	@Override
	public Retrieval add(Retrieval r) {
		Validate.notNull(r);
		jpa.tx(em -> em.persist(r));
		return r;
	}

	@Override
	public Retrieval update(Retrieval r) {
		Validate.notNull(r);
		return jpa.txExpr(TransactionType.Required, em -> em.merge(r));
	}

	@Override
	public void delete(Retrieval retrieval) {
		Validate.notNull(retrieval);
		jpa.tx(em -> {
			Retrieval r = find(retrieval.getId());
			if (r != null) {
				em.remove(r);
				deleteRetrievalBlobs(r);
			}
		});
	}

	private void deleteRetrievalBlobs(Retrieval r) {
		try {
			if (r.getPayloadUri() != null) {
				blobRepo.delete(new URL(r.getPayloadUri()));
			}
		} catch (MalformedURLException e) {
			throw new RuntimeException("Error deleting Retrieval payload blob.", e);
		}
	}

	@Override
	public void deleteAll() {
		jpa.tx(em -> {
			em.createQuery("Select r From Retrieval r", Retrieval.class)
					.getResultList()
					.forEach(r -> {
						em.remove(r);
						deleteRetrievalBlobs(r);
					});
		});
	}

	@Override
	public InputStream payloadInputStream(Retrieval retrieval) throws IOException {
		Validate.notNull(retrieval);
		Retrieval r = jpa.txExpr(TransactionType.Supports,
				em -> em.find(Retrieval.class, retrieval.getId()));

		Validate.notNull(r, "Retrieval with id %d not found.", retrieval.getId());

		String url = r.getPayloadUri();
		if (url == null) return null;
		return blobRepo.inputStream(new URL(url));
	}

	@Override
	public Retrieval updatePayload(Retrieval retrieval, InputStream inputStream) throws IOException {
		return jpa.txExpr(
				TransactionType.Required,
				em -> {
					Retrieval r = em.find(Retrieval.class, retrieval.getId());
					Validate.notNull(r, "Retrieval with id %d not found", retrieval.getId());

					URL url = makeUrl(r);
					blobRepo.save(inputStream, url);

					r.setPayloadUri(url.toString());
					return update(r);
				});
	}

	private URL makeUrl(Retrieval retrieval) {
		Validate.notNull(retrieval);
		try {
			String url = URIUtils.concat(blobLocation.retrievalUri(retrieval), RESPONSE_FILE_NAME).toString();

			return new URL(url);
		} catch (MalformedURLException e) {
			throw new RuntimeException("Error making payload URL for retrieval: " + retrieval, e);
		}
	}

	@Override
	public Retrieval updateWithError(Retrieval retrieval, Exception exception) throws IOException {
		Validate.notNull(retrieval);
		Validate.notNull(exception);

		try (OutputStream os = new ByteArrayOutputStream();
				PrintStream ps = new PrintStream(os);) {
			exception.printStackTrace(ps);
			retrieval.setErrorMessage(os.toString());
		}

		retrieval.setPayloadUri(null);
		return update(retrieval);
	}
}
