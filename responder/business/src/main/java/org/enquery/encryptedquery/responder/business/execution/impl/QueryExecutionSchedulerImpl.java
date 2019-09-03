/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.responder.business.execution.impl;

import static org.quartz.DateBuilder.evenMinuteDate;
import static org.quartz.TriggerBuilder.newTrigger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.json.JSONStringConverter;
import org.enquery.encryptedquery.responder.business.execution.ExecutionStatusUpdater;
import org.enquery.encryptedquery.responder.business.execution.QueryExecutionScheduler;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.enquery.encryptedquery.responder.data.service.QueryRunner;
import org.enquery.encryptedquery.responder.data.service.ResultRepository;
import org.enquery.encryptedquery.xml.schema.QueryInfo;
import org.enquery.encryptedquery.xml.transformation.QueryReader;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true,
		service = QueryExecutionScheduler.class,
		configurationPid = "encrypted.query.responder.business")
public class QueryExecutionSchedulerImpl implements JobFactory, Job, QueryExecutionScheduler {

	private static final Logger log = LoggerFactory.getLogger(QueryExecutionSchedulerImpl.class);

	private Scheduler scheduler;
	private String group;
	private static final String OUTPUT_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm";

	private static final String EXECUTION_ID = "execution.id";

	@Reference
	private DataSourceRegistry dataSourceRegistry;
	@Reference
	private ExecutionRepository executionRepository;
	@Reference
	private ResultRepository resultRepository;
	@Reference
	private QueryTypeConverter queryConverter;
	@Reference
	private ExecutionStatusUpdater statusUpdater;
	@Reference
	private ExecutorService threadPool;

	// force this component to wait until the data source is ready
	@Reference(target = "(dataSourceName=responder)")
	private javax.sql.DataSource sqlDatasource;

	private Path outputPath;

	@Activate
	void activate(Map<String, String> config) throws SchedulerException, IOException {
		// Initialize scheduler from properties
		Properties properties = defaultConfiguration();

		// override
		properties.putAll(config);

		outputPath = Paths.get(config.getOrDefault("query.execution.results.path", "data/responses")).toAbsolutePath();
		Files.createDirectories(outputPath);

		SchedulerFactory schedFact = new org.quartz.impl.StdSchedulerFactory(properties);

		scheduler = schedFact.getScheduler();
		scheduler.setJobFactory(this);
		scheduler.start();

		group = scheduler.getSchedulerInstanceId();
	}

	/**
	 * Default Quartz configuration, it can be overriden with config file:
	 * encrypted.query.responder.business.cfg
	 * 
	 * @return
	 */
	private Properties defaultConfiguration() {
		Properties p = new Properties();

		// this thead count controls how many jobs in parallel are executed
		p.setProperty("org.quartz.threadPool.threadCount", "1");
		p.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
		p.setProperty("org.quartz.jobStore.useProperties", "true");
		p.setProperty("org.quartz.dataSource.responder.jndiURL", "osgi:service/responder");
		p.setProperty("org.quartz.jobStore.dataSource", "responder");
		p.setProperty("org.quartz.scheduler.threadsInheritContextClassLoaderOfInitializer", "true");
		p.setProperty("org.quartz.threadPool.threadsInheritContextClassLoaderOfInitializingThread", "true");
		return p;
	}

	@Deactivate
	void deactivate() throws SchedulerException {
		scheduler.shutdown();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.business.execution.impl.QueryExecutionScheduler#add(org.
	 * enquery. encryptedquery.responder.data.entity.Execution)
	 */
	@Override
	public void add(Execution execution) throws JobExecutionException {
		log.info("Scheduling: " + execution);
		validateExecution(execution);

		final DataSource dataSource = findDataSource(execution);
		QueryInfo info = extractQueryInfo(execution);
		validateQueryAgainstDataSource(dataSource, info);

		try {
			JobDetail job = makeOrGetJobDetail(execution);
			Trigger trigger = makeTrigger(execution);

			Date ft = scheduler.scheduleJob(job, trigger);
			log.info("Job '" + job.getKey() + "' will run at: " + ft);
		} catch (SchedulerException e) {
			throw new RuntimeException("Error adding scheduling execution: " + execution, e);
		}
	}

