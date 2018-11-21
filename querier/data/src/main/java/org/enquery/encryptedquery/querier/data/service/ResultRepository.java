package org.enquery.encryptedquery.querier.data.service;

import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;

public interface ResultRepository {

	Result find(int id);

	Result findByResponderId(int responderId);

	Result findForSchedule(Schedule schedule, int id);

	Collection<Result> listForSchedule(Schedule schedule);

	Result add(Result r);

	Result update(Result r);

	void delete(Result r);

	void deleteAll();
}
