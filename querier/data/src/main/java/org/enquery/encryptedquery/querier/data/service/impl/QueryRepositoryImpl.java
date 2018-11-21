package org.enquery.encryptedquery.querier.data.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;
import org.enquery.encryptedquery.querier.data.service.BlobRepository;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.enquery.encryptedquery.querier.data.transformation.URIUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public class QueryRepositoryImpl implements QueryRepository {

	private static final String QUERY_FILE_NAME = "query.xml";
	private static final String QUERY_KEYS_FILE_NAME = "query-key.xml";

	@Reference(target = "(osgi.unit.name=querierPersistenUnit)")
	private JpaTemplate jpa;

	@Reference
	private BlobRepository blobRepo;

	@Reference(target = "(type=blob)")
	private ResourceUriRegistry queryLocation;

	@Reference(target = "(type=query-key)")
	private ResourceUriRegistry queryKeysRegistry;

	@Override
	public Query find(int id) {
		return jpa.txExpr(TransactionType.Supports,
				em -> em.find(Query.class, id, fetchAllAssociationsHint(em)));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Query findByName(String name) {
		return (Query) jpa.txExpr(TransactionType.Supports,
				em -> em
						.createQuery("Select q From Query q Where q.name = :name")
						.setParameter("name", name)
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<Query> list() {
		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select q From Query q").getResultList());
	}

	@Override
	public Query add(Query q) {
		jpa.tx(em -> {
			em.persist(q);
		});
		return q;
	}

	@Override
	public Query update(Query q) {
		return jpa.txExpr(TransactionType.Required, em -> em.merge(q));
	}

	@Override
	public void delete(int id) {
		jpa.tx(em -> {
			Query q = find(id);
			if (q != null) {
				em.remove(q);
				deleteQueryBlobs(q);
			}
		});
	}

	/**
	 * Delete external blobs
	 * 
	 * @param q
	 */
	private void deleteQueryBlobs(Query q) {
		try {
			if (q.getQueryUrl() != null) {
				blobRepo.delete(new URL(q.getQueryUrl()));
				q.setQueryUrl(null);
			}
			if (q.getQueryKeyUrl() != null) {
				blobRepo.delete(new URL(q.getQueryKeyUrl()));
				q.setQueryKeyUrl(null);
			}
		} catch (MalformedURLException e) {
			throw new RuntimeException("Error deleting Query blobs.", e);
		}
	}

	@Override
	public void deleteAll() {
		jpa.tx(em -> {
			list().forEach(q -> {
				em.remove(q);
				deleteQueryBlobs(q);
			});
		});
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<String> listNames() {
		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select q.name From Query q").getResultList());
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<Query> withQuerySchema(int querySchemaId) {
		return jpa.txExpr(
				TransactionType.Supports,
				em -> em
						.createQuery("Select q From Query q "
								+ "  Join q.querySchema qs "
								+ "  Where qs.id = :querySchemaId")
						.setParameter("querySchemaId", querySchemaId)
						.getResultList());
	}

	@Override
	public boolean isGenerated(int queryId) {
		Long result = jpa.txExpr(
				TransactionType.Supports,
				em -> {
					TypedQuery<Long> query = em.createQuery(
							"Select count(q) From Query q "
									+ "  Where q.id  = :queryId"
									+ "  And q.queryUrl Is Not Null ",
							Long.class);
					query.setParameter("queryId", queryId);
					return query.getSingleResult();
				});
		return result > 0;
	}

	@Override
	public Query findForQuerySchema(QuerySchema querySchema, int id) {
		Validate.notNull(querySchema);

		return jpa.txExpr(
				TransactionType.Supports,
				em -> em.createQuery("From Query q "
						+ "Where q.id = :id "
						+ "And   q.querySchema = :querySchema ",
						Query.class)
						.setParameter("id", id)
						.setParameter("querySchema", em.find(QuerySchema.class, querySchema.getId()))
						.setHint("javax.persistence.fetchgraph", em.getEntityGraph(Query.ALL_ENTITY_GRAPH))
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}

	@Override
	public Query updateQueryBytes(int queryId, InputStream inputStream) throws IOException {

		return jpa.txExpr(
				TransactionType.Required,
				em -> {
					URL url = makeUrl(queryLocation, find(queryId), QUERY_FILE_NAME);
					blobRepo.save(inputStream, url);

					em.createQuery("Update Query q Set q.queryUrl=:url Where q.id=:id")
							.setParameter("id", queryId)
							.setParameter("url", url.toString())
							.executeUpdate();

					return find(queryId);
				});
	}

	private URL makeUrl(ResourceUriRegistry registry, Query query, String fileName) {
		Validate.notNull(query);
		Validate.notNull(query.getQuerySchema());
		try {
			String url = URIUtils.concat(registry.queryUri(query), fileName).toString();
			return new URL(url);
		} catch (MalformedURLException e) {
			throw new RuntimeException("Error making URL for query: " + query, e);
		}
	}

	@Override
	public InputStream loadQueryBytes(int queryId) throws IOException {
		Query query = find(queryId);
		Validate.notNull(query, "Query with id %d not found.", queryId);

		String url = query.getQueryUrl();
		if (url == null) return null;

		return blobRepo.inputStream(new URL(url));
	}

	@Override
	public Query updateQueryKeyBytes(int queryId, InputStream inputStream) throws IOException {
		return jpa.txExpr(
				TransactionType.Required,
				em -> {
					URL url = makeUrl(queryKeysRegistry, find(queryId), QUERY_KEYS_FILE_NAME);
					blobRepo.save(inputStream, url);

					em.createQuery("Update Query q Set q.queryKeyUrl=:url Where q.id=:id")
							.setParameter("id", queryId)
							.setParameter("url", url.toString())
							.executeUpdate();

					return find(queryId);
				});
	}

	@Override
	public InputStream loadQueryKeyBytes(int queryId) throws IOException {
		Query query = find(queryId);
		Validate.notNull(query);

		String u = query.getQueryKeyUrl();
		if (u == null) return null;

		return blobRepo.inputStream(new URL(u));
	}

	@SuppressWarnings("rawtypes")
	private Map<String, Object> fetchAllAssociationsHint(EntityManager em) {
		EntityGraph graph = em.getEntityGraph(Query.ALL_ENTITY_GRAPH);
		Map<String, Object> hints = new HashMap<>();
		hints.put("javax.persistence.fetchgraph", graph);
		return hints;
	}

	@Override
	public Query updateWithError(Query query, Exception exception) throws IOException {
		Validate.notNull(query);
		Validate.notNull(exception);

		Query q = find(query.getId());

		try (OutputStream os = new ByteArrayOutputStream();
				PrintStream ps = new PrintStream(os);) {
			exception.printStackTrace(ps);
			q.setErrorMessage(os.toString());
		}

		deleteQueryBlobs(q);

		return update(q);
	}
}
