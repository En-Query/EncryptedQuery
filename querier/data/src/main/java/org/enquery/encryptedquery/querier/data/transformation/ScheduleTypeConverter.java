package org.enquery.encryptedquery.querier.data.transformation;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.entity.json.Schedule;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleResponse;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;


@Component(service = ScheduleTypeConverter.class)
public class ScheduleTypeConverter {

	@Reference(target = "(type=rest-service)")
	private ResourceUriRegistry registry;
	@Reference
	private QueryRepository queryRepo;
	@Reference
	private DataSourceTypeConverter dataSourceConverter;
	@Reference
	private QueryTypeConverter queryConverter;

	public Resource toResourceIdentifier(org.enquery.encryptedquery.querier.data.entity.jpa.Schedule jpa) {
		Validate.notNull(jpa);
		Resource result = new Resource();
		initialize(jpa, result);
		return result;
	}

	private void initialize(org.enquery.encryptedquery.querier.data.entity.jpa.Schedule jpa, Resource result) {
		result.setId(jpa.getId().toString());
		result.setSelfUri(registry.scheduleUri(jpa));
		result.setType(Schedule.TYPE);
	}

	public Schedule toJSON(org.enquery.encryptedquery.querier.data.entity.jpa.Schedule jpa) {
		Validate.notNull(jpa);
		Schedule result = new Schedule();
		initialize(jpa, result);

		result.setStartTime(jpa.getStartTime());
		result.setStatus(jpa.getStatus());
		result.setParameters(JSONConverter.toMapStringString(jpa.getParameters()));
		result.setQuery(queryConverter.toResourceIdentifier(jpa.getQuery()));
		result.setDataSource(dataSourceConverter.toResourceIdentifier(jpa.getDataSource()));
		result.setResultsUri(registry.resultsUri(jpa));
		return result;
	}

	public Set<Resource> referencedObjects(org.enquery.encryptedquery.querier.data.entity.jpa.Schedule schedule) {
		return Sets.union(
				Sets.unionOf(
						queryConverter.referencedObjects(schedule.getQuery()),
						queryConverter.toJSON(schedule.getQuery())),
				Sets.unionOf(
						dataSourceConverter.referencedObjects(schedule.getDataSource()),
						dataSourceConverter.toJSON(schedule.getDataSource())));
	}

	public ScheduleCollectionResponse toJSONResponse(Collection<org.enquery.encryptedquery.querier.data.entity.jpa.Schedule> data) {
		List<Schedule> result = new ArrayList<>(data.size());
		data.forEach(jpaSch -> {
			result.add(toJSON(jpaSch));
		});
		return new ScheduleCollectionResponse(result);
	}

	public ScheduleResponse toJSONResponse(org.enquery.encryptedquery.querier.data.entity.jpa.Schedule jpa) throws JsonParseException, JsonMappingException, IOException, URISyntaxException {
		ScheduleResponse result = new ScheduleResponse(toJSON(jpa));
		result.setIncluded(JSONConverter.objectsToCollectionOfMaps(referencedObjects(jpa)));
		return (result);
	}

}
