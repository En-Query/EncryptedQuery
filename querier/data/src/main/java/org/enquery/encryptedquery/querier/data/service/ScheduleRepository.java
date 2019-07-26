package org.enquery.encryptedquery.querier.data.service;

import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;

public interface ScheduleRepository {

	Schedule find(int id);

	Schedule findForQuery(Query query, int id);

	Schedule findByResponderId(int responderId);

	Collection<Schedule> listForQuery(Query query);

	long countForQuery(Query query);

	Schedule add(Schedule q);

	Schedule update(Schedule q);

	void delete(Query query, int id);

	void deleteAll();

	Schedule findByUUID(String executionUUID);

	Schedule addOrUpdate(Schedule schedule);

	Collection<Schedule> list(DataSource dataSource, Query query);
}
