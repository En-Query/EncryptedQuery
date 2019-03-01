package org.enquery.encryptedquery.querier.data.service.impl;

import java.util.Collection;

import javax.persistence.EntityManager;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.ResultRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProvider;

@Component
public class ResultRepoImpl implements ResultRepository {

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
	public Result find(int id) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.find(Result.class, id));
	}

	@Override
	public Result findForSchedule(Schedule schedule, int id) {
		Validate.notNull(schedule);
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery(
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

		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select r From Result r Join r.schedule s Where  s = :schedule  ",
						Result.class)
						.setParameter("schedule", em.find(Schedule.class, schedule.getId()))
						.getResultList());
	}

	@Override
	public Result add(Result r) {
		Validate.notNull(r);
		txControl
				.build()
				.required(() -> {
					em.persist(r);
					return 0;
				});
		return r;
	}

	@Override
	public Result update(Result r) {
		Validate.notNull(r);
		return txControl
				.build()
				.required(() -> em.merge(r));
	}

	@Override
	public void delete(Result r) {
		Validate.notNull(r);
		txControl
				.build()
				.required(() -> {
					em.remove(find(r.getId()));
					return 0;
				});
	}

	@Override
	public void deleteAll() {
		txControl
				.build()
				.required(() -> {
					em.createQuery("Select r From Result r", Result.class)
							.getResultList()
							.forEach(s -> em.remove(s));
					return 0;
				});
	}

	@Override
	public Result findByResponderId(int responderId) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select r From Result r Where r.responderId = :responderId", Result.class)
						.setParameter("responderId", responderId)
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}
}
