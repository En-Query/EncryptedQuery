package org.enquery.encryptedquery.querier.data.transformation;

import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A customized Jackson Data Format that uses JSON View from header Camel header "CAMEL_JSON_VIEW".
 * This allows the same data format to be used with dynamic views.
 */
public class JacksonDFWithDynamicViews extends JacksonDataFormat {

	private static final Logger log = LoggerFactory.getLogger(JacksonDFWithDynamicViews.class);

	private static final String CAMEL_JSON_VIEW = "CAMEL_JSON_VIEW";

	public String getDataFormatName() {
		return "JacksonDFWithDynamicViews";
	}

	public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
		final boolean isDebugging = log.isDebugEnabled();

		// We don't override content type to support vendor content types and versions.

		final ObjectMapper objectMapper = getObjectMapper();
		objectMapper.disable(MapperFeature.DEFAULT_VIEW_INCLUSION);
		objectMapper.setSerializationInclusion(Include.NON_NULL);

		String viewClassName = exchange.getIn().getHeader(CAMEL_JSON_VIEW, String.class);
		if (viewClassName == null) {
			if (isDebugging) log.debug("No view class name found in header {}. Marashalling without view.", CAMEL_JSON_VIEW);
			objectMapper.writer().writeValue(stream, graph);
		} else {
			if (isDebugging) log.debug("Marshaling with view '{}'.", viewClassName);
			objectMapper.writerWithView(Class.forName(viewClassName)).writeValue(stream, graph);
		}
	}
}
