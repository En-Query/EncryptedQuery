package org.enquery.encryptedquery.querier.data.service.impl;

import java.util.Collection;

import javax.persistence.EntityManager;

import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProvider;

@Component
public class ScheduleRepoImpl implements ScheduleRepository {

	@Reference(target = "(osgi.unit.name=querierPersistenUnit)")
	private JPAEntityManagerProvider provider;
	@Reference
	private TransactionControl txControl;
	private EntityManager em;

	@Activate
	void init() {
		em = provider.getResource(txControl);
	}

	@Override
	public Schedule find(int id) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.find(Schedule.class, id));
	}

	@Override
	public Schedule findForQuery(Query query, int id) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> {
					return em.createQuery(
							"   Select s From Schedule s "
									+ " Join   s.query q "
									+ " Where  q = :query  "
									+ " And    s.id = :id",
							Schedule.class)
							.setParameter("query", em.find(Query.class, query.getId()))
							.setParameter("id", id)
							.setHint("javax.persistence.fetchgraph", em.getEntityGraph(Schedule.ALL_ENTITY_GRAPH))
							.getResultList()
							.stream()
							.findFirst()
							.orElse(null);
				});
	}

	@Override
	public Collection<Schedule> listForQuery(Query query) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> {
					return em
							.createQuery(
									"   Select s From Schedule s "
											+ " Join   s.query q "
											+ " Where  q = :query  ",
									Schedule.class)
							.setParameter("query", em.find(Query.class, query.getId()))
							.getResultList();
				});
	}

	@Override
	public Schedule add(Schedule s) {
		return txControl
				.build()
				.required(() -> {
					em.persist(s);
					return s;
				});
	}

	@Override
	public Schedule update(Schedule s) {
		return txControl
				.build()
				.required(() -> em.merge(s));
	}

	@Override
	public void delete(Query query, int id) {
		txControl
				.build()
				.required(() -> {
					Schedule schedule = findForQuery(query, id);
					if (schedule != null) em.remove(schedule);
					return 0;
				});
	}

	@Override
	public void deleteAll() {
		txControl
				.build()
				.required(() -> {
					em.createQuery("Select s From Schedule s", Schedule.class)
							.getResultList()
							.forEach(s -> em.remove(s));
					return 0;
				});
	}

	@Override
	public Schedule findByResponderId(int responderId) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select s From Schedule s Where s.responderId = :responderId", Schedule.class)
						.setParameter("responderId", responderId)
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}

	@Override
	public long countForQuery(Query query) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> {
					return em
							.createQuery(
									"   Select count(s) From Schedule s "
											+ " Join   s.query q "
											+ " Where  q = :query  ",
									Long.class)
							.setParameter("query", em.find(Query.class, query.getId()))
							.getSingleResult();
				});
	}
}