	private void validateExecution(Execution execution) {
		Validate.notNull(execution);
		Validate.notNull(execution.getDataSourceName());
		Validate.notNull(execution.getId());
		Validate.notNull(execution.getQueryLocation());
		Validate.notNull(execution.getScheduleTime());

		// avoid overwriting previous run, just in case
		Validate.isTrue(execution.getEndTime() == null);
		Validate.isTrue(execution.getErrorMsg() == null);
		Validate.isTrue(execution.getHandle() == null);
		Validate.isTrue(execution.getOutputFilePath() == null);
	}

	private SimpleTrigger makeTrigger(Execution execution) throws SchedulerException {

		final Date startTime = evenMinuteDate(execution.getScheduleTime());
		SimpleDateFormat dateFormat = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT);
		// dates from database are in UTC zone, convert to local
		dateFormat.setTimeZone(TimeZone.getDefault());
		final String dateStr = dateFormat.format(startTime);
		final TriggerKey triggerKey = new TriggerKey(execution.getId().toString() + " @ " + dateStr, group);

		if (scheduler.checkExists(triggerKey)) {
			throw new SchedulerException("Already scheduled: " + triggerKey);
		}

		SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule();
		scheduleBuilder.withMisfireHandlingInstructionFireNow();
		scheduleBuilder.withRepeatCount(0);

