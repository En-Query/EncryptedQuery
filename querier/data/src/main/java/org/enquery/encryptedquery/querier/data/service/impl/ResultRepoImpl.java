package org.enquery.encryptedquery.querier.data.service.impl;

import java.util.Collection;

import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.ResultRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public class ResultRepoImpl implements ResultRepository {

	@Reference(target = "(osgi.unit.name=querierPersistenUnit)")
	private JpaTemplate jpa;

	@Override
	public Result find(int id) {
		return jpa.txExpr(TransactionType.Supports, em -> em.find(Result.class, id));
	}

	@Override
	public Result findForSchedule(Schedule schedule, int id) {
		Validate.notNull(schedule);
		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery(
						"   Select r From Result r "
								+ " Join r.schedule s "
								+ " Where  s = :schedule  "
								+ " And    r.id = :id",
						Result.class)
						.setParameter("schedule", em.find(Schedule.class, schedule.getId()))
						.setParameter("id", id)
						.setHint("javax.persistence.fetchgraph", em.getEntityGraph(Result.ALL_ENTITY_GRAPH))
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}

	@Override
	public Collection<Result> listForSchedule(Schedule schedule) {
		Validate.notNull(schedule);

		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select r From Result r Join r.schedule s Where  s = :schedule  ",
						Result.class)
						.setParameter("schedule", em.find(Schedule.class, schedule.getId()))
						.getResultList());
	}

	@Override
	public Result add(Result r) {
		Validate.notNull(r);
		jpa.tx(em -> em.persist(r));
		return r;
	}

	@Override
	public Result update(Result r) {
		Validate.notNull(r);
		return jpa.txExpr(TransactionType.Required, em -> em.merge(r));
	}

	@Override
	public void delete(Result r) {
		Validate.notNull(r);
		jpa.tx(em -> em.remove(find(r.getId())));
	}

	@Override
	public void deleteAll() {
		jpa.tx(em -> {
			em.createQuery("Select r From Result r", Result.class)
					.getResultList()
					.forEach(s -> em.remove(s));
		});
	}

	@Override
	public Result findByResponderId(int responderId) {
		return jpa.txExpr(TransactionType.Supports,
				em -> em.createQuery("Select r From Result r Where r.responderId = :responderId", Result.class)
						.setParameter("responderId", responderId)
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}
}
