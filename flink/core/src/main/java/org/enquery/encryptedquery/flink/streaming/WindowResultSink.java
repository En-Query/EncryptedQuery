package org.enquery.encryptedquery.flink.streaming;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
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
public class WindowResultSink extends RichSinkFunction<WindowFinalResult> {

	private static final Logger log = LoggerFactory.getLogger(WindowResultSink.class);
	private static final long serialVersionUID = -1514106781425205801L;

	private final QueryInfo queryInfo;
	private final Map<String, String> config;
	private final String basePath;

	private transient Path rootPath;
	private transient QueryTypeConverter queryConverter;
	private transient ResponseTypeConverter converter;
	private CryptoScheme crypto;
	private CryptoSchemeRegistry registry;

	public WindowResultSink(QueryInfo queryInfo,
			Map<String, String> config,
			String basePath) {
		this.queryInfo = queryInfo;
		this.config = config;
		this.basePath = basePath;
	}

	@Override
	public void open(Configuration parameters) throws Exception {
		super.open(parameters);

		rootPath = Paths.get(basePath);
		Files.createDirectories(rootPath);

		crypto = CryptoSchemeFactory.make(config);
		registry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId == null) return null;
				if (schemeId.equals(crypto.name())) return crypto;
				return null;
			}
		};
		queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(registry);
		queryConverter.initialize();

		converter = new ResponseTypeConverter();
		converter.setQueryConverter(queryConverter);
		converter.setSchemeRegistry(registry);
		converter.initialize();
	}

	@Override
	public void close() throws Exception {
		crypto.close();
		super.close();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void invoke(WindowFinalResult window, Context context) throws Exception {
		log.info("Saving window ending in '{}' with {} elements to disk.",
				TimestampFormatter.format(window.windowMaxTimestamp),
				window.result.size());

		Response response = new Response(queryInfo);
		response.addResponseElements(window.getResult());

		final long start = window.windowMinTimestamp;
		final long end = window.windowMaxTimestamp;
		final Path inProgressFile = ResponseFileNameBuilder.makeInProgressFileName(rootPath, start, end);

		try (OutputStream stream = Files.newOutputStream(inProgressFile)) {
			converter.marshal(converter.toXML(response), stream);
		}

		final Path finalFile = ResponseFileNameBuilder.makeFinalFileName(rootPath, start, end);
		Files.move(inProgressFile, finalFile);
	}

};