		return newTrigger()
				.withIdentity(triggerKey)
				.withSchedule(scheduleBuilder)
				.startAt(startTime)
				.build();
	}

	private JobDetail makeOrGetJobDetail(Execution execution) throws SchedulerException {
		final JobKey jobKey = new JobKey(execution.getId().toString(), group);
		JobDetail job = scheduler.getJobDetail(jobKey);
		if (job == null) {
			job = org.quartz.JobBuilder.newJob(Job.class)
					.usingJobData(EXECUTION_ID, execution.getId().toString())
					.withIdentity(jobKey)
					.requestRecovery()
					.build();
		}
		return job;
	}

	@Override
	public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
		return this;
	}

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {

		// gather execution id from job context and Execution from DB
		int executionId;
		Execution execution = null;
		try {
			final JobDataMap jobData = context.getMergedJobDataMap();
			Validate.notNull(jobData);
			executionId = Integer.parseInt(jobData.getString(EXECUTION_ID));
			execution = executionRepository.find(executionId);
			validateExecution(execution);
			execution.setStartTime(new Date());
		} catch (Exception e) {
			log.error("Quartz job context is invalid. Can't run this job.", e);
			JobExecutionException e2 = new JobExecutionException(e);
			e2.unscheduleAllTriggers();
			throw e2;
		}

		Query query = null;
		QueryRunner runner = null;
		try {
			log.info("Running execution id {}.", execution.getId());

			DataSource dataSource = findDataSource(execution);
			runner = dataSource.getRunner();
			Validate.notNull(runner, "DataSource '%s' runner is null.", dataSource.getName());

			query = loadEntireQuery(execution);
			validateQueryAgainstDataSource(dataSource, query);

		} catch (Exception e) {
			log.error("Either the Query or the DataSource are invalid. Can't run this job.", e);
			updateExecutionWithError(execution, e);
			JobExecutionException e2 = new JobExecutionException(e);
			e2.unscheduleAllTriggers();
			throw e2;
		}

		try {
			// save the start timestamp
			execution = executionRepository.update(execution);

			// TODO refactor the JPA Execution entity to have field for maxHitsPerSelector, move
			// away from generic maps
			Map<String, String> parameters = JSONStringConverter.toMap(execution.getParameters());
			final Path outputFileName = outputPath.resolve("response-" + execution.getId().toString() + ".xml");

			byte[] handle = null;

			try (OutputStream stdOut = executionRepository.executionOutputOutputStream(executionId)) {
				handle = runner.run(query,
						parameters,
						outputFileName.toString(),
						stdOut);
				Validate.notNull(handle, "QueryRunner 'run' method returned null. No null expected.");
			}

			// store the handle and output path and try to collect the response file
			// the response file may not be ready yet
			execution.setHandle(handle);
			execution.setOutputFilePath(outputFileName.toString());
			execution.setErrorMsg(null);
			executionRepository.update(execution);

			// optionally asynchronously update the status of the execution
			// this updates the completion time and the response payload (if finshed)
			try {
				threadPool.submit(() -> statusUpdater.update());
			} catch (Exception e) {
				log.warn("Noncritical error. Unable to schedule execution status update. It will update at a later time.", e);
			}

		} catch (Exception e) {
			log.error("Error executing query. Will flag this execution as failed.", e);
			updateExecutionWithError(execution, e);

			JobExecutionException e2 = new JobExecutionException(e);
			e2.unscheduleAllTriggers();
			throw e2;
		}
	}

	/**
	 * @param execution
	 * @param e
	 */
	private void updateExecutionWithError(Execution execution, Exception e) {
		try {
			execution.setHandle(null);
			execution.setOutputFilePath(null);
			execution.setErrorMsg(exceptionToString(e));
			executionRepository.update(execution);
		} catch (Exception e1) {
			log.error("Error updating execution error status.", e);
		}
	}

	private String exceptionToString(Exception exception) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		exception.printStackTrace(pw);
		return sw.toString();
	}

	private void validateQueryAgainstDataSource(final DataSource dataSource, final QueryInfo queryInfo) {
		String queryDataSchemaName = queryInfo.getQuerySchema().getDataSchema().getName();
		Validate.notBlank(queryDataSchemaName);

		String dataSourceDataSchemaName = dataSource.getDataSchemaName();
		Validate.notBlank(dataSourceDataSchemaName);

		Validate.isTrue(dataSourceDataSchemaName.equals(queryDataSchemaName),
				"Data schema names mismatch. Query references: %s, while DataSource references: %s.",
				queryDataSchemaName,
				dataSourceDataSchemaName);
	}

	private void validateQueryAgainstDataSource(final DataSource dataSource, final Query query) {
		String queryDataSchemaName = query.getQueryInfo().getQuerySchema().getDataSchema().getName();
		Validate.notBlank(queryDataSchemaName);

		String dataSourceDataSchemaName = dataSource.getDataSchemaName();
		Validate.notBlank(dataSourceDataSchemaName);

		Validate.isTrue(dataSourceDataSchemaName.equals(queryDataSchemaName),
				"Data schema names mismatch. Query references: %s, while DataSource references: %s.",
				queryDataSchemaName,
				dataSourceDataSchemaName);
	}

	private DataSource findDataSource(Execution execution) {
		final DataSource dataSource = dataSourceRegistry.find(execution.getDataSourceName());
		Validate.notNull(dataSource, "Data Source %s not found.", execution.getDataSourceName());
		return dataSource;
	}

	private QueryInfo extractQueryInfo(Execution execution) throws JobExecutionException {
		try (InputStream is = executionRepository.queryBytes(execution.getId());
				QueryReader reader = new QueryReader()) {
			reader.parse(is);
			return reader.getQueryInfo();
		} catch (IOException | XMLStreamException e) {
			throw new JobExecutionException("Error deserializing query associated with execution: " + execution, e);
		}
	}

	private Query loadEntireQuery(Execution execution) throws JobExecutionException {
		try (InputStream is = executionRepository.queryBytes(execution.getId())) {
			return queryConverter.toCoreQuery(is);
		} catch (JAXBException | IOException e) {
			throw new JobExecutionException("Error deserializing query associated with execution: " + execution, e);
		}
	}
}
