package org.enquery.encryptedquery.querier.data.transformation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.Configuration;
import org.enquery.encryptedquery.xml.schema.Configuration.Entry;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ExecutionExport.Item;
import org.enquery.encryptedquery.xml.transformation.ExecutionExportWriter;
import org.enquery.encryptedquery.xml.transformation.ExecutionWriter;
import org.enquery.encryptedquery.xml.transformation.XMLFactories;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Convert Schedules to Execution Import XML suitable for import into Responder
 */
@Component(service = ExecutionExporter.class)
public class ExecutionExporter {

	private final Logger log = LoggerFactory.getLogger(ExecutionExporter.class);

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

	public InputStream streamByScheduleIds(List<Integer> scheduleIds) throws JAXBException, IOException {
		Validate.notNull(scheduleIds);

		List<Schedule> schedules = new ArrayList<>();
		for (Integer id : scheduleIds) {
			Schedule s = scheduleRepo.find(id);
			if (s == null) {
				log.warn("Schedule with id {} not found.", id);
				continue;
			}
			schedules.add(s);
		}
		log.info("Generating Execution Import XML for {} schedules.", schedules.size());
		return toExecutionXMLStream(schedules);
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

	public InputStream toExecutionXMLStream(List<Schedule> jpaSchedules) {
		Validate.notNull(jpaSchedules);

		try {
			PipedOutputStream out = new PipedOutputStream();
			PipedInputStream in = new PipedInputStream(out);

			threadPool.submit(() -> {
				log.info("Starting to write {} schedules.", jpaSchedules.size());
				writeExecutionList(jpaSchedules, out);
				log.info("Finished writing {} schedules.", jpaSchedules.size());
			});

			return in;

		} catch (IOException e) {
			throw new RuntimeException("Error converting Schedule to Execution.", e);
		}
	}


	private void writeExecutionList(List<Schedule> jpaSchedules, OutputStream out) {
		try (OutputStreamWriter osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				ExecutionExportWriter writer = new ExecutionExportWriter(out);) //
		{
			writer.begin(true);

			for (Schedule s : jpaSchedules) {
				writeSingleExecution(s, writer);
			}

			writer.end();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Error writing Execution to stream.", e);
		}
	}

	/**
	 * @param s
	 * @param writer
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	private void writeSingleExecution(Schedule schedule, ExecutionExportWriter writer) throws XMLStreamException, IOException {
		log.info("Emitting schedule {}.", schedule);

		try (InputStream queryBytes = queryRepo.loadQueryBytes(schedule.getQuery().getId())) {
			Validate.notNull(queryBytes, "Query bytes blob not found for query id %d", schedule.getQuery().getId());

			Item item = new Item();
			item.setDataSchemaName(schedule.getDataSource().getDataSchema().getName());
			item.setDataSourceName(schedule.getDataSource().getName());
			item.setExecution(toXMLExecution(schedule));
			writer.writeExecution(item, queryBytes);
		}

		log.info("Finished emitting schedule {}.", schedule);
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
