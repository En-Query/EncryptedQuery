package org.enquery.encryptedquery.flink.streaming;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.flink.TimestampFormatter;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves the query result for a time window
 */
public class WindowResultSink implements SinkFunction<WindowFinalResult> {

	private static final Logger log = LoggerFactory.getLogger(WindowResultSink.class);
	private static final long serialVersionUID = -1514106781425205801L;

	private final QueryInfo queryInfo;
	private final Map<String, String> config;
	private final String basePath;

	private transient boolean initialized = false;
	private transient Path rootPath;

	public WindowResultSink(QueryInfo queryInfo,
			Map<String, String> config,
			String basePath) {
		this.queryInfo = queryInfo;
		this.config = config;
		this.basePath = basePath;
	}

	private void initialize() throws Exception {
		rootPath = Paths.get(basePath);
		Files.createDirectories(rootPath);

		initialized = true;
	}

	@Override
	public void invoke(WindowFinalResult window, Context context) throws Exception {

		if (!initialized) {
			initialize();
		}

		log.info("Saving window ending in '{}' with {} elements to disk.",
				TimestampFormatter.format(window.windowMaxTimestamp),
				window.result.size());

		final CryptoScheme crypto = CryptoSchemeFactory.make(config);
		final CryptoSchemeRegistry registry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId == null) return null;
				if (schemeId.equals(crypto.name())) return crypto;
				return null;
			}
		};

		QueryTypeConverter queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(registry);
		queryConverter.initialize();

		ResponseTypeConverter converter = new ResponseTypeConverter();
		converter.setQueryConverter(queryConverter);
		converter.setSchemeRegistry(registry);
		converter.initialize();

		Response response = new Response(queryInfo);
		response.addResponseElements(window.getResult());

		final long start = window.windowMinTimestamp;
		final long end = window.windowMaxTimestamp;
		final Path inProgressFile = ResponseFileNameBuilder.makeInProgressFileName(rootPath, start, end);

		// TODO: Use In progess file, then rename
		try (OutputStream stream = Files.newOutputStream(inProgressFile)) {
			converter.marshal(converter.toXML(response), stream);
		}

		final Path finalFile = ResponseFileNameBuilder.makeFinalFileName(rootPath, start, end);
		Files.move(inProgressFile, finalFile);
	}

};
