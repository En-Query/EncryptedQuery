package org.enquery.encryptedquery.querier.data.service.impl;

import java.util.Collection;

import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public class ScheduleRepoImpl implements ScheduleRepository {

	@Reference(target = "(osgi.unit.name=querierPersistenUnit)")
	private JpaTemplate jpa;

	@Override
	public Schedule find(int id) {
		return jpa.txExpr(TransactionType.Supports, em -> em.find(Schedule.class, id));
	}

	@Override
	public Schedule findForQuery(Query query, int id) {
		return jpa.txExpr(TransactionType.Supports,
				em -> {
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
		return jpa.txExpr(TransactionType.Supports,
				em -> {
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
		jpa.tx(em -> {
			em.persist(s);
		});
		return s;
	}

	@Override
	public Schedule update(Schedule s) {
		return jpa.txExpr(TransactionType.Required, em -> em.merge(s));
	}

	@Override
	public void delete(Query query, int id) {
		jpa.tx(em -> {
			Schedule schedule = findForQuery(query, id);
			if (schedule != null) em.remove(schedule);
		});
	}

	@Override
	public void deleteAll() {
		jpa.tx(em -> {
			em.createQuery("Select s From Schedule s", Schedule.class)
					.getResultList()
					.forEach(s -> em.remove(s));
		});
	}

	@Override
	public Schedule findByResponderId(int responderId) {
		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select s From Schedule s Where s.responderId = :responderId", Schedule.class)
						.setParameter("responderId", responderId)
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}

	@Override
	public long countForQuery(Query query) {
		return jpa.txExpr(TransactionType.Supports,
				em -> {
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
