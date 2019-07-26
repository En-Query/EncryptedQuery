package org.enquery.encryptedquery.querier.data.transformation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.Configuration;
import org.enquery.encryptedquery.xml.schema.Configuration.Entry;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.transformation.ExecutionWriter;
import org.enquery.encryptedquery.xml.transformation.XMLFactories;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;


/**
 * Convert Schedules to Execution Import XML suitable for import into Responder
 */
@Component(service = ExecutionExporter.class)
public class ExecutionExporter {

	@Reference
	private QueryRepository queryRepo;
	@Reference
	private ExecutorService threadPool;
	@Reference
	private ScheduleRepository scheduleRepo;

	ExecutorService getThreadPool() {
		return threadPool;
	}

	void setThreadPool(ExecutorService threadPool) {
		this.threadPool = threadPool;
	}

	public InputStream streamSingle(Schedule jpaSchedule) {
		Validate.notNull(jpaSchedule);
		try {
			PipedOutputStream out = new PipedOutputStream();
			PipedInputStream in = new PipedInputStream(out);

			threadPool.submit(() -> {
				try {
					try (OutputStreamWriter osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
							BufferedWriter bw = new BufferedWriter(osw);
							ExecutionWriter writer = new ExecutionWriter(out);
							InputStream queryBytes = queryRepo.loadQueryBytes(jpaSchedule.getQuery().getId())) {

						Validate.notNull(queryBytes, "Query bytes blob not found for query id %d", jpaSchedule.getQuery().getId());

						Execution ex = toXMLExecution(jpaSchedule);
						writer.writeExecution(true, ex, queryBytes);
					}
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException("Error writing Execution to stream.", e);
				}
			});

			return in;

		} catch (IOException e) {
			throw new RuntimeException("Error converting Schedule to Execution.", e);
		}
	}

	public Execution toXMLExecution(Schedule schedule) {
		Execution result = new Execution();
		result.setSchemaVersion(Versions.EXECUTION_BI);
		result.setScheduledFor(XMLFactories.toUTCXMLTime(schedule.getStartTime()));
		result.setUuid(schedule.getUuid());
		result.setConfiguration(makeConfiguration(schedule));
		return result;
	}

	/**
	 * @param schedule
	 * @return
	 */
	private Configuration makeConfiguration(Schedule schedule) {
		Map<String, String> map = JSONConverter.toMapStringString(schedule.getParameters());

		if (map == null) return null;
		if (map.isEmpty()) return null;

		Configuration result = new Configuration();
		for (java.util.Map.Entry<String, String> entry : map.entrySet()) {
			Entry e = new Entry();
			e.setKey(entry.getKey());
			e.setValue(entry.getValue());
			result.getEntry().add(e);
		}

		return result;
	}

}
