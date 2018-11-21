package org.enquery.encryptedquery.querier.data.transformation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.entity.json.Result;
import org.enquery.encryptedquery.querier.data.entity.json.ResultCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ResultResponse;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;


@Component(service = ResultTypeConverter.class)
public class ResultTypeConverter {

	@Reference(target = "(type=rest-service)")
	private ResourceUriRegistry registry;
	@Reference
	private ScheduleTypeConverter scheduleConverter;

	public Resource toResourceIdentifier(org.enquery.encryptedquery.querier.data.entity.jpa.Result jpa) {
		Validate.notNull(jpa);
		Resource result = new Resource();
		initialize(jpa, result);
		return result;
	}

	private void initialize(org.enquery.encryptedquery.querier.data.entity.jpa.Result jpa, Resource result) {
		result.setId(jpa.getId().toString());
		result.setSelfUri(registry.resultUri(jpa));
		result.setType(Result.TYPE);
	}

	public Result toJSON(org.enquery.encryptedquery.querier.data.entity.jpa.Result jpa) {
		Validate.notNull(jpa);

		Result result = new Result();
		initialize(jpa, result);
		result.setSchedule(scheduleConverter.toResourceIdentifier(jpa.getSchedule()));
		result.setRetrievalsUri(registry.retrievalsUri(jpa));
		return result;
	}

	public org.enquery.encryptedquery.querier.data.entity.jpa.Result toJPA(Result json) throws IOException {
		Validate.notNull(json);
		org.enquery.encryptedquery.querier.data.entity.jpa.Result result = new org.enquery.encryptedquery.querier.data.entity.jpa.Result();
		return result;
	}

	public ResultCollectionResponse toJSONResponse(Collection<org.enquery.encryptedquery.querier.data.entity.jpa.Result> data) {
		List<Result> result = new ArrayList<>(data.size());
		data.forEach(jpaSch -> {
			result.add(toJSON(jpaSch));
		});
		return new ResultCollectionResponse(result);
	}

	public ResultResponse toJSONResponse(org.enquery.encryptedquery.querier.data.entity.jpa.Result jpa) {
		ResultResponse result = new ResultResponse(toJSON(jpa));
		result.setIncluded(JSONConverter.objectsToCollectionOfMaps(referencedObjects(jpa)));
		return result;
	}

	public Set<Resource> referencedObjects(org.enquery.encryptedquery.querier.data.entity.jpa.Result schedule) {
		return Sets.unionOf(
				scheduleConverter.referencedObjects(schedule.getSchedule()),
				scheduleConverter.toJSON(schedule.getSchedule()));
	}
}
