package org.enquery.encryptedquery.querier.data.transformation;

import java.util.Collection;

import org.apache.camel.Converter;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.entity.json.ResourceCollectionResponse;

@Converter
public class ResourceTypeConverter {

	@Converter
	public ResourceCollectionResponse toDataSchemaCollectionResponse(Collection<Resource> data) {
		ResourceCollectionResponse result = new ResourceCollectionResponse();
		result.setData(data);
		return result;
	}
}
