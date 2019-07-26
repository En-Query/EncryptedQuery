package org.enquery.encryptedquery.querier.data.service.impl;

import java.util.Collection;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
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
		Validate.notNull(s);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.querier.data.service.ScheduleRepository#findByUUID(java.lang.
	 * String)
	 */
	@Override
	public Schedule findByUUID(String uuid) {
		Validate.notBlank(uuid);
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select s From Schedule s Where s.uuid = :uuid", Schedule.class)
						.setParameter("uuid", uuid)
						.getResultList()
						.stream()
						.findFirst()
						.orElse(null));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.querier.data.service.ScheduleRepository#addOrUpdate(org.enquery.
	 * encryptedquery.querier.data.entity.jpa.Schedule)
	 */
	@Override
	public Schedule addOrUpdate(Schedule schedule) {
		Validate.notNull(schedule);
		return txControl
				.build()
				.required(() -> {
					if (schedule.getId() != null) return update(schedule);

					Schedule prev = null;
					if (schedule.getUuid() != null) {
						prev = findByUUID(schedule.getUuid());
					}

					if (prev == null && schedule.getResponderId() != null) {
						prev = findByResponderId(schedule.getResponderId());
					}

					if (prev != null) {
						prev.setErrorMessage(schedule.getErrorMessage());
						prev.setParameters(schedule.getParameters());
						prev.setResponderId(schedule.getResponderId());
						prev.setResponderResultsUri(schedule.getResponderResultsUri());
						prev.setResponderUri(schedule.getResponderUri());
						prev.setStartTime(schedule.getStartTime());
						prev.setStatus(schedule.getStatus());
						prev.setUuid(schedule.getUuid());
						return update(prev);
					} else {
						em.persist(schedule);
						return schedule;
					}
				});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.querier.data.service.ScheduleRepository#list(
	 * org.enquery.encryptedquery.querier.data.entity.jpa.DataSource,
	 * org.enquery.encryptedquery.querier.data.entity.jpa.Query)
	 */
	@Override
	public Collection<Schedule> list(DataSource dataSource, Query query) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> {
					StringBuilder sql = new StringBuilder();

					sql.append("Select s From Schedule s ");
					if (dataSource != null || query != null) {
						sql.append("Where ");
					}

					if (dataSource != null) {
						sql.append("s.dataSource = :dataSource ");
					}

					if (query != null) {
						if (dataSource != null) sql.append("And ");
						sql.append("s.query = :query");
					}

					TypedQuery<Schedule> jpaQuery = em.createQuery(sql.toString(), Schedule.class);

					if (dataSource != null) {
						jpaQuery.setParameter("dataSource", dataSource);
					}

					if (query != null) {
						jpaQuery.setParameter("query", query);
					}
					return jpaQuery.getResultList();
				});
	}
}
