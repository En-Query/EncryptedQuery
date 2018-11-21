package org.enquery.encryptedquery.querier.data.transformation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.entity.json.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.json.RetrievalCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.RetrievalResponse;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;


@Component(service = RetrievalTypeConverter.class)
public class RetrievalTypeConverter {

	@Reference(target = "(type=rest-service)")
	private ResourceUriRegistry registry;
	@Reference
	private ResultTypeConverter resultConverter;


	public Resource toResourceIdentifier(org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval jpa) {
		Validate.notNull(jpa);
		Resource result = new Resource();
		initialize(jpa, result);
		return result;
	}

	private void initialize(org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval jpa, Resource result) {
		result.setId(jpa.getId().toString());
		result.setSelfUri(registry.retrievalUri(jpa));
		result.setType(Retrieval.TYPE);
	}

	public Retrieval toJSON(org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval jpa) {
		Validate.notNull(jpa);
		Retrieval retrieval = new Retrieval();
		initialize(jpa, retrieval);
		retrieval.setResult(resultConverter.toResourceIdentifier(jpa.getResult()));
		retrieval.setDecryptionsUri(registry.decryptionsUri(jpa));
		return retrieval;
	}


	public RetrievalCollectionResponse toJSONResponse(Collection<org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval> data) {
		List<Retrieval> result = new ArrayList<>(data.size());
		data.forEach(jpaSch -> {
			result.add(toJSON(jpaSch));
		});
		return new RetrievalCollectionResponse(result);
	}

	public RetrievalResponse toJSONResponse(org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval data) {
		RetrievalResponse result = new RetrievalResponse(toJSON(data));
		result.setIncluded(JSONConverter.objectsToCollectionOfMaps(referencedObjects(data)));
		return result;
	}

	public Set<Resource> referencedObjects(org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval r) {
		return Sets.unionOf(
				resultConverter.referencedObjects(r.getResult()),
				resultConverter.toJSON(r.getResult()));
	}

}
