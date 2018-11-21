package org.enquery.encryptedquery.querier.data.transformation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.json.Decryption;
import org.enquery.encryptedquery.querier.data.entity.json.DecryptionCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DecryptionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;


@Component(service = DecryptionTypeConverter.class)
public class DecryptionTypeConverter {

	@Reference(target = "(type=rest-service)")
	private ResourceUriRegistry registry;
	@Reference
	private RetrievalTypeConverter retrievalConverter;

	public Resource toResourceIdentifier(org.enquery.encryptedquery.querier.data.entity.jpa.Decryption jpa) {
		Validate.notNull(jpa);
		Resource result = new Resource();
		initialize(jpa, result);
		return result;
	}

	private void initialize(org.enquery.encryptedquery.querier.data.entity.jpa.Decryption jpa, Resource result) {
		result.setId(jpa.getId().toString());
		result.setSelfUri(registry.decryptionUri(jpa));
		result.setType(Decryption.TYPE);
	}

	public Decryption toJSON(org.enquery.encryptedquery.querier.data.entity.jpa.Decryption jpa) {
		Validate.notNull(jpa);
		Decryption decryption = new Decryption();
		initialize(jpa, decryption);
		decryption.setRetrieval(retrievalConverter.toResourceIdentifier(jpa.getRetrieval()));
		return decryption;
	}


	public DecryptionCollectionResponse toJSONResponse(Collection<org.enquery.encryptedquery.querier.data.entity.jpa.Decryption> data) {
		List<Decryption> result = new ArrayList<>(data.size());
		data.forEach(jpaSch -> {
			result.add(toJSON(jpaSch));
		});
		return new DecryptionCollectionResponse(result);
	}


	public DecryptionResponse toJSONResponse(org.enquery.encryptedquery.querier.data.entity.jpa.Decryption data) {
		DecryptionResponse result = new DecryptionResponse(toJSON(data));
		result.setIncluded(JSONConverter.objectsToCollectionOfMaps(referencedObjects(data)));
		return result;
	}

	public Set<Resource> referencedObjects(org.enquery.encryptedquery.querier.data.entity.jpa.Decryption d) {
		return Sets.unionOf(
				retrievalConverter.referencedObjects(d.getRetrieval()),
				retrievalConverter.toJSON(d.getRetrieval()));
	}
}
